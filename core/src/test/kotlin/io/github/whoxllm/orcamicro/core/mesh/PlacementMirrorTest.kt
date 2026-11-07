package io.github.whoxllm.orcamicro.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacementMirrorTest {

    private fun box(w: Float, d: Float, h: Float) = TestMeshes.box(w, d, h)

    @Test
    fun `mirror X negates x about the centre`() {
        val mesh = box(10f, 4f, 4f)                 // centred box
        val p = Placement(mirrorX = true)
        val out = p.orient(mesh)
        // a mirror about the centre is a no-op for a symmetric box's bounds
        assertEquals(mesh.bounds().min.x, out.bounds().min.x, 1e-3f)
        // but an asymmetric point flips sign
        val single = Mesh(floatArrayOf(2f, 0f, 0f, 3f, 0f, 0f, 3f, 1f, 0f))
        val mirrored = Placement(mirrorX = true).orient(single)
        val c = single.bounds().center.x
        // x=2 (c+2) maps to c-2; x=3 (c+3) maps to c-3
        assertEquals(c - 2f, mirrored.positions[0], 1e-3f)
        assertEquals(c - 3f, mirrored.positions[3], 1e-3f)
    }

    @Test
    fun `no mirror leaves the mesh unchanged`() {
        val mesh = box(10f, 4f, 4f)
        val out = Placement().orient(mesh)
        assertTrue(out.positions.contentEquals(mesh.positions))
    }
}
