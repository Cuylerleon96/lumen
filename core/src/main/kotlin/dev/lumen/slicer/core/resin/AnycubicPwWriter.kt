package dev.lumen.slicer.core.resin

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room writer for the **Anycubic v1 resin family** (`.pw0`, `.pws`, `.pwx`, `.pwmx`, `.pwms`,
 * `.pwmb`, `.pwma`, `.px6s`, …).
 *
 * Written from the open UVTools specification (`UVtools.Core/FileFormats/AnycubicFile.cs`, GPL — used
 * as a **reference for byte layouts only**, no code copied). The digest is
 * `docs/research/anycubic-pw.md`.
 *
 * Facts that are easy to get wrong and that this writer encodes deliberately:
 *  - Every variant shares one 12-byte `"ANYCUBIC"` mark; the **variant is chosen by the filename
 *    extension**, not by any header byte. The RLE codec is likewise extension-selected: `.pws` uses
 *    the bit-plane RLE, *everything else* (`.pw0`, `.pwmx`, `.pwmb`, …) uses the PW0 nibble RLE.
 *  - Little-endian throughout. **No XOR, no encryption, no CRC, no trailing block** — the CRC call
 *    site in the reference is commented out.
 *  - The FileMark lives at offset 0 but is only knowable after the payload is laid down, so it is
 *    patched in last. Its size is version-dependent (48/52/56/64) and `HeaderAddress` equals that
 *    size exactly.
 *  - `Header.TableLength` is the **payload** length (80/84/92/96); `Machine.TableLength` is the
 *    **whole** block (156/224). The two named tables use opposite conventions.
 *  - `LayerDef.DataAddress` is an **absolute** file offset; `DataLength` is the exact RLE byte count.
 *
 * Layers are streamed: [write] takes a lazy `maskAt` provider so only one full-resolution mask is in
 * memory at a time (a 4K print's masks are ~2 GB and would OOM a phone). The assembled file is
 * buffered internally only because the FileMark must be seek-patched at offset 0.
 */
object AnycubicPwWriter {

    // ---------------------------------------------------------------- versions

    const val VERSION_1 = 1
    const val VERSION_515 = 515
    const val VERSION_516 = 516
    const val VERSION_517 = 517
    const val VERSION_518 = 518

    /** Layer codec. Chosen by the file extension on the printer, never by a header byte. */
    enum class Codec { PW0, PWS }

    // ------------------------------------------------------------------- limits

    /** 12-bit maximum run of the PW0 two-byte form (nibble colours `0x0` and `0xF`). */
    const val RLE4_LIMIT = 0xFFF

    /** 4-bit maximum run of the PW0 one-byte form (nibble colours `0x1`..`0xE`). */
    const val RLE4_SHORT_LIMIT = 0xF

    /** PWS bit-plane run cap — matches [BitPlaneRle.RLE8_LIMIT]. */
    const val RLE1_LIMIT = 0x7D

    // ------------------------------------------------- exact on-disk byte sizes
    // Named so a too-small allocation fails loudly here instead of as a BufferOverflowException
    // halfway through writing a real file.

    const val MARK_BYTES = 12

    /** FileMark with only the always-present fields (V1/V515): 12 + 9×4. */
    const val FILE_MARK_BASE_BYTES = 48

    /** AnycubicNamedTable name (12) + length (4). */
    const val TABLE_BASE_BYTES = 16

    /** One LayerDef record. */
    const val LAYER_DEF_RECORD_BYTES = 32

    /** One SubLayerDef record. */
    const val SUB_LAYER_DEF_RECORD_BYTES = 44

    /** LayerImageColorTable — a raw struct, no name/length header. */
    const val COLOR_TABLE_BYTES = 28

    /** EXTRA block on disk: 16-byte base + 14 four-byte fields. */
    const val EXTRA_BLOCK_BYTES = 72

    /** The (deliberately inconsistent) value UVTools writes into `EXTRA.TableLength`. */
    const val EXTRA_TABLE_LENGTH = 24

    /** MACHINE whole block for V516/V517. */
    const val MACHINE_BLOCK_LEGACY = 156

    /** MACHINE whole block for V518 (adds pixel pitch, resolution, padding). */
    const val MACHINE_BLOCK_518 = 224

    /** SOFTWARE block — a raw struct with an internal length field. */
    const val SOFTWARE_BLOCK_BYTES = 164

    /** MODEL block: 16-byte base + 8 four-byte fields. */
    const val MODEL_BLOCK_BYTES = 48

    /** MACHINE.MachineBackground default (`0x00634701`). */
    const val MACHINE_BACKGROUND = 6506241

    /** Number of grey levels the colour table always carries. */
    const val GREY_COUNT = 16

    // ------------------------------------------------------------------ params

    /**
     * Everything the printer reads out of the header, machine table and per-layer records.
     * Lengths are in the units the file stores (mm, seconds, mm/s); the `*MmPerMin` fields are the
     * slicer's native units and are converted to mm/s on the way out.
     */
    data class Params(
        val printerName: String,
        val resolutionX: Int,
        val resolutionY: Int,
        val pixelSizeMicrons: Double,
        val displayWidthMm: Float,
        val displayHeightMm: Float,
        val machineZmm: Float,
        val layerHeightMm: Float,
        val exposureSec: Float,
        val bottomExposureSec: Float,
        val bottomLayerCount: Int,
        val lightOffSec: Float = 0f,
        val liftDistanceMm: Float = 5f,
        val liftSpeedMmPerMin: Float = 60f,
        val retractSpeedMmPerMin: Float = 150f,
        val bottomLiftSpeedMmPerMin: Float = 30f,
        val bottomRetractSpeedMmPerMin: Float = 60f,
        val antiAliasing: Int = 1,
        val transitionLayerCount: Int = 0,
        val printTimeSec: Int = 0,
        val volumeMl: Float = 0f,
        val weightG: Float = 0f,
        val price: Float = 0f,
        val priceCurrency: String = "$",
        /** MACHINE.LayerImageFormat; defaults to the codec's conventional value. */
        val layerImageFormat: String? = null,
        val softwareName: String = "Lumen",
        val softwareVersion: String = "1.0",
        val softwareOs: String = "Android",
        val softwareOpenGl: String = "3.3-CoreProfile",
        /** MACHINE.MaxFileVersion; 0 = use the version being written. */
        val machineMaxFileVersion: Int = 0,
        val supportsEnabled: Boolean = false,
        val supportsDensity: Float = 0f,
        /** Model bounds as [minX, minY, minZ, maxX, maxY, maxZ] mm; null = all zero. */
        val modelBounds: FloatArray? = null,
    ) {
        // FloatArray in a data class defeats the generated equals/hashCode; fine for a write-only DTO.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    // ------------------------------------------------------------------- write

    /**
     * Write one Anycubic PW0/PWS file.
     *
     * @param layerCount number of layers; `maskAt` is called for indices `0 until layerCount`.
     * @param maskAt lazy per-layer mask provider — `resolutionX * resolutionY` bytes, row-major,
     *   top-left origin, 8-bit grayscale. Only one is held at a time.
     * @param version file version (1, 515, 516, 517 or 518).
     * @param codec layer codec; must agree with the filename extension the caller will use.
     * @param preview565 optional thumbnail 1, raw RGB565 **little-endian**, top-down, `w*h*2` bytes.
     * @param preview2_565 optional thumbnail 2 (V518 only), same packing.
     */
    fun write(
        out: OutputStream,
        layerCount: Int,
        maskAt: (Int) -> ByteArray,
        params: Params,
        version: Int = VERSION_516,
        codec: Codec = Codec.PW0,
        preview565: ByteArray? = null,
        preview2_565: ByteArray? = null,
    ) {
        require(version == VERSION_1 || version in VERSION_515..VERSION_518) {
            "unsupported Anycubic version $version (expected 1, 515, 516, 517 or 518)"
        }
        require(layerCount >= 0) { "layerCount must be >= 0" }
        require(params.resolutionX > 0 && params.resolutionY > 0) { "resolution must be positive" }
        if (codec == Codec.PWS) require(params.antiAliasing >= 1) { "PWS requires antiAliasing >= 1" }
        if (preview2_565 != null) require(version >= VERSION_518) { "PREVIEW2 needs version >= 518" }

        val pixels = params.resolutionX * params.resolutionY
        val fileMarkBytes = fileMarkSize(version)
        val imageFormat = params.layerImageFormat
            ?: if (codec == Codec.PWS) "pwsImg" else "pw0Img"

        val buf = ByteArrayOutputStream(1 shl 16)

        // ---- FileMark placeholder (patched last; address 0) ----
        buf.write(ByteArray(fileMarkBytes))

        // ---- HEADER ----
        val headerAddress = buf.size()
        buf.write(buildHeader(version, params))

        // ---- PREVIEW (thumbnail 1) ----
        var previewAddress = 0
        if (preview565 != null) {
            previewAddress = buf.size()
            buf.write(buildPreview(preview565, PREVIEW1_W, PREVIEW1_H))
        }

        // ---- LayerImageColorTable (Version >= 515) ----
        var colorTableAddress = 0
        if (version >= VERSION_515) {
            colorTableAddress = buf.size()
            buf.write(buildColorTable(params.antiAliasing))
        }

        // ---- LAYERDEF placeholder + geometry ----
        val layerDefinitionAddress = buf.size()
        val layerDefPayload = 4 + LAYER_DEF_RECORD_BYTES * layerCount
        val layerDefBlockBytes = TABLE_BASE_BYTES + layerDefPayload
        buf.write(ByteArray(layerDefBlockBytes)) // patched after the layer blobs are written
        var layerImageAddress = layerDefinitionAddress + layerDefBlockBytes

        // ---- EXTRA / MACHINE / SOFTWARE / MODEL / SUBIMGS / PREVIEW2 (Version >= 516) ----
        var extraAddress = 0
        var machineAddress = 0
        var softwareAddress = 0
        var modelAddress = 0
        var subLayerDefinitionAddress = 0
        var preview2Address = 0
        if (version >= VERSION_516) {
            extraAddress = layerImageAddress
            buf.write(buildExtra(params))
            machineAddress = buf.size()
            buf.write(buildMachine(version, params, imageFormat))
            if (version >= VERSION_517) {
                softwareAddress = buf.size()
                buf.write(buildSoftware(params))
                modelAddress = buf.size()
                buf.write(buildModel(params))
                if (version >= VERSION_518) {
                    subLayerDefinitionAddress = buf.size()
                    val subBlockBytes = TABLE_BASE_BYTES + 8 + SUB_LAYER_DEF_RECORD_BYTES * layerCount
                    buf.write(ByteArray(subBlockBytes)) // patched after the layer blobs
                    if (preview2_565 != null) {
                        preview2Address = buf.size()
                        buf.write(buildPreview2(preview2_565, PREVIEW2_W, PREVIEW2_H))
                    }
                }
            }
            layerImageAddress = buf.size()
        } else if (version == VERSION_515) {
            // The field is set but nothing is written at that address for 515.
            extraAddress = layerImageAddress
        }

        // ---- layer blobs (streamed one mask at a time) ----
        val dataAddress = IntArray(layerCount)
        val dataLength = IntArray(layerCount)
        val exposure = FloatArray(layerCount)
        val liftHeight = FloatArray(layerCount)
        val liftSpeed = FloatArray(layerCount)
        val layerHeight = FloatArray(layerCount)
        val nonZero = IntArray(layerCount)

        for (i in 0 until layerCount) {
            val mask = maskAt(i)
            require(mask.size == pixels) { "layer $i mask is ${mask.size} bytes, expected $pixels" }
            val rle = when (codec) {
                Codec.PW0 -> encodeNibbleRle(mask)
                Codec.PWS -> encodePws(mask, params.antiAliasing)
            }
            val bottom = i < params.bottomLayerCount
            dataAddress[i] = buf.size()
            dataLength[i] = rle.size
            exposure[i] = exposureFor(params, i)
            liftHeight[i] = params.liftDistanceMm
            liftSpeed[i] = (if (bottom) params.bottomLiftSpeedMmPerMin else params.liftSpeedMmPerMin) / 60f
            layerHeight[i] = params.layerHeightMm
            nonZero[i] = mask.count { it.toInt() != 0 }
            buf.write(rle)
        }

        // ---- assemble + patch the forward-referenced tables ----
        val finalBytes = buf.toByteArray()

        val fileMark = buildFileMark(
            version = version,
            numberOfTables = numberOfTables(version),
            headerAddress = headerAddress,
            softwareAddress = softwareAddress,
            previewAddress = previewAddress,
            colorTableAddress = colorTableAddress,
            layerDefinitionAddress = layerDefinitionAddress,
            extraAddress = extraAddress,
            machineAddress = machineAddress,
            layerImageAddress = layerImageAddress,
            modelAddress = modelAddress,
            subLayerDefinitionAddress = subLayerDefinitionAddress,
            preview2Address = preview2Address,
        )
        System.arraycopy(fileMark, 0, finalBytes, 0, fileMarkBytes)

        val layerDefBlock = buildLayerDefBlock(
            layerCount, dataAddress, dataLength, liftHeight, liftSpeed, exposure, layerHeight, nonZero,
        )
        System.arraycopy(layerDefBlock, 0, finalBytes, layerDefinitionAddress, layerDefBlock.size)

        if (version >= VERSION_518) {
            val subBlock = buildSubLayerDefBlock(layerCount, dataAddress, dataLength, nonZero)
            System.arraycopy(subBlock, 0, finalBytes, subLayerDefinitionAddress, subBlock.size)
        }

        out.write(finalBytes)
        out.flush()
    }

    // ------------------------------------------------------------------ codecs

    /**
     * Encode one layer as the **PW0 nibble RLE** (`.pw0`, `.pwmx`, `.pwms`, `.pwmb`, …).
     *
     * Runs are maximal stretches of bytes sharing the same **high nibble** (`& 0xF0`); the nibble is
     * the colour code. Two forms, chosen by the colour:
     *  - colour `0x1`..`0xE`: one byte, `count(1..15) | (colour << 4)`.
     *  - colour `0x0` or `0xF`: two bytes, `(colour << 12) | count(1..4095)` written big-endian
     *    within the pair — byte0 = `colour<<4 | count>>8`, byte1 = `count & 0xFF`.
     *
     * Runs longer than the form's limit are split into repeated code words of the same colour.
     * The colour is *not* bit-reversed and the count is stored directly.
     */
    fun encodeNibbleRle(mask: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(mask.size / 4 + 16)
        var i = 0
        while (i < mask.size) {
            val color = (mask[i].toInt() and 0xFF) ushr 4
            var run = 1
            while (i + run < mask.size && ((mask[i + run].toInt() and 0xFF) ushr 4) == color) run++
            i += run
            putReps(out, color, run)
        }
        return out.toByteArray()
    }

    private fun putReps(out: ByteArrayOutputStream, color: Int, reps: Int) {
        var remaining = reps
        if (color == 0x0 || color == 0xF) {
            while (remaining > 0) {
                val done = if (remaining > RLE4_LIMIT) RLE4_LIMIT else remaining
                val word = done or (color shl 12)
                out.write((word ushr 8) and 0xFF)
                out.write(word and 0xFF)
                remaining -= done
            }
        } else {
            while (remaining > 0) {
                val done = if (remaining > RLE4_SHORT_LIMIT) RLE4_SHORT_LIMIT else remaining
                out.write(done or (color shl 4))
                remaining -= done
            }
        }
    }

    /**
     * Encode one layer as the **PWS bit-plane RLE** (`.pws` only).
     *
     * One binary plane per anti-aliasing level, concatenated with no separator. Plane `k`
     * (`k = 1..antiAliasing`) has threshold `255*k/(antiAliasing+1) + 1`; pixels at or above it are
     * "on". Delegates the plane codec to [BitPlaneRle] (same semantics as the reference).
     */
    fun encodePws(mask: ByteArray, antiAliasing: Int): ByteArray {
        require(antiAliasing >= 1) { "PWS requires antiAliasing >= 1" }
        val out = ByteArrayOutputStream(mask.size / 8 + 16)
        for (aa in 1..antiAliasing) {
            val threshold = 255 * aa / (antiAliasing + 1) + 1
            out.write(BitPlaneRle.encodePlane(mask, threshold = threshold, maxRunLength = RLE1_LIMIT))
        }
        return out.toByteArray()
    }

    // -------------------------------------------------------------- table sizes

    /** FileMark size: 48 (V1/V515), 52 (V516), 56 (V517), 64 (V518). */
    fun fileMarkSize(version: Int): Int =
        FILE_MARK_BASE_BYTES +
            (if (version >= VERSION_516) 4 else 0) +
            (if (version >= VERSION_517) 4 else 0) +
            (if (version >= VERSION_518) 8 else 0)

    /** NumberOfTables: 4 (V1), 5 (V515), 8 (V516), 9 (V517), 11 (V518). */
    fun numberOfTables(version: Int): Int = when {
        version >= VERSION_518 -> 11
        version >= VERSION_517 -> 9
        version >= VERSION_516 -> 8
        version >= VERSION_515 -> 5
        else -> 4
    }

    /** HEADER.TableLength — the **payload** length, excluding the 16-byte name+length base. */
    fun headerTableLength(version: Int): Int = when {
        version >= VERSION_518 -> 96
        version >= VERSION_517 -> 92
        version >= VERSION_516 -> 84
        else -> 80
    }

    /** MACHINE.TableLength — the **whole** block, including the 16-byte base. */
    fun machineTableLength(version: Int): Int =
        if (version >= VERSION_518) MACHINE_BLOCK_518 else MACHINE_BLOCK_LEGACY

    // ------------------------------------------------------------------ builders

    private fun buildFileMark(
        version: Int,
        numberOfTables: Int,
        headerAddress: Int,
        softwareAddress: Int,
        previewAddress: Int,
        colorTableAddress: Int,
        layerDefinitionAddress: Int,
        extraAddress: Int,
        machineAddress: Int,
        layerImageAddress: Int,
        modelAddress: Int,
        subLayerDefinitionAddress: Int,
        preview2Address: Int,
    ): ByteArray {
        val size = fileMarkSize(version)
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.putAsciiFixed("ANYCUBIC", MARK_BYTES)
        b.putInt(version)
        b.putInt(numberOfTables)
        b.putInt(headerAddress)
        b.putInt(softwareAddress)
        b.putInt(previewAddress)
        b.putInt(colorTableAddress)
        b.putInt(layerDefinitionAddress)
        b.putInt(extraAddress) // ALWAYS present (no SerializeWhen gate in the reference)
        if (version >= VERSION_516) b.putInt(machineAddress)
        b.putInt(layerImageAddress)
        if (version >= VERSION_517) b.putInt(modelAddress)
        if (version >= VERSION_518) {
            b.putInt(subLayerDefinitionAddress)
            b.putInt(preview2Address)
        }
        check(b.position() == size) { "FileMark wrote ${b.position()} bytes, expected $size" }
        return b.array()
    }

    private fun buildHeader(version: Int, p: Params): ByteArray {
        val payload = headerTableLength(version)
        val out = ByteArrayOutputStream(TABLE_BASE_BYTES + payload)
        writeAsciiFixed(out, "HEADER", MARK_BYTES)
        writeU32(out, payload)
        writeF32(out, p.pixelSizeMicrons.toFloat())
        writeF32(out, p.layerHeightMm)
        writeF32(out, p.exposureSec)
        writeF32(out, p.lightOffSec) // WaitTimeBeforeCure
        writeF32(out, p.bottomExposureSec)
        writeF32(out, p.bottomLayerCount.toFloat())
        writeF32(out, p.liftDistanceMm)
        writeF32(out, p.liftSpeedMmPerMin / 60f) // stored mm/s
        writeF32(out, p.retractSpeedMmPerMin / 60f)
        writeF32(out, p.volumeMl)
        writeU32(out, p.antiAliasing)
        writeU32(out, p.resolutionX)
        writeU32(out, p.resolutionY)
        writeF32(out, p.weightG)
        writeF32(out, p.price)
        writeAsciiFixed(out, p.priceCurrency, 4)
        writeU32(out, 1) // PerLayerSettings (UInt4 bool)
        writeU32(out, p.printTimeSec)
        writeU32(out, p.transitionLayerCount)
        writeU32(out, 0) // TransitionLayerType
        if (payload >= 84) writeU32(out, 0) // AdvancedMode
        if (payload >= 86) writeU16(out, 0) // Grey
        if (payload >= 88) writeU16(out, 0) // BlurLevel
        if (payload >= 92) writeU32(out, 0) // ResinType
        if (payload >= 96) writeU32(out, 0) // IntelligentMode
        return out.toByteArray()
    }

    /** PREVIEW: 16-byte base + 12 header bytes + raw RGB565 data. TableLength = 28 + data.size. */
    private fun buildPreview(data: ByteArray, w: Int, h: Int): ByteArray {
        require(data.size == w * h * 2) { "preview data is ${data.size} bytes, expected ${w * h * 2}" }
        val out = ByteArrayOutputStream(28 + data.size)
        writeAsciiFixed(out, "PREVIEW", MARK_BYTES)
        writeU32(out, 28 + data.size)
        writeU32(out, w)
        writeAsciiFixed(out, "x", 4)
        writeU32(out, h)
        out.write(data)
        return out.toByteArray()
    }

    /** PREVIEW2: 16-byte base + 12 header bytes + raw RGB565 data. TableLength = 28 + data.size. */
    private fun buildPreview2(data: ByteArray, w: Int, h: Int): ByteArray {
        require(data.size == w * h * 2) { "preview2 data is ${data.size} bytes, expected ${w * h * 2}" }
        val out = ByteArrayOutputStream(28 + data.size)
        writeAsciiFixed(out, "PREVIEW2", MARK_BYTES)
        writeU32(out, 28 + data.size)
        writeU32(out, w)
        writeU16(out, 4293) // BackgroundColor1 (0x10C5)
        writeU16(out, 0) // BackgroundColor2
        writeU32(out, h)
        out.write(data)
        return out.toByteArray()
    }

    /** LayerImageColorTable — a raw 28-byte struct. Grey[i] = min((i+1)*255/AA, 255). */
    private fun buildColorTable(antiAliasing: Int): ByteArray {
        val out = ByteArrayOutputStream(COLOR_TABLE_BYTES)
        writeU32(out, 0) // UseFullGreyscale
        writeU32(out, GREY_COUNT)
        val increment = 255.0f / maxOf(1, antiAliasing)
        for (i in 0 until GREY_COUNT) {
            out.write(minOf(((i + 1) * increment).toInt(), 255))
        }
        writeU32(out, 0) // Unknown
        return out.toByteArray()
    }

    /** EXTRA — 16-byte base + 14 four-byte fields = 72 bytes; the length field is (wrongly) 24. */
    private fun buildExtra(p: Params): ByteArray {
        val out = ByteArrayOutputStream(EXTRA_BLOCK_BYTES)
        writeAsciiFixed(out, "EXTRA", MARK_BYTES)
        writeU32(out, EXTRA_TABLE_LENGTH)
        val bLift = p.bottomLiftSpeedMmPerMin / 60f
        val bRetract = p.bottomRetractSpeedMmPerMin / 60f
        val lift = p.liftSpeedMmPerMin / 60f
        val retract = p.retractSpeedMmPerMin / 60f
        writeU32(out, 2) // BottomLiftCount
        writeF32(out, p.liftDistanceMm) // BottomLiftHeight1
        writeF32(out, bLift) // BottomLiftSpeed1
        writeF32(out, bRetract) // BottomRetractSpeed2
        writeF32(out, 0f) // BottomLiftHeight2
        writeF32(out, bLift) // BottomLiftSpeed2
        writeF32(out, bRetract) // BottomRetractSpeed1
        writeU32(out, 2) // NormalLiftCount
        writeF32(out, p.liftDistanceMm) // LiftHeight1
        writeF32(out, lift) // LiftSpeed1
        writeF32(out, retract) // RetractSpeed2
        writeF32(out, 0f) // LiftHeight2
        writeF32(out, lift) // LiftSpeed2
        writeF32(out, retract) // RetractSpeed1
        return out.toByteArray()
    }

    private fun buildMachine(version: Int, p: Params, imageFormat: String): ByteArray {
        val whole = machineTableLength(version)
        val b = ByteBuffer.allocate(whole).order(ByteOrder.LITTLE_ENDIAN)
        b.putAsciiFixed("MACHINE", MARK_BYTES)
        b.putInt(whole)
        b.putAsciiFixed(p.printerName, 96)
        b.putAsciiFixed(imageFormat, 16)
        b.putInt(16) // MaxAntialiasingLevel
        b.putInt(
            when {
                version >= VERSION_518 -> 15
                version >= VERSION_517 -> 7
                else -> 1
            },
        ) // PropertyFields
        b.putFloat(p.displayWidthMm)
        b.putFloat(p.displayHeightMm)
        b.putFloat(p.machineZmm)
        b.putInt(if (p.machineMaxFileVersion != 0) p.machineMaxFileVersion else version)
        b.putInt(MACHINE_BACKGROUND)
        if (whole >= 160) {
            b.putFloat(p.pixelSizeMicrons.toFloat()) // PixelWidthUm
            b.putFloat(p.pixelSizeMicrons.toFloat()) // PixelHeightUm
            repeat(8) { b.putInt(0) } // Padding1..8
            b.putInt(1) // DisplayCount
            b.putInt(0) // Padding9
            b.putShort(p.resolutionX.toShort()) // ResolutionX
            b.putShort(p.resolutionY.toShort()) // ResolutionY
            repeat(4) { b.putInt(0) } // Padding10..13
        }
        check(b.position() == whole) { "MACHINE wrote ${b.position()} bytes, expected $whole" }
        return b.array()
    }

    private fun buildSoftware(p: Params): ByteArray {
        val out = ByteArrayOutputStream(SOFTWARE_BLOCK_BYTES)
        writeAsciiFixed(out, p.softwareName, 32)
        writeU32(out, SOFTWARE_BLOCK_BYTES.toInt())
        writeAsciiFixed(out, p.softwareVersion, 32)
        writeAsciiFixed(out, p.softwareOs, 64)
        writeAsciiFixed(out, p.softwareOpenGl, 32)
        return out.toByteArray()
    }

    private fun buildModel(p: Params): ByteArray {
        val out = ByteArrayOutputStream(MODEL_BLOCK_BYTES)
        writeAsciiFixed(out, "MODEL", MARK_BYTES)
        writeU32(out, MODEL_BLOCK_BYTES)
        val bounds = p.modelBounds
        for (k in 0 until 6) writeF32(out, bounds?.getOrNull(k) ?: 0f)
        writeU32(out, if (p.supportsEnabled) 1 else 0)
        writeF32(out, p.supportsDensity)
        return out.toByteArray()
    }

    private fun buildLayerDefBlock(
        layerCount: Int,
        dataAddress: IntArray,
        dataLength: IntArray,
        liftHeight: FloatArray,
        liftSpeed: FloatArray,
        exposure: FloatArray,
        layerHeight: FloatArray,
        nonZero: IntArray,
    ): ByteArray {
        val out = ByteArrayOutputStream(TABLE_BASE_BYTES + 4 + LAYER_DEF_RECORD_BYTES * layerCount)
        writeAsciiFixed(out, "LAYERDEF", MARK_BYTES)
        writeU32(out, 4 + LAYER_DEF_RECORD_BYTES * layerCount) // payload only
        writeU32(out, layerCount)
        for (i in 0 until layerCount) {
            writeU32(out, dataAddress[i]) // absolute file offset
            writeU32(out, dataLength[i])
            writeF32(out, liftHeight[i])
            writeF32(out, liftSpeed[i])
            writeF32(out, exposure[i])
            writeF32(out, layerHeight[i])
            writeU32(out, nonZero[i])
            writeU32(out, 0) // Padding1
        }
        return out.toByteArray()
    }

    private fun buildSubLayerDefBlock(
        layerCount: Int,
        dataAddress: IntArray,
        dataLength: IntArray,
        nonZero: IntArray,
    ): ByteArray {
        val out = ByteArrayOutputStream(TABLE_BASE_BYTES + 8 + SUB_LAYER_DEF_RECORD_BYTES * layerCount)
        writeAsciiFixed(out, "SUBIMGS", MARK_BYTES)
        writeU32(out, 24 + SUB_LAYER_DEF_RECORD_BYTES * layerCount) // whole
        writeU32(out, layerCount)
        writeU32(out, 1) // Index
        for (i in 0 until layerCount) {
            writeU32(out, dataAddress[i])
            writeU32(out, dataLength[i])
            writeU32(out, nonZero[i])
            repeat(8) { writeF32(out, 0f) } // 8 padding floats
        }
        return out.toByteArray()
    }

    // -------------------------------------------------------------------- utils

    private fun exposureFor(p: Params, index: Int): Float {
        if (index < p.bottomLayerCount) return p.bottomExposureSec
        val fadeEnd = p.bottomLayerCount + p.transitionLayerCount
        if (index < fadeEnd) {
            val t = (index - p.bottomLayerCount + 1).toFloat() / (p.transitionLayerCount + 1)
            return p.bottomExposureSec + (p.exposureSec - p.bottomExposureSec) * t
        }
        return p.exposureSec
    }

    /** Thumbnail 1 dimensions the reference uses. */
    private const val PREVIEW1_W = 224
    private const val PREVIEW1_H = 168

    /** Thumbnail 2 dimensions (must match the blob handed in; the reference field default is 320). */
    private const val PREVIEW2_W = 320
    private const val PREVIEW2_H = 190

    private fun ByteBuffer.putAsciiFixed(s: String, len: Int) {
        val src = s.toByteArray(Charsets.US_ASCII)
        val n = minOf(src.size, len)
        put(src, 0, n)
        repeat(len - n) { put(0) }
    }

    private fun writeAsciiFixed(out: ByteArrayOutputStream, s: String, len: Int) {
        val src = s.toByteArray(Charsets.US_ASCII)
        val n = minOf(src.size, len)
        out.write(src, 0, n)
        repeat(len - n) { out.write(0) }
    }

    private fun writeU16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    private fun writeF32(out: ByteArrayOutputStream, v: Float) = writeU32(out, java.lang.Float.floatToIntBits(v))
}
