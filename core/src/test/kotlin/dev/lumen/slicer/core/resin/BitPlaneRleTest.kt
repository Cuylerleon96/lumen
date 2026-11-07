package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bit-plane RLE codec (CBDDLP / Chitubox v1-v2 family).
 *
 * The byte-level test is the important one: this codec stores the run length **directly** in the low
 * 7 bits, while the PhotonS codec we already ship stores a **bit-reversed** length in the same bits.
 * Both round-trip perfectly against their own decoder, so only a hand-computed byte assertion tells
 * you which one you actually implemented.
 */
class BitPlaneRleTest {

    private fun plane(w: Int, h: Int, on: (Int) -> Boolean) =
        ByteArray(w * h) { if (on(it)) 255.toByte() else 0 }

    @Test
    fun `plane bytes carry the run length directly`() {
        // 300 on, then 3 off, then 5 on -> runs of 125, 125, 50 (on), 3 (off), 5 (on)
        val px = ByteArray(308)
        for (i in 0 until 300) px[i] = 255.toByte()
        for (i in 303 until 308) px[i] = 255.toByte()

        val rle = BitPlaneRle.encodePlane(px)

        assertEquals(
            listOf(0xFD, 0xFD, 0xB2, 0x03, 0x85),
            rle.map { it.toInt() and 0xFF },
            "on-run = 0x80 | count; off-run = count. A bit-reversed codec would give different bytes here.",
        )
    }

    @Test
    fun `runs longer than the 125 limit are split`() {
        val px = ByteArray(250) { 255.toByte() }

        val rle = BitPlaneRle.encodePlane(px)

        assertEquals(listOf(0xFD, 0xFD), rle.map { it.toInt() and 0xFF })
        assertEquals(250, BitPlaneRle.decodePlane(rle, 250).count { it.toInt() == 1 })
    }

    @Test
    fun `a plane round-trips into per-pixel counts`() {
        val mask = plane(64, 4) { it % 7 == 0 }

        val counts = BitPlaneRle.decodePlane(BitPlaneRle.encodePlane(mask), mask.size)

        for (i in mask.indices) {
            val expected = if (mask[i].toInt() != 0) 1 else 0
            assertEquals(expected, counts[i].toInt(), "pixel $i")
        }
    }

    @Test
    fun `trailing black is not stored and decodes as zero`() {
        val mask = plane(100, 1) { it < 10 }

        val rle = BitPlaneRle.encodePlane(mask)
        val counts = BitPlaneRle.decodePlane(rle, mask.size)

        assertEquals(2, rle.size, "one on-run then one off-run")
        assertEquals(10, counts.count { it.toInt() == 1 })
        assertEquals(90, counts.count { it.toInt() == 0 })
    }

    @Test
    fun `counts map to grayscale with the documented minus-one rule`() {
        // AA off: any count >= 1 is fully exposed.
        val one = BitPlaneRle.countsToGray(byteArrayOf(0, 1, 2, 8), antiAliasing = 1)
        assertEquals(listOf(0, 255, 255, 255), one.map { it.toInt() and 0xFF })

        // 8 AA levels: step 32, level = count*32 - 1.
        val eight = BitPlaneRle.countsToGray(byteArrayOf(0, 1, 2, 8), antiAliasing = 8)
        assertEquals(listOf(0, 31, 63, 255), eight.map { it.toInt() and 0xFF })
    }

    @Test
    fun `a binary layer survives encode and decode unchanged`() {
        // A disc, so runs cross the 125-byte split.
        val w = 200; val h = 200
        val mask = plane(w, h) { i ->
            val x = i % w - 100; val y = i / w - 100
            x * x + y * y < 70 * 70
        }

        val planes = BitPlaneRle.encodeLayer(mask, bits = 1)
        assertEquals(1, planes.size, "AA off means a single plane")
        val gray = BitPlaneRle.countsToGray(BitPlaneRle.decodePlane(planes[0], mask.size))

        assertEquals(mask.size, gray.size)
        for (i in mask.indices) {
            val expected = if (mask[i].toInt() != 0) 255 else 0
            if ((gray[i].toInt() and 0xFF) != expected) {
                throw AssertionError("pixel $i: expected $expected, got ${gray[i].toInt() and 0xFF}")
            }
        }
    }

    @Test
    fun `bit planes are ordered from bit zero`() {
        // value 2 -> only bit 1 is set, so plane 0 is empty and plane 1 is full.
        val mask = ByteArray(8) { 2 }

        val planes = BitPlaneRle.encodeLayer(mask, bits = 2)

        assertEquals(2, planes.size)
        assertEquals(0, BitPlaneRle.decodePlane(planes[0], 8).sumOf { it.toInt() }, "plane 0 (bit 0) must be empty")
        assertEquals(8, BitPlaneRle.decodePlane(planes[1], 8).sumOf { it.toInt() }, "plane 1 (bit 1) must be set")
    }

    @Test
    fun `a run longer than the limit is rejected rather than silently truncated`() {
        val error = runCatching { BitPlaneRle.encodePlane(ByteArray(10) { 255.toByte() }, maxRunLength = 200) }
        assertTrue(error.isFailure, "a run cap above 125 cannot be represented in one byte")
    }
}
