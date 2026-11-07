package io.github.whoxllm.orcamicro.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AntiAliaserTest {

    private fun mask(w: Int, h: Int, vararg on: Pair<Int, Int>): ByteArray {
        val m = ByteArray(w * h)
        for ((x, y) in on) m[y * w + x] = 127
        return m
    }

    @Test
    fun `boundary pixels get a mid level, interior stays full`() {
        // a 3x3 solid block inside a 5x5: centre interior stays full, the ring becomes boundary
        val m = ByteArray(5 * 5)
        for (y in 1..3) for (x in 1..3) m[y * 5 + x] = 127
        val aa = AntiAliaser.apply(m, 5, 5, levels = 2)
        assertEquals(127, aa[2 * 5 + 2].toInt())   // centre interior → full
        assertEquals(63, aa[1 * 5 + 1].toInt())    // block corner → boundary edge level
        assertEquals(0, aa[0].toInt())             // outside untouched
    }

    @Test
    fun `levels 1 is a no-op`() {
        val m = mask(3, 3, 1 to 1)
        assertTrue(AntiAliaser.apply(m, 3, 3, levels = 1).contentEquals(m))
    }
}
