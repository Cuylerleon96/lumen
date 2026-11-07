package dev.lumen.slicer.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeshPrimitivesTest {

    @Test
    fun `cylinder has the requested bounds and triangle count`() {
        val c = MeshPrimitives.cylinder(radiusMm = 2f, heightMm = 10f, segments = 32)
        val b = c.bounds()
        assertEquals(4f, b.size.x, 1e-2f)
        assertEquals(4f, b.size.y, 1e-2f)
        assertEquals(10f, b.size.z, 1e-3f)
        assertEquals(0f, b.min.z, 1e-3f)
        // 32 segments × (2 side + 1 bottom + 1 top) triangles
        assertEquals(32 * 4, c.triangleCount)
    }

    @Test
    fun `cylinder volume is close to pi r squared h`() {
        val r = 5f; val h = 20f
        val c = MeshPrimitives.cylinder(r, h, segments = 256)
        val expected = Math.PI * r * r * h
        assertTrue(Math.abs(c.volumeMm3() - expected) / expected < 0.01, "vol=${c.volumeMm3()} exp=$expected")
    }
}
