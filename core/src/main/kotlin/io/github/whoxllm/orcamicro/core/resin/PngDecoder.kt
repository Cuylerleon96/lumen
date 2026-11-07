package io.github.whoxllm.orcamicro.core.resin

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Minimal **8-bit grayscale** PNG decoder (what PrusaSlicer's SL1 layers use).
 * Handles colour type 0 (grayscale), bit depth 8, no interlace. Undoes PNG filtering.
 * Not a general PNG reader — just enough to read SL1 layer images.
 */
object PngDecoder {

    class Image(val width: Int, val height: Int, val gray: ByteArray)

    fun decode(bytes: ByteArray): Image {
        require(bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) { "not a PNG" }
        var pos = 8
        var width = 0; var height = 0; var bitDepth = 8; var colorType = 0
        val idat = ByteArrayOutputStream()
        while (pos + 8 <= bytes.size) {
            val len = readInt(bytes, pos); val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            when (type) {
                "IHDR" -> {
                    width = readInt(bytes, dataStart); height = readInt(bytes, dataStart + 4)
                    bitDepth = bytes[dataStart + 8].toInt() and 0xFF
                    colorType = bytes[dataStart + 9].toInt() and 0xFF
                }
                "IDAT" -> idat.write(bytes, dataStart, len)
                "IEND" -> { pos = bytes.size; }
            }
            pos = dataStart + len + 4
            if (type == "IEND") break
        }
        require(bitDepth == 8 && colorType == 0) { "only 8-bit grayscale supported (got depth=$bitDepth type=$colorType)" }

        val raw = inflate(idat.toByteArray())
        val stride = width
        val out = ByteArray(width * height)
        val prev = ByteArray(stride)
        val cur = ByteArray(stride)
        var rp = 0
        for (y in 0 until height) {
            val filter = raw[rp++].toInt() and 0xFF
            System.arraycopy(raw, rp, cur, 0, stride); rp += stride
            unfilter(filter, cur, prev, stride)
            System.arraycopy(cur, 0, out, y * width, width)
            System.arraycopy(cur, 0, prev, 0, stride)
        }
        return Image(width, height, out)
    }

    private fun unfilter(filter: Int, cur: ByteArray, prev: ByteArray, stride: Int) {
        when (filter) {
            0 -> {}
            1 -> for (i in 1 until stride) cur[i] = (cur[i] + cur[i - 1]).toByte()
            2 -> for (i in 0 until stride) cur[i] = (cur[i] + prev[i]).toByte()
            3 -> for (i in 0 until stride) {
                val a = if (i >= 1) cur[i - 1].toInt() and 0xFF else 0
                cur[i] = (cur[i] + ((a + (prev[i].toInt() and 0xFF)) shr 1)).toByte()
            }
            4 -> for (i in 0 until stride) {
                val a = if (i >= 1) cur[i - 1].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= 1) prev[i - 1].toInt() and 0xFF else 0
                cur[i] = (cur[i] + paeth(a, b, c)).toByte()
            }
        }
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a); val pb = kotlin.math.abs(p - b); val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && inf.needsInput()) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    private fun readInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
