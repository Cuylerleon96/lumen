package dev.lumen.slicer.core.resin

/**
 * The SLA engine's layer raster is **transposed** relative to the panel, and the mapping is a
 * **measured** property — not a convention to reason about. Measured by slicing three size-tagged
 * cubes at world (0,0), (+30,0) and (0,+20) mm through the standalone engine CLI and reading where
 * the ink landed in the layer PNG:
 *
 * - the PNG's **width is the panel's Y pixel count** (a 2560x1440 panel yields a 1440x2560 PNG);
 * - **world Y runs across the image directly** (`image-x == world Y`);
 * - **world X runs down the image reversed** (`image-y == panelW - world X`), so world X = 0 is the
 *   image's BOTTOM edge and X = panelW is its TOP edge.
 *
 * The markers measured exactly their nominal 6.00 and 12.00 mm, which is what makes the reading
 * self-checking: a wrong mm-per-pixel would have shown up as a wrong marker size, and the negative
 * half of each marker was missing because the raster is corner-origin.
 *
 * To land in the panel's own frame — width = X px, height = Y px, world (0,0) at the top-left, X to
 * the right, Y down — the raster must be rotated **90 degrees CLOCKWISE**. Verified on real pixels:
 * the source's bottom-left ink (world X = 0, Y = 0) becomes the result's row 0 / column 0.
 *
 * What a measurement cannot settle: which physical edge the **printer** calls Y = 0. A 180 degree
 * error there mirrors the print front-to-back, and only a physical print closes it — so this class
 * deliberately does not guess at it.
 */
object RasterOrient {

    /** A grayscale mask together with its own dimensions (in the panel's orientation after [forPanel]). */
    class Mask(val gray: ByteArray, val width: Int, val height: Int)

    /** The engine's transposed raster for a [resolutionX]-wide panel: w == Y count, h == X count. */
    fun isEngineTransposed(width: Int, height: Int, resolutionX: Int, resolutionY: Int): Boolean =
        width == resolutionY && height == resolutionX

    /** Already in the panel's own orientation. */
    fun isPanelOriented(width: Int, height: Int, resolutionX: Int, resolutionY: Int): Boolean =
        width == resolutionX && height == resolutionY

    /**
     * Rotate a raster 90 degrees clockwise: a [width] x [height] source becomes [height] x [width],
     * and source (row v, col u) moves to (row u, col height-1-v). The direction [forPanel] needs,
     * pinned by a hand-computed test.
     */
    fun rotateClockwise(gray: ByteArray, width: Int, height: Int): Mask {
        require(gray.size >= width * height) { "mask is ${gray.size} bytes, smaller than ${width}x$height" }
        val out = ByteArray(width * height)
        for (v in 0 until height) {
            for (u in 0 until width) {
                out[u * height + (height - 1 - v)] = gray[v * width + u]
            }
        }
        return Mask(out, height, width)
    }

    /**
     * Put an engine raster into [printer]'s panel orientation.
     *
     * A raster that matches NEITHER the panel nor the engine's transpose of it means the engine was
     * told the wrong panel — the signature of dropped `display_*` keys — and it is refused rather than
     * scaled: silently rescaling hides the real fault behind a print that comes out subtly the wrong
     * size.
     */
    fun forPanel(gray: ByteArray, width: Int, height: Int, printer: ResinPrinter): Mask {
        if (isPanelOriented(width, height, printer.resolutionX, printer.resolutionY)) {
            return Mask(gray, width, height)
        }
        if (isEngineTransposed(width, height, printer.resolutionX, printer.resolutionY)) {
            return rotateClockwise(gray, width, height)
        }
        throw IllegalArgumentException(
            "SL1 raster ${width}x$height does not match ${printer.name} " +
                "(${printer.resolutionX}x${printer.resolutionY}) nor its transpose " +
                "(${printer.resolutionY}x${printer.resolutionX}); the engine was sliced for another panel"
        )
    }
}
