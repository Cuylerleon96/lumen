package io.github.whoxllm.orcamicro.core.resin

/**
 * Island detection (K, UVTools-class): an **island** is a connected region of exposed pixels in a
 * layer with **no exposed pixel directly below it** in the previous layer — it would print into thin
 * air and tear off. Runs on the sliced layer masks before writing the container.
 *
 * Masks are `width*height` bytes, non-zero = exposed. Layer 0 is never an island (it sits on the
 * plate). 4-connectivity.
 */
object IslandDetector {

    data class Island(
        val layer: Int,
        val pixelCount: Int,
        val minX: Int, val minY: Int, val maxX: Int, val maxY: Int,
    )

    /** @param minPixels ignore regions smaller than this (tiny specks are usually false positives). */
    fun detect(layers: List<ByteArray>, width: Int, height: Int, minPixels: Int = 1): List<Island> {
        require(layers.all { it.size == width * height }) { "layer size != ${width}x$height" }
        val islands = ArrayList<Island>()
        var prev: ByteArray? = null
        val visited = BooleanArray(width * height)
        for ((idx, layer) in layers.withIndex()) {
            if (idx == 0) { prev = layer; continue }   // bottom layer rests on the plate
            java.util.Arrays.fill(visited, false)
            for (start in layer.indices) {
                if (layer[start].toInt() == 0 || visited[start]) continue
                val stack = ArrayDeque<Int>()
                stack.addLast(start)
                var count = 0
                var supported = false
                var minX = width; var minY = height; var maxX = -1; var maxY = -1
                while (stack.isNotEmpty()) {
                    val p = stack.removeLast()
                    if (visited[p]) continue
                    visited[p] = true
                    if (layer[p].toInt() == 0) continue
                    count++
                    val x = p % width; val y = p / width
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                    if (prev!![p].toInt() != 0) supported = true
                    if (x > 0) stack.addLast(p - 1)
                    if (x < width - 1) stack.addLast(p + 1)
                    if (y > 0) stack.addLast(p - width)
                    if (y < height - 1) stack.addLast(p + width)
                }
                if (!supported && count >= minPixels) islands += Island(idx, count, minX, minY, maxX, maxY)
            }
            prev = layer
        }
        return islands
    }
}
