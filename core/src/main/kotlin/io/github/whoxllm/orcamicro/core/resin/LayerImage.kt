package io.github.whoxllm.orcamicro.core.resin

/**
 * One rasterized layer: 8-bit grayscale, row-major, [width]×[height].
 * 0 = masked (no light), 255 = fully exposed. Intermediate values are partial exposure (anti-aliasing).
 */
class LayerImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(pixels.size == width * height) { "pixels must be width*height bytes" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    fun set(x: Int, y: Int, value: Int) = run { pixels[y * width + x] = value.coerceIn(0, 255).toByte() }

    /** Fraction of the layer that is exposed (0..1) — used for resin-volume estimates. */
    fun coverage(): Double {
        var sum = 0L
        for (p in pixels) sum += (p.toInt() and 0xFF)
        return sum.toDouble() / (255.0 * width * height)
    }
}
