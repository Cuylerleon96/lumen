package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewRendererTest {

    @Test
    fun `packs rgb565 with the requested size and endianness`() {
        val mask = ByteArray(64 * 64) { if (it % 2 == 0) 127 else 0 }
        val be = PreviewRenderer.rgb565(mask, 64, 64, 116, 116, bigEndian = true)
        val le = PreviewRenderer.rgb565(mask, 64, 64, 116, 116, bigEndian = false)
        assertEquals(116 * 116 * 2, be.size)
        assertEquals(116 * 116 * 2, le.size)
        // same pixels, byte-swapped
        for (i in be.indices step 2) {
            assertEquals(be[i], le[i + 1])
            assertEquals(be[i + 1], le[i])
        }
    }

    @Test
    fun `cured pixels are brighter than empty ones`() {
        val mask = ByteArray(4) { if (it == 0) 127 else 0 }
        val bytes = PreviewRenderer.rgb565(mask, 2, 2, 2, 2, bigEndian = true)
        val full = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val empty = ((bytes[2 * 3].toInt() and 0xFF) shl 8) or (bytes[2 * 3 + 1].toInt() and 0xFF)
        assertTrue(full > empty, "full=$full empty=$empty")
    }
}
