package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh

/**
 * Where a resin model sits in the SLA engine's frame.
 *
 * **Centre it on half the panel.** The engine's raster covers world (0,0) → (panelW, panelH) — a
 * corner-origin mapping — so a model centred on the origin hangs off it and is silently clipped.
 *
 * Measured, because reading the source and the conventions got this wrong twice:
 *  - PrusaSlicer's SLA *profiles* carry a `bed_shape` centred on (0,0) (FDM beds are corner-origin),
 *    which is why the original code's comment claimed the bed was centred on the origin.
 *  - Slicing a marker with features at world +40 mm (X) and +30 mm (Y) produced ink only in world
 *    x∈[0, 42.7], y∈[0, 36.2]: the positive quadrant, everything negative clipped. That is a
 *    corner-origin raster regardless of the declared bed_shape.
 *  - Two earlier test versions passed while the bug was live because they measured quantities that
 *    cannot move: populated layer count (clipping removes material *within* layers) and archive size
 *    (byte-identical, because the engine places the object itself). Only ink touching a plate edge
 *    discriminates.
 */
object ResinPlacement {

    /**
     * Centre of the engine's bed in its own frame.
     *
     * **Half the panel.** Measured, not read: slicing a marker with features at world +40 mm (X) and
     * +30 mm (Y) produced ink only in the region world x∈[0, 42.7] and y∈[0, 36.2] — the positive
     * quadrant, everything negative clipped. So the raster covers world (0,0)→(panelW, panelH): a
     * corner-origin mapping, and a model centred on the origin hangs off it. Placing at half the panel
     * puts the model's centre at the middle of that raster.
     *
     * (PrusaSlicer's SLA *profiles* carry a bed_shape centred on (0,0) — which is why the original
     * code's comment claimed the bed was centred on the origin — but the raster mapping we actually
     * get back is corner-origin. The measurement wins over the convention.)
     */
    fun bedCentre(printer: ResinPrinter): Pair<Float, Float> =
        (printer.buildWidthMm / 2.0).toFloat() to (printer.buildDepthMm / 2.0).toFloat()

    /** Footprint margins (left, right, front, back) in mm for a [mesh] on [printer]'s bed. */
    fun margins(mesh: Mesh, printer: ResinPrinter): FloatArray {
        val b = mesh.bounds()
        return floatArrayOf(
            b.min.x,
            (printer.buildWidthMm - b.max.x).toFloat(),
            b.min.y,
            (printer.buildDepthMm - b.max.y).toFloat(),
        )
    }

    /**
     * True when [mesh]'s footprint sits inside the bed with its base on the plate — i.e. the slice
     * will not silently lose material off the edge.
     */
    fun isOnBed(mesh: Mesh, printer: ResinPrinter, epsMm: Float = 0.01f): Boolean {
        if (mesh.isEmpty) return false
        val b = mesh.bounds()
        return b.min.x >= -epsMm && b.min.y >= -epsMm &&
            b.max.x <= printer.buildWidthMm + epsMm && b.max.y <= printer.buildDepthMm + epsMm &&
            kotlin.math.abs(b.min.z) <= epsMm
    }
}
