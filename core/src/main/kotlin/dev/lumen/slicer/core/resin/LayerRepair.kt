package dev.lumen.slicer.core.resin

import java.util.ArrayDeque

/**
 * Layer repair (K) — the first half of UVTools-class cleanup, operating on the engine's per-layer
 * masks (values 0..127, 0 = off). Detects and fixes the two most common resin defects without a full
 * voxel pipeline:
 *  - **specks**: connected components smaller than a threshold (floating debris / dust pixels)
 *  - **pinholes**: single empty pixels enclosed by cured resin
 */
object LayerRepair {

    /**
     * Remove connected components (4-connected) with fewer than [minPixels] pixels. Returns a new mask.
     */
    fun removeSpecks(mask: ByteArray, width: Int, height: Int, minPixels: Int): ByteArray {
        if (minPixels <= 1) return mask
        val out = mask.copyOf()
        val visited = BooleanArray(mask.size)
        val queue = ArrayDeque<Int>()
        val component = ArrayList<Int>()
        for (start in mask.indices) {
            if (visited[start] || mask[start].toInt() == 0) continue
            component.clear(); queue.clear()
            queue.add(start); visited[start] = true
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst(); component.add(i)
                val x = i % width; val y = i / width
                if (x > 0) visit(i - 1, mask, visited, queue)
                if (x < width - 1) visit(i + 1, mask, visited, queue)
                if (y > 0) visit(i - width, mask, visited, queue)
                if (y < height - 1) visit(i + width, mask, visited, queue)
            }
            if (component.size < minPixels) for (i in component) out[i] = 0
        }
        return out
    }

    private fun visit(i: Int, mask: ByteArray, visited: BooleanArray, queue: ArrayDeque<Int>) {
        if (!visited[i] && mask[i].toInt() != 0) { visited[i] = true; queue.add(i) }
    }

    /**
     * Fill empty pixels whose four neighbours are all cured (pinholes). One pass; returns a new mask.
     */
    fun fillHoles(mask: ByteArray, width: Int, height: Int, fill: Byte = 127): ByteArray {
        val out = mask.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                if (mask[i].toInt() != 0) continue
                if (mask[i - 1].toInt() != 0 && mask[i + 1].toInt() != 0 &&
                    mask[i - width].toInt() != 0 && mask[i + width].toInt() != 0
                ) {
                    out[i] = fill
                }
            }
        }
        return out
    }
}
