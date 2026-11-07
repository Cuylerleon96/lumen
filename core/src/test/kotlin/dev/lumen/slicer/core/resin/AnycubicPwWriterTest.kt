package dev.lumen.slicer.core.resin

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Anycubic PW0/PWS writer.
 *
 * The centrepiece is the **hand-computed nibble-RLE byte test**: an encoder and decoder that share a
 * mistake round-trip perfectly, so the only way to catch a wrong run length / wrong form is to assert
 * the literal bytes. Every length class is covered — 1, below the limit, exactly the limit, and above
 * it — for both the one-byte form (colours 1..14, cap 15) and the two-byte form (colours 0 and 15,
 * cap 4095).
 *
 * The writer is also walked by an **independent parser** written from the same digest, and every
 * layer is decoded by an **independent decoder** written straight from the reference pseudocode.
 */
class AnycubicPwWriterTest {

    // ----------------------------------------------------------- helpers

    /** Mask byte for nibble colour [c] (the low nibble is irrelevant to the codec). */
    private fun nib(c: Int) = ((c shl 4) or 0x0A).toByte()

    private fun run(c: Int, len: Int) = ByteArray(len) { nib(c) }

    private fun enc(mask: ByteArray) = AnycubicPwWriter.encodeNibbleRle(mask).map { it.toInt() and 0xFF }

    private fun params(width: Int = 8, height: Int = 8) = AnycubicPwWriter.Params(
        printerName = "Anycubic Photon Mono X",
        resolutionX = width,
        resolutionY = height,
        pixelSizeMicrons = 50.0,
        displayWidthMm = 192f,
        displayHeightMm = 120f,
        machineZmm = 245f,
        layerHeightMm = 0.05f,
        exposureSec = 2.5f,
        bottomExposureSec = 25f,
        bottomLayerCount = 1,
        lightOffSec = 7f,
        liftDistanceMm = 5f,
        liftSpeedMmPerMin = 60f,
        retractSpeedMmPerMin = 150f,
    )

    private fun writeFile(
        version: Int,
        masks: List<ByteArray>,
        width: Int = 8,
        height: Int = 8,
        codec: AnycubicPwWriter.Codec = AnycubicPwWriter.Codec.PW0,
        preview: ByteArray? = null,
        preview2: ByteArray? = null,
        p: AnycubicPwWriter.Params = params(width, height),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        AnycubicPwWriter.write(out, masks.size, { masks[it] }, p, version, codec, preview, preview2)
        return out.toByteArray()
    }

    // ============================================================ 1. BYTE LEVEL

    @Test
    fun `nibble rle hand-computed bytes for every length class`() {
        // --- one-byte form, colour 5, limit 15 ---
        assertEquals(listOf(0x51), enc(run(5, 1)), "colour 5, run 1 -> 1 | 5<<4")
        assertEquals(listOf(0x57), enc(run(5, 7)), "colour 5, run 7 -> 7 | 5<<4")
        assertEquals(listOf(0x5F), enc(run(5, 15)), "colour 5, run 15 -> 15 | 5<<4 (exactly the limit)")
        assertEquals(listOf(0x5F, 0x55), enc(run(5, 20)), "colour 5, run 20 -> 15 then 5")

        // --- two-byte form, colour 0 (black), limit 4095 ---
        assertEquals(listOf(0x00, 0x01), enc(run(0, 1)), "colour 0, run 1 -> word 0x0001, big-endian")
        assertEquals(listOf(0x01, 0x2C), enc(run(0, 300)), "colour 0, run 300 -> word 0x012C")
        assertEquals(listOf(0x0F, 0xFF), enc(run(0, 4095)), "colour 0, run 4095 -> word 0x0FFF (limit)")
        assertEquals(listOf(0x0F, 0xFF, 0x03, 0x89), enc(run(0, 5000)), "colour 0, run 5000 -> 4095 then 905=0x0389")

        // --- two-byte form, colour 15 (white), limit 4095 ---
        assertEquals(listOf(0xF0, 0x01), enc(run(15, 1)), "colour 15, run 1 -> word 0xF001")
        assertEquals(listOf(0xF1, 0x2C), enc(run(15, 300)), "colour 15, run 300 -> word 0xF12C")
        assertEquals(listOf(0xFF, 0xFF), enc(run(15, 4095)), "colour 15, run 4095 -> word 0xFFFF (limit)")
        assertEquals(listOf(0xFF, 0xFF, 0xF3, 0x89), enc(run(15, 5000)), "colour 15, run 5000 -> 4095 then 0xF389")
    }

    @Test
    fun `nibble rle mixed runs are labelled and hand-computed`() {
        // 3 grey-10, 2 black-0, 5 white-15, 4 grey-3, 16 grey-7 — so a mislabelled on/off (0 vs 15)
        // run, or a wrong form, shows up as a mismatched literal below.
        val mask = run(10, 3) + run(0, 2) + run(15, 5) + run(3, 4) + run(7, 16)

        assertEquals(
            listOf(
                0xA3, // grey 10 x3 : 3 | 10<<4
                0x00, 0x02, // black 0 x2 : two-byte form
                0xF0, 0x05, // white 15 x5 : two-byte form
                0x34, // grey 3 x4 : 4 | 3<<4
                0x7F, 0x71, // grey 7 x16 : 15 then 1
            ),
            enc(mask),
        )
    }

    @Test
    fun `an all-black layer costs two bytes per 4095 pixels`() {
        val px = 4095 * 2 + 10
        val rle = AnycubicPwWriter.encodeNibbleRle(ByteArray(px))
        // 4095 -> 0x0FFF, 4095 -> 0x0FFF, 10 -> 0x000A
        assertEquals(listOf(0x0F, 0xFF, 0x0F, 0xFF, 0x00, 0x0A), rle.map { it.toInt() and 0xFF })
    }

    // ================================================== 2. INDEPENDENT DECODER

    /** Decode PW0 straight from the reference pseudocode (no shared code with the writer). */
    private fun decodeNibbleRle(rle: ByteArray, pixelCount: Int): ByteArray {
        val out = ByteArray(pixelCount)
        var pos = 0
        var i = 0
        while (i < rle.size) {
            val b = rle[i].toInt() and 0xFF
            val code = b ushr 4
            var repeat = b and 0xF
            val color: Int
            when (code) {
                0x0 -> {
                    color = 0
                    i++
                    repeat = if (i >= rle.size) pixelCount - pos else (repeat shl 8) + (rle[i].toInt() and 0xFF)
                }
                0xF -> {
                    color = 255
                    i++
                    repeat = if (i >= rle.size) pixelCount - pos else (repeat shl 8) + (rle[i].toInt() and 0xFF)
                }
                else -> color = (code shl 4) or code
            }
            check(pos + repeat <= pixelCount) { "run overran the image at $pos (+$repeat > $pixelCount)" }
            for (k in 0 until repeat) out[pos + k] = color.toByte()
            pos += repeat
            if (pos == pixelCount) break
            i++
        }
        check(pos == pixelCount) { "decoder ended short: $pos of $pixelCount" }
        return out
    }

    @Test
    fun `every layer round-trips through the independent decoder`() {
        val w = 384; val h = 240
        // Nibble-aligned values only: the codec reconstructs (code<<4)|code.
        val levels = intArrayOf(0x00, 0x22, 0x55, 0x88, 0xFF)
        val masks = listOf(
            ByteArray(w * h), // all black
            ByteArray(w * h) { i ->
                val x = i % w - w / 2
                val y = i / w - h / 2
                if (x * x + y * y < 100 * 100) 0xFF.toByte() else 0x00
            },
            ByteArray(w * h) { levels[(it / 97) % levels.size].toByte() },
        )
        val file = writeFile(516, masks, width = w, height = h)
        val parsed = parse(file)

        assertEquals(3, parsed.layerCount)
        masks.forEachIndexed { i, original ->
            val decoded = decodeNibbleRle(parsed.layers[i], w * h)
            for (p in decoded.indices) {
                if (original[p] != decoded[p]) {
                    throw AssertionError("layer $i differs at pixel $p: ${original[p]} vs ${decoded[p]}")
                }
            }
        }
    }

    // ============================================= 3. INDEPENDENT FILE PARSER

    private class Parsed(
        val version: Int,
        val numberOfTables: Int,
        val headerAddress: Int,
        val softwareAddress: Int,
        val previewAddress: Int,
        val colorTableAddress: Int,
        val layerDefinitionAddress: Int,
        val extraAddress: Int,
        val machineAddress: Int,
        val layerImageAddress: Int,
        val modelAddress: Int,
        val subLayerDefinitionAddress: Int,
        val preview2Address: Int,
        val fileMarkSize: Int,
        val headerTableLength: Int,
        val machineTableLength: Int,
        val machineLayerImageFormat: String,
        val layerCount: Int,
        val layers: List<ByteArray>,
        val dataAddresses: List<Int>,
        val dataLengths: List<Int>,
        val extraTableLength: Int,
    )

    private fun readAscii(bb: ByteBuffer, len: Int): String {
        val raw = ByteArray(len); bb.get(raw)
        val end = raw.indexOf(0).let { if (it < 0) raw.size else it }
        return String(raw, 0, end, Charsets.US_ASCII)
    }

    private fun parse(bytes: ByteArray): Parsed {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(readAscii(bb, 12) == "ANYCUBIC") { "bad file mark" }
        val version = bb.int
        val numberOfTables = bb.int
        val headerAddress = bb.int
        val softwareAddress = bb.int
        val previewAddress = bb.int
        val colorTableAddress = bb.int
        val layerDefinitionAddress = bb.int
        val extraAddress = bb.int // always present
        val machineAddress = if (version >= 516) bb.int else 0
        val layerImageAddress = bb.int
        val modelAddress = if (version >= 517) bb.int else 0
        val subLayerDefinitionAddress = if (version >= 518) bb.int else 0
        val preview2Address = if (version >= 518) bb.int else 0
        val fileMarkSize = bb.position()
        check(headerAddress == fileMarkSize) { "HeaderAddress $headerAddress != FileMark size $fileMarkSize" }

        // ---- HEADER ----
        bb.position(headerAddress)
        check(readAscii(bb, 12) == "HEADER") { "bad HEADER name" }
        val headerTableLength = bb.int
        val pixelSizeUm = bb.float
        val layerHeight = bb.float
        val exposure = bb.float
        bb.float // WaitTimeBeforeCure
        val bottomExposure = bb.float
        val bottomLayers = bb.float
        val liftHeight = bb.float
        val liftSpeed = bb.float
        val retractSpeed = bb.float
        bb.float // VolumeMl
        val antiAliasing = bb.int
        val resX = bb.int
        val resY = bb.int
        check(pixelSizeUm == 50f) { "PixelSizeUm $pixelSizeUm" }
        check(layerHeight == 0.05f) { "LayerHeight $layerHeight" }
        check(exposure == 2.5f) { "ExposureTime $exposure" }
        check(bottomExposure == 25f) { "BottomExposureTime $bottomExposure" }
        check(bottomLayers == 1f) { "BottomLayersCount $bottomLayers" }
        check(liftHeight == 5f) { "LiftHeight $liftHeight" }
        check(kotlin.math.abs(liftSpeed - 1f) < 1e-6f) { "LiftSpeed $liftSpeed (mm/s)" }
        check(kotlin.math.abs(retractSpeed - 2.5f) < 1e-6f) { "RetractSpeed $retractSpeed (mm/s)" }
        check(antiAliasing >= 1)
        check(resX > 0 && resY > 0)

        // ---- MACHINE (>=516) ----
        var machineTableLength = 0
        var machineFormat = ""
        if (version >= 516) {
            bb.position(machineAddress)
            check(readAscii(bb, 12) == "MACHINE") { "bad MACHINE name" }
            machineTableLength = bb.int
            readAscii(bb, 96) // MachineName
            machineFormat = readAscii(bb, 16)
        }

        // ---- EXTRA (>=516): length field is 24 but the block is 72 bytes ----
        var extraTableLength = 0
        if (version >= 516) {
            bb.position(extraAddress)
            check(readAscii(bb, 12) == "EXTRA") { "bad EXTRA name" }
            extraTableLength = bb.int
            check(machineAddress - extraAddress == AnycubicPwWriter.EXTRA_BLOCK_BYTES) {
                "EXTRA block is ${machineAddress - extraAddress} bytes, expected 72"
            }
        }

        // ---- LAYERDEF ----
        bb.position(layerDefinitionAddress)
        check(readAscii(bb, 12) == "LAYERDEF") { "bad LAYERDEF name" }
        val layerDefTableLength = bb.int
        val layerCount = bb.int
        check(layerDefTableLength == 4 + 32 * layerCount) { "LAYERDEF TableLength $layerDefTableLength" }
        val addresses = ArrayList<Int>(layerCount)
        val lengths = ArrayList<Int>(layerCount)
        val layers = ArrayList<ByteArray>(layerCount)
        val pixelCount = resX * resY
        repeat(layerCount) {
            val addr = bb.int
            val len = bb.int
            bb.float; bb.float; bb.float; bb.float // lift/exposure/layerheight
            bb.int // NonZeroPixelCount
            bb.int // Padding1
            addresses.add(addr)
            lengths.add(len)
            val rle = ByteArray(len)
            System.arraycopy(bytes, addr, rle, 0, len)
            layers.add(rle)
        }

        return Parsed(
            version, numberOfTables, headerAddress, softwareAddress, previewAddress, colorTableAddress,
            layerDefinitionAddress, extraAddress, machineAddress, layerImageAddress, modelAddress,
            subLayerDefinitionAddress, preview2Address, fileMarkSize, headerTableLength,
            machineTableLength, machineFormat, layerCount, layers, addresses, lengths, extraTableLength,
        )
    }

    // ==================================================== 4. STRUCTURAL CHECKS

    @Test
    fun `file mark size and header address track the version`() {
        val expected = mapOf(1 to 48, 515 to 48, 516 to 52, 517 to 56, 518 to 64)
        for ((version, size) in expected) {
            val file = writeFile(version, List(2) { ByteArray(8 * 8) })
            val parsed = parse(file)
            assertEquals(size, parsed.fileMarkSize, "FileMark size for v$version")
            assertEquals(size, parsed.headerAddress, "HeaderAddress for v$version")
            // The 12-byte mark is identical for every variant.
            assertEquals("ANYCUBIC", String(file, 0, 8, Charsets.US_ASCII))
            assertEquals(
                mapOf(1 to 4, 515 to 5, 516 to 8, 517 to 9, 518 to 11)[version],
                parsed.numberOfTables,
                "NumberOfTables for v$version",
            )
        }
    }

    @Test
    fun `header table length is payload only`() {
        assertEquals(80, AnycubicPwWriter.headerTableLength(1))
        assertEquals(80, AnycubicPwWriter.headerTableLength(515))
        assertEquals(84, AnycubicPwWriter.headerTableLength(516))
        assertEquals(92, AnycubicPwWriter.headerTableLength(517))
        assertEquals(96, AnycubicPwWriter.headerTableLength(518))
        // The block on disk is 16 + payload.
        val file = writeFile(516, List(2) { ByteArray(64) })
        assertEquals(84, parse(file).headerTableLength)
    }

    @Test
    fun `machine table length is the whole block and format matches the codec`() {
        val v516 = parse(writeFile(516, List(2) { ByteArray(64) }))
        assertEquals(156, v516.machineTableLength)
        assertEquals("pw0Img", v516.machineLayerImageFormat)

        val v518 = parse(writeFile(518, List(2) { ByteArray(64) }))
        assertEquals(224, v518.machineTableLength)

        val pws = parse(writeFile(516, List(2) { ByteArray(64) }, codec = AnycubicPwWriter.Codec.PWS))
        assertEquals("pwsImg", pws.machineLayerImageFormat)
    }

    @Test
    fun `extra table length field is 24 while the block is 72 bytes`() {
        val parsed = parse(writeFile(516, List(2) { ByteArray(64) }))
        assertEquals(AnycubicPwWriter.EXTRA_TABLE_LENGTH, parsed.extraTableLength)
        assertEquals(72, AnycubicPwWriter.EXTRA_BLOCK_BYTES)
    }

    @Test
    fun `version one has no colour table extra or machine addresses`() {
        val parsed = parse(writeFile(1, List(2) { ByteArray(64) }))
        assertEquals(0, parsed.colorTableAddress)
        assertEquals(0, parsed.extraAddress)
        assertEquals(0, parsed.machineAddress)
        assertEquals(0, parsed.softwareAddress)
        assertEquals(0, parsed.modelAddress)
    }

    @Test
    fun `version 515 writes a colour table and points extra at the layer image`() {
        val parsed = parse(writeFile(515, List(2) { ByteArray(64) }))
        assertTrue(parsed.colorTableAddress > 0, "515 carries the grayscale LUT")
        assertEquals(parsed.layerImageAddress, parsed.extraAddress, "515 sets ExtraAddress, writes nothing")
        assertEquals(0, parsed.machineAddress)
    }

    @Test
    fun `version 516 writes extra then machine and points layer image past them`() {
        val parsed = parse(writeFile(516, List(2) { ByteArray(64) }))
        // EXTRA sits right after the LAYERDEF table; MACHINE right after EXTRA; the first layer
        // blob right after MACHINE.
        assertTrue(parsed.extraAddress > parsed.layerDefinitionAddress)
        assertEquals(parsed.extraAddress + AnycubicPwWriter.EXTRA_BLOCK_BYTES, parsed.machineAddress)
        assertEquals(parsed.machineAddress + 156, parsed.layerImageAddress)
    }

    @Test
    fun `layer data addresses are absolute and blobs decode back`() {
        val masks = List(3) { idx -> ByteArray(8 * 8) { i -> if ((i + idx) % 3 == 0) 0xFF.toByte() else 0x00 } }
        val file = writeFile(516, masks)
        val parsed = parse(file)
        assertEquals(3, parsed.layerCount)
        masks.forEachIndexed { i, original ->
            // The recorded absolute address must point at the exact blob.
            assertEquals(original.toList(), decodeNibbleRle(parsed.layers[i], 64).toList(), "layer $i")
            assertTrue(parsed.dataAddresses[i] > 0)
            assertEquals(parsed.layers[i].size, parsed.dataLengths[i])
        }
    }

    @Test
    fun `preview table is raw rgb565 with an inclusive table length`() {
        val w = 224; val h = 168
        val preview = ByteArray(w * h * 2)
        val file = writeFile(516, List(1) { ByteArray(64) }, preview = preview)
        val parsed = parse(file)
        assertTrue(parsed.previewAddress > 0)

        val bb = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(parsed.previewAddress)
        assertEquals("PREVIEW", readAscii(bb, 12))
        assertEquals(28 + preview.size, bb.int, "TableLength includes base + 12 header bytes + data")
        assertEquals(w, bb.int)
        assertEquals("x", readAscii(bb, 4))
        assertEquals(h, bb.int)
    }

    // ============================================================ 5. PWS CODEC

    @Test
    fun `pws blob is one bit-plane per aa level`() {
        val mask = ByteArray(64) { (it * 4).toByte() }
        val aa = 4
        val expected = ByteArrayOutputStream().apply {
            for (k in 1..aa) {
                val threshold = 255 * k / (aa + 1) + 1
                write(BitPlaneRle.encodePlane(mask, threshold = threshold, maxRunLength = AnycubicPwWriter.RLE1_LIMIT))
            }
        }.toByteArray()
        assertEquals(expected.toList(), AnycubicPwWriter.encodePws(mask, aa).toList())

        // AA off is a single plane at threshold 128.
        val one = AnycubicPwWriter.encodePws(mask, 1)
        assertEquals(
            BitPlaneRle.encodePlane(mask, threshold = 128, maxRunLength = AnycubicPwWriter.RLE1_LIMIT).toList(),
            one.toList(),
        )
    }

    @Test
    fun `pws requires at least one aa level`() {
        assertTrue(runCatching { AnycubicPwWriter.encodePws(ByteArray(8), 0) }.isFailure)
    }
}
