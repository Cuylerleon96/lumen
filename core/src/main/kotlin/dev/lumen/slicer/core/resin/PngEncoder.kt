package dev.lumen.slicer.core.resin

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Minimal 8-bit grayscale PNG encoder. Uses only `java.util.zip` (Deflater + CRC32), which is
 * present on both the JVM and Android — so no platform image libraries are needed, and the app
 * and its unit tests share the exact same bytes.
 */
object PngEncoder {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Encodes an 8-bit grayscale image. [gray] is [width]×[height] bytes, row-major. */
    fun encode(width: Int, height: Int, gray: ByteArray): ByteArray {
        require(width > 0 && height > 0) { "image must be non-empty" }
        require(gray.size == width * height) { "gray must hold width*height bytes" }

        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)

        val ihdr = ByteArrayOutputStream()
        writeInt(ihdr, width)
        writeInt(ihdr, height)
        ihdr.write(8) // bit depth
        ihdr.write(0) // colour type: grayscale
        ihdr.write(0) // compression method
        ihdr.write(0) // filter method
        ihdr.write(0) // interlace method
        writeChunk(out, "IHDR", ihdr.toByteArray())

        // IDAT: each scanline prefixed with a filter byte (0 = none), then zlib-compressed.
        val raw = ByteArray(height * (width + 1))
        var r = 0
        var s = 0
        for (y in 0 until height) {
            raw[r++] = 0
            System.arraycopy(gray, s, raw, r, width)
            r += width
            s += width
        }
        writeChunk(out, "IDAT", deflate(raw))

        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(data)
        deflater.finish()
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        writeInt(out, data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(out, crc.value.toInt())
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
