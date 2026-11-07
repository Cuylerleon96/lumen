package dev.lumen.slicer.core.resin

/**
 * Builds the RGB565 previews embedded in resin containers (CTB/GOO). The engine's layer mask is a
 * single-channel image; we downscale it into a thumbnail and pack 5-6-5 RGB, tinting cured pixels
 * amber and empty pixels near-black so the printer's screen shows the actual layer silhouette.
 */
object PreviewRenderer {

    /**
     * Downscale [mask] (width×height, 0..127) to [outW]×[outH] and return RGB565 bytes.
     * @param bigEndian true for GOO (big-endian), false for CTB (little-endian).
     */
    fun rgb565(mask: ByteArray, width: Int, height: Int, outW: Int, outH: Int, bigEndian: Boolean): ByteArray {
        val out = ByteArray(outW * outH * 2)
        var o = 0
        for (y in 0 until outH) {
            val sy = (y.toLong() * height / outH).toInt().coerceIn(0, height - 1)
            for (x in 0 until outW) {
                val sx = (x.toLong() * width / outW).toInt().coerceIn(0, width - 1)
                val v = (mask[sy * width + sx].toInt() and 0xFF) * 2   // 0..127 → 0..254
                val r = (20 + v * 235 / 255)      // dark→bright amber
                val g = (16 + v * 150 / 255)
                val b = (18 + v * 40 / 255)
                val c = ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
                if (bigEndian) { out[o] = (c shr 8).toByte(); out[o + 1] = c.toByte() }
                else { out[o] = c.toByte(); out[o + 1] = (c shr 8).toByte() }
                o += 2
            }
        }
        return out
    }
}
