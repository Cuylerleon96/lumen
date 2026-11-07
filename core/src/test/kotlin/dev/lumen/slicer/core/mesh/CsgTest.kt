package dev.lumen.slicer.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boolean CSG over meshes.
 *
 * Method: every test states a **volume identity** that must hold by set algebra on solids
 * (`V(a−b) = V(a) − V(a∩b)`, `V(a∪b) = V(a) + V(b) − V(a∩b)`, `V(a∩b)`) and checks the mesh the
 * algorithm actually produced against it. Volume is the right observable: it is one number that
 * collapses topology, winding and watertightness at once, so a hole in the result, a dropped
 * fragment or an unflipped cavity all show up as a wrong number rather than a plausible-looking mesh.
 *
 * The reference solid is an axis-aligned box with an analytically known volume, and the tools are
 * chosen so the intersection volume is exact — no tolerance slack needed for the algebra itself.
 */
class CsgTest {

    private fun box(sx: Float, sy: Float, sz: Float, cx: Float = 0f, cy: Float = 0f, cz: Float = 0f) =
        MeshPrimitives.box(sx, sy, sz, cx, cy, cz)

    // ---- subtract -------------------------------------------------------------

    @Test
    fun `subtracting a fully enclosed box leaves the shell with the inner volume gone`() {
        val outer = box(10f, 10f, 10f) // 1000 mm3, spans -5..5
        val inner = box(4f, 4f, 4f) // 64 mm3, spans -2..2

        val result = Csg.subtract(outer, inner)

        assertEquals(1000.0 - 64.0, result.volumeMm3(), 0.05, "vol=${result.volumeMm3()}")
        assertTrue(result.triangleCount > outer.triangleCount, "the cavity adds surface")
    }

    @Test
    fun `subtracting a box that half overlaps one face removes only the overlap`() {
        val outer = box(10f, 10f, 10f) // z spans -5..5
        val tool = box(4f, 4f, 4f, 0f, 0f, 5f) // z spans 3..7 -> overlap 4x4x2 = 32

        val result = Csg.subtract(outer, tool)

        assertEquals(1000.0 - 32.0, result.volumeMm3(), 0.05, "vol=${result.volumeMm3()}")
    }

    @Test
    fun `subtracting a disjoint box changes nothing`() {
        val outer = box(10f, 10f, 10f)
        val elsewhere = box(2f, 2f, 2f, 50f, 0f, 0f)

        val result = Csg.subtract(outer, elsewhere)

        assertEquals(1000.0, result.volumeMm3(), 0.05, "vol=${result.volumeMm3()}")
    }

    @Test
    fun `subtracting a box that contains the subject leaves nothing`() {
        val small = box(4f, 4f, 4f)
        val big = box(20f, 20f, 20f)

        val result = Csg.subtract(small, big)

        assertEquals(0.0, result.volumeMm3(), 0.05, "vol=${result.volumeMm3()}")
    }

    // ---- union / intersect ----------------------------------------------------

    @Test
    fun `union of two half overlapping boxes counts the shared block once`() {
        val a = box(10f, 10f, 10f, -2.5f, 0f, 0f) // x spans -7.5..2.5
        val b = box(10f, 10f, 10f, 2.5f, 0f, 0f) // x spans -2.5..7.5

        val result = Csg.union(a, b)

        // 1000 + 1000 - (5 x 10 x 10)
        assertEquals(1500.0, result.volumeMm3(), 0.1, "vol=${result.volumeMm3()}")
    }

    @Test
    fun `intersection of two half overlapping boxes is the shared block`() {
        val a = box(10f, 10f, 10f, -2.5f, 0f, 0f)
        val b = box(10f, 10f, 10f, 2.5f, 0f, 0f)

        val result = Csg.intersect(a, b)

        assertEquals(500.0, result.volumeMm3(), 0.1, "vol=${result.volumeMm3()}")
    }

    @Test
    fun `intersection of disjoint boxes is empty`() {
        val result = Csg.intersect(box(4f, 4f, 4f), box(4f, 4f, 4f, 50f, 0f, 0f))

        assertTrue(result.isEmpty, "expected an empty mesh, got ${result.triangleCount} triangles")
    }

    // ---- the pruned (production) path -----------------------------------------

    @Test
    fun `a cylinder drilled through a box removes pi r squared h`() {
        val r = 2f
        val segmentCount = 48
        val box20 = box(20f, 20f, 20f)
        val bit = MeshPrimitives.cylinderAlong(
            radiusMm = r,
            lengthMm = 30f,
            origin = Vec3(0f, 0f, -15f),
            direction = Vec3(0f, 0f, 1f),
            segments = segmentCount,
        )

        val result = Csg.subtract(box20, bit)

        // The bit is a 48-gon prism, not a circle: its cross-section is
        //   0.5 * n * r^2 * sin(2*pi/n)  (99.857% of pi r^2 at n = 48)
        // so assert against the discretisation we actually asked for, not against pi.
        val polygonArea = 0.5 * segmentCount * r * r * Math.sin(2.0 * Math.PI / segmentCount)
        assertEquals(8000.0 - polygonArea * 20.0, result.volumeMm3(), 0.5, "vol=${result.volumeMm3()}")
    }

    @Test
    fun `local subtract agrees with the full boolean on the same tool`() {
        val subject = box(20f, 20f, 20f)
        val bit = MeshPrimitives.cylinderAlong(
            radiusMm = 2f,
            lengthMm = 30f,
            origin = Vec3(0f, 0f, -15f),
            direction = Vec3(0f, 0f, 1f),
            segments = 48,
        )

        val full = Csg.subtract(subject, bit)
        val local = Csg.subtractLocal(subject, bit, marginMm = 3f)

        assertEquals(full.volumeMm3(), local.volumeMm3(), 0.5, "full=${full.volumeMm3()} local=${local.volumeMm3()}")
        assertTrue(local.triangleCount < full.triangleCount + subject.triangleCount)
    }

    @Test
    fun `local subtract cuts a notch that crosses a surface`() {
        val subject = box(200f, 200f, 200f) // 8 000 000 mm3, spans -100..100
        // A notch straddling the +X and +Y faces near that corner: 2 x 2 x 4 = 16 mm3 inside.
        val notch = box(4f, 4f, 4f, 100f, 100f, 0f)

        val local = Csg.subtractLocal(subject, notch, marginMm = 2f)

        assertEquals(8_000_000.0 - 16.0, local.volumeMm3(), 1.0, "vol=${local.volumeMm3()}")
    }

    /**
     * The documented limit of the pruned path: a tool sitting **entirely inside** a coarse mesh
     * touches no triangle, so nothing is selected and the call is a no-op. Real drill bits always
     * cross the surface (that is what a drain hole *is*), and a real STL is fine-grained enough that
     * the surrounding walls fall inside the margin — but a sealed internal void needs the full
     * [Csg.subtract].
     */
    @Test
    fun `a tool entirely inside a coarse mesh selects nothing`() {
        val subject = box(200f, 200f, 200f)
        val buried = box(4f, 4f, 4f, 90f, 90f, 0f)

        val local = Csg.subtractLocal(subject, buried, marginMm = 2f)
        val full = Csg.subtract(subject, buried)

        assertEquals(8_000_000.0, local.volumeMm3(), 1.0, "the pruned path sees no triangle to cut")
        assertEquals(8_000_000.0 - 64.0, full.volumeMm3(), 1.0, "the full boolean still cuts it")
    }
}
