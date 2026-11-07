package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResinTrapDetectorTest {

    private fun ring(w: Int, h: Int): ByteArray {
        // a hollow box border: exposed edge, empty interior -> the interior is an enclosed void
        val m = ByteArray(w * h) { 1 }
        for (y in 1 until h - 1) for (x in 1 until w - 1) m[y * w + x] = 0
        return m
    }

    @Test
    fun `enclosed void persisting across layers is a trap`() {
        val w = 5; val h = 5
        val layers = List(5) { ring(w, h) }
        val traps = ResinTrapDetector.detect(layers, w, h, minDepth = 3)
        assertEquals(1, traps.size)
        assertEquals(9, traps[0].pixelCount)   // 3x3 interior
        assertEquals(0, traps[0].startLayer)
        assertEquals(4, traps[0].endLayer)
    }

    @Test
    fun `shallow enclosed void is ignored`() {
        val w = 5; val h = 5
        val layers = listOf(ring(w, h), ring(w, h))   // only 2 layers deep
        assertTrue(ResinTrapDetector.detect(layers, w, h, minDepth = 3).isEmpty())
    }

    @Test
    fun `solid layer has no trap`() {
        val w = 5; val h = 5
        val solid = ByteArray(w * h) { 1 }
        assertTrue(ResinTrapDetector.detect(List(5) { solid }, w, h).isEmpty())
    }
}
