package dev.lumen.slicer.core.resin

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Photon S writer, checked with an **independent reader** written from the same open spec
 * (UVTools' `AnycubicPhotonSFile`): a writer validated only by its own encoder proves nothing.
 *
 * Three levels of evidence:
 *  1. byte-level — hand-computed RLE bytes for known runs, and the header's big-endian tag;
 *  2. structural — the parser walks header → preview → layer count → per-layer defs, and every
 *     `dataSize` must agree with the RLE length it prefixes;
 *  3. round-trip — every pixel of every layer survives encode → decode.
 */
class PhotonSWriterTest {

    private val printer = ResinPrinter("Photon S", 1440, 2560, 47.25, 165.0, ResinFormat.PHOTONS)
    private val settings = ResinSettings(
        layerHeightMm = 0.05,
        exposureSec = 8.0,
        bottomExposureSec = 60.0,
        bottomLayers = 5,
        lightOffSec = 1.0,
        liftDistanceMm = 5.0,
        liftSpeedMmPerMin = 60.0,
        retractSpeedMmPerMin = 150.0,
    )

    private fun mask(w: Int, h: Int, on: (Int) -> Boolean) =
        LayerImage(w, h, ByteArray(w * h) { if (on(it)) 255.toByte() else 0 })

    // ---- independent parser ---------------------------------------------------

    private class Parsed(
        val tag1: Int,
        val tag2: Int,
        val pixelSizeMm: Double,
        val layerHeight: Double,
        val exposure: Double,
        val lightOff: Double,
        val bottomExposure: Double,
        val bottomLayers: Int,
        val liftHeight: Double,
        val liftSpeedMmPerSec: Double,
        val retractSpeedMmPerSec: Double,
        val volumeMl: Double,
        val previewW: Int,
        val previewH: Int,
        val previewBytes: ByteArray,
        val layerCount: Int,
        val layers: List<ByteArray>,
        val dataSizes: List<Int>,
    )

    /** Walks the file exactly as the reference decoder does; throws on any structural mismatch. */
    private fun parse(bytes: ByteArray): Parsed {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val tag1 = bb.int
        val tag2 = bb.short.toInt()
        val pixelSize = bb.double
        val layerHeight = bb.double
        val exposure = bb.double
        val lightOff = bb.double
        val bottomExposure = bb.double
        val bottomLayers = bb.int
        val liftHeight = bb.double
        val liftSpeed = bb.double
        val retractSpeed = bb.double
        val volume = bb.double
        val previewW = bb.int
        check(bb.int == 42) { "preview tag missing" }
        val previewH = bb.int
        check(bb.int == 10) { "preview tag2 missing" }
        val preview = ByteArray(previewW * previewH * 2)
        bb.get(preview)
        val layerCount = bb.int
        val layers = ArrayList<ByteArray>(layerCount)
        val sizes = ArrayList<Int>(layerCount)
        repeat(layerCount) {
            check(bb.int == 44944) { "layer tag missing" }
            check(bb.int == 0); check(bb.int == 0)
            check(bb.int == printer.resolutionX) { "layer resX" }
            check(bb.int == printer.resolutionY) { "layer resY" }
            val dataSize = bb.int
            check(bb.int == 2684702720L.toInt()) { "layer tag2" }
            val rleSize = (dataSize shr 3) - 4
            check(rleSize > 0) { "bad dataSize $dataSize" }
            val rle = ByteArray(rleSize)
            bb.get(rle)
            layers.add(rle)
            sizes.add(dataSize)
        }
        check(!bb.hasRemaining()) { "${bb.remaining()} trailing bytes" }
        return Parsed(
            tag1, tag2, pixelSize, layerHeight, exposure, lightOff, bottomExposure, bottomLayers,
            liftHeight, liftSpeed, retractSpeed, volume, previewW, previewH, preview, layerCount,
            layers, sizes,
        )
    }

    /** Independent RLE decode, straight from the spec text. */
    private fun decodeRle(rle: ByteArray, pixelCount: Int): ByteArray {
        val out = ByteArray(pixelCount)
        var pos = 0
        for (b in rle) {
            val v = b.toInt() and 0xFF
            val brightness = (v and 1) * 255
            val run = PhotonSWriter.reverseBits(v and 0xFE) + 1
            check(pos + run <= pixelCount) { "run overran the image" }
            for (k in 0 until run) out[pos + k] = brightness.toByte()
            pos += run
        }
        return out
    }

    private fun writeFile(layers: List<LayerImage>): ByteArray {
        val out = ByteArrayOutputStream()
        PhotonSWriter.write(out, printer, settings, layers.size, volumeMl = 12.5) { layers[it] }
        return out.toByteArray()
    }

    // ---- 1. byte level -------------------------------------------------------

    @Test
    fun `rle encodes hand-computed bit-reversed run lengths`() {
        // One row of 308 pixels: 300 on, 3 off, then 5 on — so runs of 128, 128, 44 and 3 and 5.
        val row = ByteArray(308)
        for (i in 0 until 300) row[i] = 255.toByte()
        for (i in 303 until 308) row[i] = 255.toByte()
        val layer = LayerImage(308, 1, row)

        val rle = PhotonSWriter.encodeRle(layer)

        // 128 on -> reverse8(127)=0xFE | 1 = 0xFF ; 128 on -> 0xFF ; 44 on -> reverse8(43)=0xD4 | 1 = 0xD5
        // 3 off -> reverse8(2)=0x40 | 0 = 0x40 ; 5 on -> reverse8(4)=0x20 | 1 = 0x21
        assertEquals(listOf(0xFF, 0xFF, 0xD5, 0x40, 0x21), rle.map { it.toInt() and 0xFF })
        // and the independent decoder agrees with the original mask
        val decoded = decodeRle(rle, 308)
        assertEquals(305, decoded.count { (it.toInt() and 0xFF) == 255 })
        assertEquals(3, decoded.count { (it.toInt() and 0xFF) == 0 })
    }

    @Test
    fun `the header tag is big-endian`() {
        val file = writeFile(listOf(mask(8, 8) { false }))
        // 0x00000002 then 0x0031 — little-endian would put 02 00 00 00 31 00 first.
        assertEquals(listOf(0x00, 0x00, 0x00, 0x02, 0x00, 0x31), file.take(6).map { it.toInt() and 0xFF })
    }

    // ---- 2. structural -------------------------------------------------------

    @Test
    fun `header and layer defs carry the settings the printer needs`() {
        val layers = List(3) { mask(16, 16) { it % 3 == 0 } }
        val parsed = parse(writeFile(layers))

        assertEquals(2, parsed.tag1)
        assertEquals(49, parsed.tag2)
        assertEquals(0.047, parsed.pixelSizeMm, 1e-9) // 47.25 um -> 0.04725 mm, rounded to 3 dp
        assertEquals(0.05, parsed.layerHeight, 1e-9)
        assertEquals(8.0, parsed.exposure, 1e-9)
        assertEquals(60.0, parsed.bottomExposure, 1e-9)
        assertEquals(5, parsed.bottomLayers)
        assertEquals(1.0, parsed.lightOff, 1e-9)
        assertEquals(5.0, parsed.liftHeight, 1e-9)
        // The format stores mm/s; the settings are mm/min.
        assertEquals(1.0, parsed.liftSpeedMmPerSec, 1e-9)
        assertEquals(2.5, parsed.retractSpeedMmPerSec, 1e-9)
        assertEquals(12.5, parsed.volumeMl, 1e-9)
        assertEquals(224, parsed.previewW)
        assertEquals(168, parsed.previewH)
        assertEquals(224 * 168 * 2, parsed.previewBytes.size)
        assertEquals(3, parsed.layerCount)
        assertTrue(parsed.dataSizes.all { it > 0 })
    }

    // ---- 3. round-trip -------------------------------------------------------

    @Test
    fun `every layer round-trips pixel for pixel`() {
        // A realistic full-res mask: a filled disc, so runs cross the 128-pixel split.
        val layers = listOf(
            mask(1440, 2560) { false },
            mask(1440, 2560) { i ->
                val x = i % 1440 - 720
                val y = i / 1440 - 1280
                x * x + y * y < 500 * 500
            },
            mask(1440, 2560) { it % 1440 < 700 },
        )
        val parsed = parse(writeFile(layers))

        assertEquals(3, parsed.layerCount)
        layers.forEachIndexed { i, original ->
            val decoded = decodeRle(parsed.layers[i], 1440 * 2560)
            assertEquals(original.pixels.size, decoded.size)
            for (p in decoded.indices) {
                if (original.pixels[p] != decoded[p]) {
                    throw AssertionError("layer $i differs at pixel $p: ${original.pixels[p]} vs ${decoded[p]}")
                }
            }
        }
    }

    @Test
    fun `an all-black layer costs one byte per 128 pixels`() {
        val rle = PhotonSWriter.encodeRle(mask(1440, 2560) { false })
        val pixels = 1440 * 2560
        // ceil(pixels / 128) chunks, each one byte
        assertEquals((pixels + 127) / 128, rle.size)
    }
}
