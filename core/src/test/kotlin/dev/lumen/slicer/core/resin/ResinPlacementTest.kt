package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.MeshPrimitives
import dev.lumen.slicer.core.mesh.Placement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Resin bed placement.
 *
 * This exists because the placement was previously an *assumption* — a code comment asserting the
 * engine's bed was centred on the origin. Slicing disproved it: a mesh centred on the origin landed
 * in the corner, half clipped, with the layers above the clipped part empty. The invariant is now a
 * test instead of a comment.
 */
class ResinPlacementTest {

    private val monoX6k = ResinPrinter.byName("Anycubic Photon Mono X 6K")!!
    private val monoX = ResinPrinter.byName("Anycubic Photon Mono X")!!

    @Test
    fun `the bed centre is the origin, because the SLA bed is centred`() {
        val (cx, cy) = ResinPlacement.bedCentre(monoX6k)

        // PrusaSlicer's SLA profiles carry a bed_shape centred on (0,0) -- FDM beds are corner-origin,
        // and assuming the FDM convention here is what made the bed degenerate and clipped the model.
        assertEquals(0f, cx, 0.001f)
        assertEquals(0f, cy, 0.001f)
    }

    @Test
    fun `a model placed at the bed centre sits inside the bed with even margins`() {
        val model = MeshPrimitives.box(40f, 30f, 20f, 0f, 0f, 10f)

        val (cx, cy) = ResinPlacement.bedCentre(monoX6k)
        val placed = Placement(centerX = cx, centerY = cy).place(model)

        assertTrue(ResinPlacement.isOnBed(placed, monoX6k), "must be fully on the plate")
        val m = ResinPlacement.margins(placed, monoX6k)
        assertEquals(m[0], m[1], 0.01f, "left and right margins must match")
        assertEquals(m[2], m[3], 0.01f, "front and back margins must match")
    }

    @Test
    fun `a model centred on the origin sits on the centred bed`() {
        // The bug, captured: a mesh at the origin on a CORNER-origin bed hangs off it, which is what
        // the engine did when bed_shape was missing. With bed_shape sent the bed is centred, so the
        // same mesh is fine -- and this test is the difference between the two conventions.
        val model = MeshPrimitives.box(40f, 30f, 20f, 0f, 0f, 10f)

        val placed = Placement(centerX = 0f, centerY = 0f).place(model)

        assertTrue(
            ResinPlacement.isOnBed(placed, monoX6k),
            "a model at the origin belongs on a bed centred on the origin",
        )
        val m = ResinPlacement.margins(placed, monoX6k)
        assertEquals(m[0], m[1], 0.01f, "left and right margins must match")
        assertEquals(m[2], m[3], 0.01f, "front and back margins must match")
    }

    @Test
    fun `a model larger than the bed is reported off-bed rather than silently clipped`() {
        val huge = MeshPrimitives.box(300f, 300f, 20f, 0f, 0f, 10f)
        val (cx, cy) = ResinPlacement.bedCentre(monoX)
        val placed = Placement(centerX = cx, centerY = cy).place(huge)

        assertFalse(ResinPlacement.isOnBed(placed, monoX))
    }

    @Test
    fun `placement drops the model onto the plate`() {
        val floating = MeshPrimitives.box(20f, 20f, 20f, 0f, 0f, 100f)
        val (cx, cy) = ResinPlacement.bedCentre(monoX6k)

        val placed = Placement(centerX = cx, centerY = cy).place(floating)

        assertEquals(0f, placed.bounds().min.z, 1e-3f)
        assertTrue(ResinPlacement.isOnBed(placed, monoX6k))
    }

    @Test
    fun `an empty mesh is never on the bed`() {
        assertFalse(ResinPlacement.isOnBed(Mesh(FloatArray(0)), monoX6k))
    }
}
