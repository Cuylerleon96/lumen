package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh

/**
 * Where a resin model sits in the SLA engine's frame.
 *
 * **The SLA bed is centred on the origin.** PrusaSlicer's SLA printer profiles carry a `bed_shape`
 * polygon centred on (0,0); FDM beds are corner-origin instead, and that difference is the trap. A
 * model centred on the origin is centred on the bed, so [bedCentre] is the origin.
 *
 * The history is worth keeping, because the obvious reading of the evidence was wrong twice:
 *  - The engine's bed is defined by **`bed_shape`**, not by `display_width`/`display_height` (those
 *    only describe the panel and the raster size). The app sent the display keys but no `bed_shape`,
 *    so the bed was degenerate, the object was placed at the bed's corner and **clipped**.
 *  - That clipping looked exactly like a placement bug, and a standalone CLI harness appeared to
 *    confirm it: a mesh centred on the origin came out clipped into the corner (700 KB of layers)
 *    while the same mesh translated to (w/2, h/2) came out complete (3.0 MB). The harness was also
 *    running without a `bed_shape`, so it was demonstrating the same missing key, not the placement.
 *  - On device the tell was that BOTH placements produced byte-identical output with the ink jammed
 *    into the bottom-left (margins left=0, bottom=0): the engine was placing the object itself and
 *    ignoring our coordinates entirely. An earlier test of this that compared populated LAYER COUNTS
 *    passed while the bug was present -- clipping removes material *within* layers, not layers.
 *
 * With `bed_shape` sent, the bed is the panel centred on the origin, and the model is centred by
 * being at the origin. [margins] and [isOnBed] therefore measure against a CENTRED bed.
 */
object ResinPlacement {

    /** Centre of the engine's bed in its own frame — the origin, because the SLA bed is centred. */
    fun bedCentre(printer: ResinPrinter): Pair<Float, Float> = 0f to 0f

    /** Footprint margins (left, right, front, back) in mm for a [mesh] on [printer]'s centred bed. */
    fun margins(mesh: Mesh, printer: ResinPrinter): FloatArray {
        val b = mesh.bounds()
        val halfW = (printer.buildWidthMm / 2.0).toFloat()
        val halfD = (printer.buildDepthMm / 2.0).toFloat()
        return floatArrayOf(
            b.min.x + halfW,
            halfW - b.max.x,
            b.min.y + halfD,
            halfD - b.max.y,
        )
    }

    /**
     * True when [mesh]'s footprint sits inside the centred bed with its base on the plate — i.e. the
     * slice will not silently lose material off the edge.
     */
    fun isOnBed(mesh: Mesh, printer: ResinPrinter, epsMm: Float = 0.01f): Boolean {
        if (mesh.isEmpty) return false
        val b = mesh.bounds()
        val halfW = (printer.buildWidthMm / 2.0).toFloat()
        val halfD = (printer.buildDepthMm / 2.0).toFloat()
        return b.min.x >= -halfW - epsMm && b.max.x <= halfW + epsMm &&
            b.min.y >= -halfD - epsMm && b.max.y <= halfD + epsMm &&
            kotlin.math.abs(b.min.z) <= epsMm
    }
}
