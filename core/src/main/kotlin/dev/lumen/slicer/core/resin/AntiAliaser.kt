package dev.lumen.slicer.core.resin

/**
 * Anti-aliasing (D.5): softens layer edges by lowering the exposure of boundary voxels to an
 * intermediate gray level, so the LCD edge is partially cured and the print reads smoother.
 *
 * This is a cheap **edge-softening** pass over the engine's binary masks. Full 3D supersampling
 * (SSAA) would need higher-resolution engine output; this works from what the engine already gives.
 * Masks are `width*height` bytes with values 0..127 (0 = off, 127 = full).
 */
object AntiAliaser {

    /**
     * @param levels AA levels (1 = off). A boundary pixel is set to `127*(levels-1)/levels`.
     */
    fun apply(mask: ByteArray, width: Int, height: Int, levels: Int): ByteArray {
        if (levels <= 1) return mask
        val edge = (127 * (levels - 1) / levels).toByte()
        val out = mask.copyOf()
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val i = row + x
                if (mask[i].toInt() == 0) continue
                val boundary =
                    (x > 0 && mask[i - 1].toInt() == 0) ||
                        (x < width - 1 && mask[i + 1].toInt() == 0) ||
                        (y > 0 && mask[i - width].toInt() == 0) ||
                        (y < height - 1 && mask[i + width].toInt() == 0)
                if (boundary) out[i] = edge
            }
        }
        return out
    }
}
