package dev.lumen.slicer.core.mesh

import dev.lumen.slicer.core.settings.BedShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Is the model centred on the plate?" — the invariant behind that question, pinned by a test.
 *
 * A fresh model is placed with its footprint centre at [Placement.centerX]/[centerY], so what a user
 * sees is asserted directly: the footprint centred on the plate, resting on it. The two cases that
 * genuinely differ are both covered, because they are where an implementation goes wrong:
 *
 *  * an FDM bed, whose `bed_shape` need not start at the origin — so the centre is NOT `width / 2`;
 *  * a resin panel, where the engine's raster is **corner-origin**, so the model has to sit at half the
 *    PANEL or the print silently loses everything off the corner.
 */
class PlacementCentreTest {

    /** A 20 × 20 × 10 box sitting on z = 0, like a real model dropped on the plate. */
    private val box = MeshPrimitives.box(20f, 20f, 10f, cx = 0f, cy = 0f, cz = 5f)

    private fun placedAt(bed: BedShape, scale: Float = 100f): Mesh =
        Placement(centerX = bed.centerX, centerY = bed.centerY, scalePercent = scale).place(box)

    @Test
    fun `a model placed on an FDM bed ends up centred on it and resting on it`() {
        val bed = BedShape.rectangle(0f, 0f, 220f, 220f)

        val b = placedAt(bed).bounds()

        assertEquals(bed.centerX, b.center.x, 1e-3f)
        assertEquals(bed.centerY, b.center.y, 1e-3f)
        assertEquals(0f, b.min.z, 1e-3f, "the model rests on the plate")
    }

    @Test
    fun `a bed that does not start at the origin still centres properly`() {
        // This is why the centre is taken from the bed's own bounds rather than assumed to be
        // (width / 2, depth / 2). Note `rectangle` takes (originX, originY, WIDTH, DEPTH) — not the far
        // corner — so a 220 mm bed starting at -110 is `(-110, -110, 220, 220)`.
        val bed = BedShape.rectangle(-110f, -110f, 220f, 220f)

        val b = placedAt(bed).bounds()

        assertEquals(220f, bed.width, 1e-3f)
        assertEquals(-110f, bed.minX, 1e-3f)
        assertEquals(0f, bed.centerX, 1e-3f)
        assertEquals(bed.centerX, b.center.x, 1e-3f)
        assertEquals(bed.centerY, b.center.y, 1e-3f)
    }

    @Test
    fun `a model on the resin panel is centred on the panel, not on the FDM bed`() {
        // The Photon Mono X 6K panel. Half of it is (99.072, 61.92); pinning that here is what stops
        // the resin side from quietly centring against the FDM machine bed instead — the bug that made
        // this worth a test.
        val panel = BedShape.rectangle(0f, 0f, 198.144f, 123.84f)

        val b = placedAt(panel).bounds()

        assertEquals(99.072f, b.center.x, 1e-2f)
        assertEquals(61.92f, b.center.y, 1e-2f)
        assertTrue(
            b.min.x >= 0f && b.min.y >= 0f && b.max.x <= 198.144f && b.max.y <= 123.84f,
            "the footprint must sit inside the panel: ${b.min} .. ${b.max}",
        )
    }

    @Test
    fun `scaling keeps the footprint centred`() {
        val bed = BedShape.rectangle(0f, 0f, 220f, 220f)

        val small = placedAt(bed, scale = 25f).bounds()
        val big = placedAt(bed, scale = 200f).bounds()

        assertEquals(bed.centerX, small.center.x, 1e-3f)
        assertEquals(bed.centerX, big.center.x, 1e-3f)
        assertEquals(40f, big.size.x, 1e-3f, "200% of a 20 mm box is 40 mm")
    }
}
