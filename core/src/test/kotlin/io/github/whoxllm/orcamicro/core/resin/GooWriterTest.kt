package io.github.whoxllm.orcamicro.core.resin

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GooWriterTest {

    // ---- a tiny decoder mirroring the GooFile grammar, to round-trip our encoder ----
    private fun decodeRle(rle: ByteArray): ByteArray {
        assertEquals(0x55, rle[0].toInt() and 0xFF, "layer magic")
        var sum = 0
        for (j in 1 until rle.size - 1) sum += rle[j].toInt() and 0xFF
        assertEquals(sum.inv() and 0xFF, rle[rle.size - 1].toInt() and 0xFF, "checksum")
        val out = ArrayList<Byte>()
        var i = 1
        var color = 0
        while (i < rle.size - 1) {
            val chunkType = (rle[i].toInt() and 0xFF) shr 6
            val stride: Int
            when (chunkType) {
                0 -> { color = 0; stride = readRun(rle, i) { n -> i = n } }
                3 -> { color = 255; stride = readRun(rle, i) { n -> i = n } }
                1 -> { color = rle[i + 1].toInt() and 0xFF; stride = readRun(rle, i, valueExtra = 1) { n -> i = n } }
                else -> error("diff chunk not used by our encoder")
            }
            repeat(stride) { out.add(color.toByte()) }
            i += 1
        }
        return out.toByteArray()
    }

    private inline fun readRun(rle: ByteArray, i: Int, valueExtra: Int = 0, setI: (Int) -> Unit): Int {
        val b0 = rle[i].toInt() and 0xFF
        val lenType = (b0 shr 4) and 0x3
        val base = i + 1 + valueExtra
        return when (lenType) {
            0 -> { setI(i + valueExtra); b0 and 0xF }
            1 -> { setI(i + 1 + valueExtra); (rle[base].toInt() and 0xFF shl 4) + (b0 and 0xF) }
            2 -> { setI(i + 2 + valueExtra); (rle[base].toInt() and 0xFF shl 12) + (rle[base + 1].toInt() and 0xFF shl 4) + (b0 and 0xF) }
            else -> { setI(i + 3 + valueExtra); (rle[base].toInt() and 0xFF shl 20) + (rle[base + 1].toInt() and 0xFF shl 12) + (rle[base + 2].toInt() and 0xFF shl 4) + (b0 and 0xF) }
        }
    }

    @Test
    fun `rle round-trips a mixed mask`() {
        val mask = ByteArray(600)
        for (k in 10 until 300) mask[k] = 127      // long full run
        for (k in 400 until 410) mask[k] = 64      // short gray run
        val rle = GooWriter.encodeRle(mask)
        val back = decodeRle(rle)
        assertEquals(mask.size, back.size)
        for (k in mask.indices) {
            val expected = ((mask[k].toInt() and 0xFF) * 2).coerceAtMost(255)
            assertEquals(expected, back[k].toInt() and 0xFF, "pixel $k")
        }
    }

    @Test
    fun `writes a valid header and footer`() {
        val layers = listOf(ByteArray(4) { 127 }, ByteArray(4))
        val out = ByteArrayOutputStream()
        GooWriter.write(layers, 2, 2, 0.05, 2.5, 25.0, 5, 7.0, 0.1, 68.0, 120.0, out)
        val b = out.toByteArray()
        // header starts with "V3.0"
        assertEquals("V3.0", String(b, 0, 4, Charsets.US_ASCII))
        // footer: last 8 bytes are the magic
        assertTrue(b.copyOfRange(b.size - 8, b.size).contentEquals(byteArrayOf(0x07, 0, 0, 0, 0x44, 0x4C, 0x50, 0)))
        // LayerDefAddress (u32 BE at size-8 of header) == 0x...  we can find it via the encoded layers count
        assertTrue(b.size > 180 * 1024)   // header carries two big previews (116² + 290² RGB565 ≈ 195 KB)
    }
}
