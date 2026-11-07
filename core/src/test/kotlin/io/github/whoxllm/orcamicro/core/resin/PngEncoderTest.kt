package io.github.whoxllm.orcamicro.core.resin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class PngEncoderTest {
    @Test
    fun encodesReadableGrayscalePng() {
        val w = 4
        val h = 3
        val gray = ByteArray(w * h) { if (it % 2 == 0) 0xFF.toByte() else 0 }
        val png = PngEncoder.encode(w, h, gray)

        // PNG signature.
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), png.take(4).map { it.toInt() and 0xFF })

        val img = ImageIO.read(ByteArrayInputStream(png))
        assertNotNull(img, "the encoder must produce a PNG a standard decoder can read")
        assertEquals(w, img.width)
        assertEquals(h, img.height)
        assertEquals(0xFF, img.getRGB(0, 0) and 0xFF) // first pixel was 0xFF
        assertEquals(0x00, img.getRGB(1, 0) and 0xFF) // second pixel was 0x00
    }

    @Test
    fun rejectsWrongBufferSize() {
        val error = runCatching { PngEncoder.encode(2, 2, ByteArray(3)) }.exceptionOrNull()
        assertNotNull(error)
    }
}
