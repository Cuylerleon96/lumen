package dev.lumen.slicer.core.resin

/**
 * Resin-trap detection (K, UVTools-class): a **resin trap** is an enclosed **empty** region that the
 * cured shell walls off, so liquid resin is trapped inside and can split the print. Found by
 * flood-filling the empty pixels inward from the border — any empty pixel **not** reached is enclosed.
 *
 * A single-layer enclosed void is usually harmless (it fills/ventilates); report only voids that
 * persist across at least [minDepth] consecutive layers to avoid false positives.
 */
object ResinTrapDetector {

    data class Trap(val startLayer: Int, val endLayer: Int, val pixelCount: Int)

    /** @param minPixels minimum enclosed area (px) to count; @param minDepth minimum layer depth. */
    fun detect(
        layers: List<ByteArray>,
        width: Int,
        height: Int,
        minPixels: Int = 1,
        minDepth: Int = 3,
    ): List<Trap> {
        require(layers.all { it.size == width * height }) { "layer size != ${width}x$height" }
        val traps = ArrayList<Trap>()
        var runStart = -1
        var runPixels = 0
        for ((idx, layer) in layers.withIndex()) {
            val enclosed = enclosedPixels(layer, width, height)
            if (enclosed >= minPixels) {
                if (runStart < 0) { runStart = idx; runPixels = 0 }
                runPixels = maxOf(runPixels, enclosed)
            } else if (runStart >= 0) {
                if (idx - runStart >= minDepth) traps += Trap(runStart, idx - 1, runPixels)
                runStart = -1
            }
        }
        if (runStart >= 0 && layers.size - runStart >= minDepth) traps += Trap(runStart, layers.size - 1, runPixels)
        return traps
    }

    /** Count empty pixels unreachable from the border (4-connectivity) — i.e. walled-off voids. */
    private fun enclosedPixels(layer: ByteArray, width: Int, height: Int): Int {
        val reachable = BooleanArray(width * height)
        val stack = ArrayDeque<Int>()
        // seed from every empty border pixel
        for (x in 0 until width) {
            if (layer[x].toInt() == 0) stack.addLast(x)
            val b = (height - 1) * width + x
            if (layer[b].toInt() == 0) stack.addLast(b)
        }
        for (y in 0 until height) {
            if (layer[y * width].toInt() == 0) stack.addLast(y * width)
            val r = y * width + (width - 1)
            if (layer[r].toInt() == 0) stack.addLast(r)
        }
        var empty = 0
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            if (reachable[p]) continue
            reachable[p] = true
            val x = p % width; val y = p / width
            if (x > 0 && layer[p - 1].toInt() == 0 && !reachable[p - 1]) stack.addLast(p - 1)
            if (x < width - 1 && layer[p + 1].toInt() == 0 && !reachable[p + 1]) stack.addLast(p + 1)
            if (y > 0 && layer[p - width].toInt() == 0 && !reachable[p - width]) stack.addLast(p - width)
            if (y < height - 1 && layer[p + width].toInt() == 0 && !reachable[p + width]) stack.addLast(p + width)
        }
        for (i in layer.indices) if (layer[i].toInt() == 0 && !reachable[i]) empty++
        return empty
    }
}
