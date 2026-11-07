package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh

/**
 * Where a resin model must sit on the bed.
 *
 * The SLA engine's bed spans `0..width × 0..depth` in mm with its origin at a **corner**, not the
 * centre — which is what the FDM bed convention leads you to assume, and what the old slice path
 * assumed (`centerX = 0f, centerY = 0f` with a comment claiming the bed was centred on the origin).
 * A mesh centred on the origin therefore lands in the corner with its negative half off the plate,
 * and the layers above the clipped part come out **empty** — which reads like a support or engine
 * failure rather than a placement one.
 *
 * Verified by slicing one mesh twice with only the translation changed: centred on the origin the SL1
 * held 700 KB of layers, the body was clipped and layers 146-179 were empty; centred on the bed it
 * held 3.0 MB with every layer populated.
 *
 * The naive [MeshRasterizer] fallback never had this bug because it centres each raster on the mesh's
 * own bounds — so the placement only has to be right for the engine, and the two paths agree.
 */
object ResinPlacement {

    /** Centre of [printer]'s bed in the engine's frame, as (x, y) in mm. */
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
