package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IslandDetectorTest {

    private fun mask(w: Int, h: Int, vararg on: Pair<Int, Int>): ByteArray {
        val m = ByteArray(w * h)
        for ((x, y) in on) m[y * w + x] = 1
        return m
    }

    @Test
    fun `supported region is not an island`() {
        val w = 4; val h = 2
        val bottom = mask(w, h, 0 to 0, 1 to 0)      // a 2-px bar on the plate
        val top = mask(w, h, 0 to 0, 1 to 0)         // same bar directly above -> supported
        assertEquals(0, IslandDetector.detect(listOf(bottom, top), w, h).size)
    }

    @Test
    fun `detached region above an empty layer is an island`() {
        val w = 4; val h = 2
        val empty = mask(w, h)                        // nothing on layer 1
        val floating = mask(w, h, 3 to 0, 3 to 1)     // a 2-px blob with no support below
        val islands = IslandDetector.detect(listOf(mask(w, h, 0 to 0), empty, floating), w, h)
        assertEquals(1, islands.size)
        assertEquals(2, islands[0].layer)
        assertEquals(2, islands[0].pixelCount)
        assertEquals(3, islands[0].minX)
    }

    @Test
    fun `minPixels filters tiny specks`() {
        val w = 4; val h = 1
        val speck = mask(w, h, 2 to 0)
        assertEquals(0, IslandDetector.detect(listOf(mask(w, h), speck), w, h, minPixels = 3).size)
        assertEquals(1, IslandDetector.detect(listOf(mask(w, h), speck), w, h, minPixels = 1).size)
    }
}
