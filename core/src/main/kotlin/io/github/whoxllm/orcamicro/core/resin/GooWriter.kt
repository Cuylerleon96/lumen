package io.github.whoxllm.orcamicro.core.resin

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Clean-room **GOO** (Elegoo / Phrozen) writer (D.8) — built from the open GPL UVTools `GooFile`
 * layout (facts, not lifted code). Whole file is **big-endian**. Layout:
 * `Header (61 fields) → [LayerDef + RLE + 0D0A]×N → Footer (3 pad + 8-byte magic)`.
 *
 * The layer RLE uses only the plain run chunks (types 0=zero / 1=gray / 3=255) with 4/12/20/28-bit
 * run lengths, terminated by a checksum byte = bitwise-NOT of the byte sum. Masks are 0..127;
 * values are doubled to the 0..255 GOO range.
 */
object GooWriter {

    private val MAGIC = byteArrayOf(0x07, 0x00, 0x00, 0x00, 0x44, 0x4C, 0x50, 0x00) // 07 00 00 00 "DLP" 00
    private const val LAYER_MAGIC = 0x55
    private val DELIMITER = byteArrayOf(0x0D, 0x0A)

    fun write(
        layers: List<ByteArray>,          // per-layer masks, width*height, values 0..127
        width: Int,
        height: Int,
        layerHeightMm: Double,
        exposureSec: Double,
        bottomExposureSec: Double,
        bottomLayers: Int,
        lightOffSec: Double,
        machineZmm: Double,
        plateWidthMm: Double,
        plateDepthMm: Double,
        out: OutputStream,
    ) {
        val layerCount = layers.size

        // --- encode each layer's RLE (also gives us each LayerDef's DataLength) ---
        val rle = layers.map { encodeRle(it) }

        // --- Header ---
        val header = ByteArrayOutputStream()
        header.writeAsciiFixed("V3.0", 4)
        header.write(MAGIC)
        header.writeAsciiFixed("OrcaMicro", 32)
        header.writeAsciiFixed("1.0", 24)
        header.writeAsciiFixed(nowStamp(), 24)
        header.writeAsciiFixed("OrcaMicro", 32)   // MachineName
        header.writeAsciiFixed("DLP", 32)          // MachineType
        header.writeAsciiFixed("OrcaMicro", 32)    // ProfileName
        header.writeU16(1)                         // AntiAliasingLevel
        header.writeU16(1)                         // GreyLevel
        header.writeU16(0)                         // BlurLevel
        header.write(ByteArray(116 * 116 * 2))     // SmallPreview565 (blank)
        header.write(DELIMITER)
        header.write(ByteArray(290 * 290 * 2))     // BigPreview565 (blank)
        header.write(DELIMITER)
        header.writeU32(layerCount)                // LayerCount
        header.writeU16(width)                     // ResolutionX
        header.writeU16(height)                    // ResolutionY
        header.write(0); header.write(0)           // MirrorX, MirrorY
        header.writeF32(plateWidthMm)              // DisplayWidth
        header.writeF32(plateDepthMm)              // DisplayHeight
        header.writeF32(machineZmm)                // MachineZ
        header.writeF32(layerHeightMm)             // LayerHeight
        header.writeF32(exposureSec)               // ExposureTime
        header.write(1)                            // DelayMode = WaitTime
        header.writeF32(lightOffSec)               // LightOffDelay
        header.writeF32(0f); header.writeF32(0f); header.writeF32(0f)   // BottomWait AfterCure/Lift/BeforeCure
        header.writeF32(0f); header.writeF32(0f); header.writeF32(0f)   // Wait AfterCure/Lift/BeforeCure
        header.writeF32(bottomExposureSec)         // BottomExposureTime
        header.writeU32(bottomLayers)              // BottomLayerCount
        header.writeF32(5f); header.writeF32(60f)  // BottomLiftHeight/Speed
        header.writeF32(5f); header.writeF32(60f)  // LiftHeight/Speed
        header.writeF32(0f); header.writeF32(60f)  // BottomRetractHeight/Speed
        header.writeF32(0f); header.writeF32(60f)  // RetractHeight/Speed
        header.writeF32(0f); header.writeF32(60f)  // BottomLiftHeight2/Speed2
        header.writeF32(0f); header.writeF32(60f)  // LiftHeight2/Speed2
        header.writeF32(0f); header.writeF32(60f)  // BottomRetractHeight2/Speed2
        header.writeF32(0f); header.writeF32(60f)  // RetractHeight2/Speed2
        header.writeU16(255)                       // BottomLightPWM
        header.writeU16(255)                       // LightPWM
        header.write(1)                            // PerLayerSettings
        header.writeF32(0f)                        // PrintTime (unknown)
        header.writeF32(0f)                        // Volume
        header.writeF32(0f)                        // MaterialGrams
        header.writeF32(0f)                        // MaterialCost
        header.writeAsciiFixed("$", 8)             // PriceCurrencySymbol
        // LayerDefAddress(u32) + GrayScaleLevel(u16) + TransitionLayerCount(u16); patch address once size is known.
        header.writeU32(0)                         // LayerDefAddress (patched below)
        header.writeU16(1)                         // GrayScaleLevel
        header.writeU16(0)                         // TransitionLayerCount
        val headerBytes = header.toByteArray()
        val layerDefAddressPos = headerBytes.size - 8   // u32 sits 8 bytes from the end (u32 + u16 + u16)
        putU32(headerBytes, layerDefAddressPos, headerBytes.size.toLong())

        out.write(headerBytes)

        // --- Layer table: [LayerDef + RLE + delimiter] × N ---
        for (i in 0 until layerCount) {
            val def = ByteArrayOutputStream()
            def.writeU16(0)                        // Pause
            def.writeF32(machineZmm)               // PausePositionZ
            def.writeF32(layerHeightMm * (i + 1))  // PositionZ
            def.writeF32(if (i < bottomLayers) bottomExposureSec else exposureSec)  // ExposureTime
            def.writeF32(lightOffSec)              // LightOffDelay
            def.writeF32(0f); def.writeF32(0f); def.writeF32(0f)   // Wait AfterCure/Lift/BeforeCure
            def.writeF32(5f); def.writeF32(60f)    // LiftHeight/Speed
            def.writeF32(0f); def.writeF32(60f)    // LiftHeight2/Speed2
            def.writeF32(0f); def.writeF32(60f)    // RetractHeight/Speed
            def.writeF32(0f); def.writeF32(60f)    // RetractHeight2/Speed2
            def.writeU16(255)                      // LightPWM
            def.write(DELIMITER)                   // DelimiterData
            def.writeU32(rle[i].size)              // DataLength
            out.write(def.toByteArray())
            out.write(rle[i])
            out.write(DELIMITER)
        }

        // --- Footer ---
        out.write(byteArrayOf(0, 0, 0))
        out.write(MAGIC)
        out.flush()
    }

    // ---- RLE (plain runs only: 0 = zero, 1 = gray, 3 = 255) ----

    fun encodeRle(mask: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(LAYER_MAGIC)
        var i = 0
        while (i < mask.size) {
            val v = ((mask[i].toInt() and 0xFF) * 2).coerceAtMost(255)
            var run = 1
            while (i + run < mask.size && ((mask[i + run].toInt() and 0xFF) * 2).coerceAtMost(255) == v && run < (1 shl 27)) run++
            writeChunk(out, v, run)
            i += run
        }
        val bytes = out.toByteArray()
        var sum = 0
        for (j in 1 until bytes.size) sum += bytes[j].toInt() and 0xFF
        out.write((sum.inv()) and 0xFF)
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, value: Int, run: Int) {
        val type = when (value) {
            0 -> 0
            255 -> 3
            else -> 1
        }
        val lenType = when {
            run < (1 shl 4) -> 0
            run < (1 shl 12) -> 1
            run < (1 shl 20) -> 2
            else -> 3
        }
        val byte0 = (type shl 6) or (lenType shl 4) or (run and 0xF)
        out.write(byte0)
        if (type == 1) out.write(value)
        when (lenType) {
            1 -> out.write((run shr 4) and 0xFF)
            2 -> { out.write((run shr 4) and 0xFF); out.write((run shr 12) and 0xFF) }
            3 -> { out.write((run shr 4) and 0xFF); out.write((run shr 12) and 0xFF); out.write((run shr 20) and 0xFF) }
        }
    }

    // ---- byte helpers (big-endian) ----

    private fun ByteArrayOutputStream.writeAsciiFixed(s: String, len: Int) {
        val b = ByteArray(len)
        val src = s.toByteArray(Charsets.US_ASCII)
        System.arraycopy(src, 0, b, 0, minOf(src.size, len))
        write(b)
    }
    private fun ByteArrayOutputStream.writeU16(v: Int) { write((v ushr 8) and 0xFF); write(v and 0xFF) }
    private fun ByteArrayOutputStream.writeU32(v: Int) {
        write((v ushr 24) and 0xFF); write((v ushr 16) and 0xFF); write((v ushr 8) and 0xFF); write(v and 0xFF)
    }
    private fun ByteArrayOutputStream.writeF32(v: Double) = writeF32(v.toFloat())
    private fun ByteArrayOutputStream.writeF32(v: Float) {
        val bits = java.lang.Float.floatToIntBits(v)
        write((bits ushr 24) and 0xFF); write((bits ushr 16) and 0xFF); write((bits ushr 8) and 0xFF); write(bits and 0xFF)
    }
    private fun putU32(buf: ByteArray, pos: Int, v: Long) {
        buf[pos] = ((v ushr 24) and 0xFF).toByte(); buf[pos + 1] = ((v ushr 16) and 0xFF).toByte()
        buf[pos + 2] = ((v ushr 8) and 0xFF).toByte(); buf[pos + 3] = (v and 0xFF).toByte()
    }
    private fun nowStamp(): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return f.format(java.util.Date())
    }
}
