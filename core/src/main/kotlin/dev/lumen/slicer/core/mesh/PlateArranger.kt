package dev.lumen.slicer.core.mesh

import kotlin.math.max

/**
 * Auto-arrange: fit N models on the plate without overlap.
 *
 * **Deterministic on purpose.** The same input must always produce the same layout, so pressing
 * arrange twice does not reshuffle the plate, and so tests can assert exact positions. Packing is
 * shelf-based: models are sorted largest-footprint-first, laid along the plate's long axis, and
 * wrapped to a new row when the current one is full. A model that will not fit in its current
 * orientation is retried rotated 90° before wrapping.
 *
 * Shelf packing is not optimal — nested packing fits more — but it is predictable, fast, and adequate
 * on a phone. The contract it *does* guarantee: **nothing overlaps, nothing lands off the plate, and
 * anything that does not fit is reported** rather than silently dropped.
 *
 * The frame is the bed's own, `(0,0)..(bedWidth, bedDepth)`, which is what `Placement.centerX/centerY`
 * speak. For the SLA engine that raster is **corner-origin**, so a model centred here at
 * `bedWidth/2, bedDepth/2` sits in the middle of the plate — see [dev.lumen.slicer.core.resin.ResinPlacement]
 * for why that distinction is worth a comment this long.
 */
object PlateArranger {

    /** A model's footprint on the plate, in mm. [id] is the caller's handle for the model. */
    data class Item(val id: Int, val widthMm: Double, val depthMm: Double) {
        val areaMm2: Double get() = widthMm * depthMm
    }

    /** Where a model goes: centre in bed coordinates, and whether it was turned 90°. */
    data class Spot(val id: Int, val centerX: Float, val centerY: Float, val rotated: Boolean)

    /**
     * @param spots where each placed model goes, in the same order as the input items
     * @param unplaced ids that did not fit — the caller warns rather than silently losing them
     * @param rows how many shelf rows were used (useful for a "plate is getting full" hint)
     */
    data class Result(val spots: List<Spot>, val unplaced: List<Int>, val rows: Int) {
        val placedCount: Int get() = spots.size
        val allPlaced: Boolean get() = unplaced.isEmpty()
    }

    /**
     * Lay [items] out on a [bedWidthMm] x [bedDepthMm] plate.
     *
     * @param spacingMm gap to keep between neighbouring models (and between a model and the margin)
     * @param marginMm keep-out border around the plate edge
     * @param allowRotation try 90° rotations — usually the difference between fitting one more model
     *   and not, since most prints are not square
     */
    fun arrange(
        items: List<Item>,
        bedWidthMm: Double,
        bedDepthMm: Double,
        spacingMm: Double = 3.0,
        marginMm: Double = 2.0,
        allowRotation: Boolean = true,
    ): Result {
        if (items.isEmpty()) return Result(emptyList(), emptyList(), 0)

        val usableW = bedWidthMm - 2 * marginMm
        val usableD = bedDepthMm - 2 * marginMm
        if (usableW <= 0 || usableD <= 0) return Result(emptyList(), items.map { it.id }, 0)

        // Largest first: big models are the ones that fail to fit, and placing them while the plate is
        // empty is what keeps the small ones from boxing them out.
        val ordered = items.sortedWith(
            compareByDescending<Item> { it.areaMm2 }.thenByDescending { max(it.widthMm, it.depthMm) }.thenBy { it.id },
        )

        val spots = ArrayList<Spot>(items.size)
        val unplaced = ArrayList<Int>()
        var cursorX = 0.0          // consumed width in the current row
        var cursorY = 0.0          // top of the current row
        var rowDepth = 0.0         // depth of the tallest model in the current row
        // Start at 1, not 0: we are already IN the first row. With 0 the first wrap added nothing to
        // cursorY, so "row 2" was laid straight on top of row 1 -- two models in the same place. The
        // spacing test caught it; that is what the test is for.
        var rows = 1

        for (item in ordered) {
            val orientations = ArrayList<Triple<Double, Double, Boolean>>(2)
            orientations += Triple(item.widthMm, item.depthMm, false)
            if (allowRotation && item.widthMm != item.depthMm) {
                orientations += Triple(item.depthMm, item.widthMm, true)
            }

            // Prefer an orientation that fits in the current row; else start a new row and retry.
            var chosen = orientations.firstOrNull { (w, d, _) ->
                cursorX + w <= usableW && max(rowDepth, d) <= usableD - cursorY
            }
            if (chosen == null) {
                cursorY += rowDepth + spacingMm
                cursorX = 0.0
                rowDepth = 0.0
                rows++
                chosen = orientations.firstOrNull { (w, d, _) ->
                    w <= usableW && d <= usableD - cursorY
                }
            }
            if (chosen == null) {
                unplaced += item.id
                continue
            }

            val (w, d, rotated) = chosen
            val left = marginMm + cursorX
            val top = marginMm + cursorY
            spots += Spot(
                id = item.id,
                centerX = (left + w / 2.0).toFloat(),
                centerY = (top + d / 2.0).toFloat(),
                rotated = rotated,
            )
            cursorX += w + spacingMm
            rowDepth = max(rowDepth, d)
        }

        // Report in the caller's input order so a UI can map spots to models without sorting.
        val byId = spots.associateBy { it.id }
        return Result(items.mapNotNull { byId[it.id] }, unplaced, rows)
    }
}
