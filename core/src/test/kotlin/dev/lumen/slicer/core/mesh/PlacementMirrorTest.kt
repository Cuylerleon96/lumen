package dev.lumen.slicer.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals

class PlacementMirrorTest {

    // an asymmetric single triangle so a mirror is observable
    private fun tri() = Mesh(floatArrayOf(2f, 0f, 0f, 3f, 0f, 0f, 3f, 1f, 0f))

    @Test
    fun `mirror X flips a point about the centre`() {
        val single = tri()
        val c = single.bounds().center.x      // (2+3)/2 = 2.5
        val m = Placement(mirrorX = true).orient(single)
        // orient centres the mesh: x=2 -> (2-c)*-1 = 0.5 ; x=3 -> -0.5
        assertEquals(c - 2f, m.positions[0], 1e-3f)
        assertEquals(c - 3f, m.positions[3], 1e-3f)
    }

    @Test
    fun `no mirror keeps the x sign (centred)`() {
        val single = tri()
        val c = single.bounds().center.x
        val m = Placement().orient(single)
        assertEquals(2f - c, m.positions[0], 1e-3f)
        assertEquals(3f - c, m.positions[3], 1e-3f)
    }
}
