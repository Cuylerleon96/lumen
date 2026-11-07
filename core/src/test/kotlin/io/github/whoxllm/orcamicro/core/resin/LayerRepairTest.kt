package io.github.whoxllm.orcamicro.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals

class LayerRepairTest {

    @Test
    fun `removes a 2-pixel speck but keeps a 5-pixel blobs`() {
        val w = 10; val h = 10
        val m = ByteArray(w * h)
        // blob of 5
        for (k in 0..4) m[1 * w + 1 + k] = 127
        // speck of 2
        m[8 * w + 8] = 127; m[8 * w + 9] = 127
        val out = LayerRepair.removeSpecks(m, w, h, minPixels = 3)
        assertEquals(0, out[8 * w + 8].toInt())
        assertEquals(0, out[8 * w + 9].toInt())
        assertEquals(127, out[1 * w + 3].toInt())
    }

    @Test
    fun `fills a single-pixel pinhole`() {
        val w = 5; val h = 5
        val m = ByteArray(w * h)
        for (y in 1..3) for (x in 1..3) m[y * w + x] = 127
        m[2 * w + 2] = 0                 // pinhole in the middle
        val out = LayerRepair.fillHoles(m, w, h)
        assertEquals(127, out[2 * w + 2].toInt())
    }
}
