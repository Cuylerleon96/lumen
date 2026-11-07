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
    fun `the bed centre is half the panel`() {
        val (cx, cy) = ResinPlacement.bedCentre(monoX6k)

        // Measured: the raster covers world (0,0)->(panelW, panelH), so the middle of it is panel/2.
        // A model centred on the origin hangs off the corner and is silently clipped.
        assertEquals(99.072f, cx, 0.01f, "198.144 mm panel / 2")
        assertEquals(61.92f, cy, 0.01f, "123.84 mm panel / 2")
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
    fun `a model centred on the origin is off the plate`() {
        // The bug, captured: this is the state the code was in when the model was clipped into the
        // raster's corner. The raster is corner-origin, so the origin is its corner, not its middle.
        val model = MeshPrimitives.box(40f, 30f, 20f, 0f, 0f, 10f)

        val placed = Placement(centerX = 0f, centerY = 0f).place(model)

        assertFalse(
            ResinPlacement.isOnBed(placed, monoX6k),
            "the origin is the corner of a corner-origin raster — this is the clipping bug",
        )
    }

    @Test
    fun `a model placed at half the panel sits on the plate with even margins`() {
        val model = MeshPrimitives.box(40f, 30f, 20f, 0f, 0f, 10f)

        val (cx, cy) = ResinPlacement.bedCentre(monoX6k)
        val placed = Placement(centerX = cx, centerY = cy).place(model)

        assertTrue(ResinPlacement.isOnBed(placed, monoX6k), "must be fully on the plate")
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
