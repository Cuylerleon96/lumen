package dev.lumen.slicer.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalibrationModelsTest {

    @Test
    fun `dimension cube is the requested size`() {
        val m = CalibrationModels.dimensionCube(20f)
        val b = m.bounds()
        assertEquals(20f, b.size.x, 1e-3f)
        assertEquals(20f, b.size.y, 1e-3f)
        assertEquals(20f, b.size.z, 1e-3f)
        assertEquals(0f, b.min.z, 1e-3f)   // sits on the bed
        assertEquals(12, m.triangleCount)
    }

    @Test
    fun `step bar has one cube per step`() {
        val m = CalibrationModels.stepBar(steps = 5)
        assertEquals(5 * 12, m.triangleCount)
        assertTrue(m.bounds().size.x > 5f)   // spans multiple cubes
    }

    @Test
    fun `fdm tower stacks its segments from the bed`() {
        val m = CalibrationModels.fdmTower(segments = 4, segmentHeightMm = 10f)

        val b = m.bounds()
        assertEquals(0f, b.min.z, 1e-3f)
        assertEquals(40f, b.size.z, 1e-3f)
        // The footprint is the bottom (widest) segment: each step above is inset, never wider.
        assertEquals(20f, b.size.x, 1e-3f)
        assertEquals(20f, b.size.y, 1e-3f)
    }

    @Test
    fun `the tower is a real union, not stacked boxes`() {
        val segments = 4
        val m = CalibrationModels.fdmTower(segments = segments, segmentHeightMm = 10f)

        // Volume is the same either way (each box contributes its own signed volume), so it cannot
        // tell a union from a soup -- it only confirms no material was lost or double-counted.
        val expected = (0 until segments).sumOf { i ->
            val side = (20.0 - i * 0.5)
            side * side * 10.0
        }
        assertEquals(expected, m.volumeMm3(), 1.0, "vol=${m.volumeMm3()}")

        // The discriminator: a ray down the tower's axis crosses its top and its bottom -- two
        // surfaces. Stacked boxes would cross two per box, i.e. 2 * segments, because each interface
        // keeps both faces. (A triangle count does NOT work here: unioning stepped boxes removes the
        // interface faces but triangulates the annulus left on the larger box, so the count can rise.)
        assertEquals(2, rayHits(m, Vec3(0.3f, 0.1f, 100f), Vec3(0f, 0f, -1f)))
    }

    @Test
    fun `tower boundaries are the segment starts above the bed`() {
        assertEquals(listOf(10f, 20f, 30f), CalibrationModels.towerBoundaries(4, 10f))
        assertEquals(emptyList(), CalibrationModels.towerBoundaries(1, 10f))
    }

    @Test
    fun `a tower whose inset would consume a segment is rejected`() {
        val error = kotlin.runCatching { CalibrationModels.fdmTower(segments = 50, widthMm = 20f, insetPerSegmentMm = 1f) }
        assertTrue(error.isFailure, "an inset that vanishes a segment must fail loudly, not emit junk")
    }

    /** Möller–Trumbore: how many triangles of [mesh] the ray `p + t*d` (t > 0) hits. */
    private fun rayHits(mesh: Mesh, p: Vec3, d: Vec3): Int {
        val pos = mesh.positions
        val eps = 1e-6f
        var hits = 0
        var i = 0
        while (i < pos.size) {
            val ax = pos[i]; val ay = pos[i + 1]; val az = pos[i + 2]
            val bx = pos[i + 3]; val by = pos[i + 4]; val bz = pos[i + 5]
            val cx = pos[i + 6]; val cy = pos[i + 7]; val cz = pos[i + 8]
            val e1x = bx - ax; val e1y = by - ay; val e1z = bz - az
            val e2x = cx - ax; val e2y = cy - ay; val e2z = cz - az
            val hx = d.y * e2z - d.z * e2y
            val hy = d.z * e2x - d.x * e2z
            val hz = d.x * e2y - d.y * e2x
            val det = e1x * hx + e1y * hy + e1z * hz
            if (det > -eps && det < eps) { i += 9; continue }
            val inv = 1f / det
            val sx = p.x - ax; val sy = p.y - ay; val sz = p.z - az
            val u = (sx * hx + sy * hy + sz * hz) * inv
            if (u < 0f || u > 1f) { i += 9; continue }
            val qx = sy * e1z - sz * e1y
            val qy = sz * e1x - sx * e1z
            val qz = sx * e1y - sy * e1x
            val v = (d.x * qx + d.y * qy + d.z * qz) * inv
            if (v < 0f || u + v > 1f) { i += 9; continue }
            val t = (e2x * qx + e2y * qy + e2z * qz) * inv
            if (t > eps) hits++
            i += 9
        }
        return hits
    }
}
