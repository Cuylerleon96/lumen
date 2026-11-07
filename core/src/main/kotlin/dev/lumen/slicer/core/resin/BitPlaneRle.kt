package dev.lumen.slicer.core.resin

/**
 * **Bit-plane RLE** — the layer codec the Chitubox-family formats use (CBDDLP `.cbddlp`, CTB v1/v2,
 * and the AA sub-layers of later ones). Clean-room from the open UVTools specification
 * (`BitPlaneRle.cs`, GPL — reference only, no code copied).
 *
 * This is **not** the RLE7 codec in [CtbWriter]. CTB v3+ stores each pixel's 7-bit value in one byte
 * (bit 7 = run flag); this family stores each layer as **one binary plane per bit of the value**, and
 * each plane is run-length encoded. A decoder counts, per pixel, how many planes had it set, and maps
 * that count back to a grayscale level ([countsToGray]). Two different codecs that happen to live in
 * the same family of files — reaching for the wrong one produces a file that looks structurally fine
 * and prints a wrong image.
 *
 * Plane byte layout — deliberately unlike [PhotonSWriter]:
 * ```
 *   bit 7      : 1 = this run is "on" (exposed), 0 = "off"
 *   bits 6..0  : the run length, stored DIRECTLY (1..125)
 * ```
 * Runs longer than [RLE8_LIMIT] are split into several bytes. PhotonS puts `reverse8(len - 1)` in the
 * same bits; here it is the plain count, so a 125-run is `0xFD` with the flag and `0x7D` without.
 */
object BitPlaneRle {

    /**
     * Maximum run a single byte can carry: 0x7D = 125. The low 7 bits hold the count, and 125 is the
     * vendor's ceiling (not 127) — exceeding it silently truncates the run on a real printer.
     */
    const val RLE8_LIMIT = 125

    /** A pixel at or above this counts as "on" in a binary plane. */
    const val ON_THRESHOLD = 128

    /** The high bit: this run is exposed. */
    private const val ON_FLAG = 0x80

    /**
     * Encode one binary plane ([pixels] 0 or 255) into RLE bytes.
     *
     * @param maxRunLength runs are split at this length; must be ≤ [RLE8_LIMIT] to stay decodable.
     */
    fun encodePlane(
        pixels: ByteArray,
        threshold: Int = ON_THRESHOLD,
        maxRunLength: Int = RLE8_LIMIT,
    ): ByteArray {
        require(maxRunLength in 1..RLE8_LIMIT) { "maxRunLength must be 1..$RLE8_LIMIT" }
        val out = java.io.ByteArrayOutputStream(pixels.size / 8 + 16)
        var i = 0
        while (i < pixels.size) {
            val on = (pixels[i].toInt() and 0xFF) >= threshold
            var run = 1
            while (i + run < pixels.size && ((pixels[i + run].toInt() and 0xFF) >= threshold) == on) run++
            i += run
            var remaining = run
            while (remaining > 0) {
                val chunk = if (remaining > maxRunLength) maxRunLength else remaining
                out.write(chunk or (if (on) ON_FLAG else 0))
                remaining -= chunk
            }
        }
        return out.toByteArray()
    }

    /**
     * Decode a plane into per-pixel **counts** (0..8 across all bit planes): a run whose flag is set
     * increments the count of every pixel it covers. Returns a [pixelCount]-long array; a plane that
     * stops early leaves the remaining pixels at 0 (trailing black is not stored).
     */
    fun decodePlane(rle: ByteArray, pixelCount: Int): ByteArray {
        val counts = ByteArray(pixelCount)
        var pixel = 0
        for (b in rle) {
            val v = b.toInt() and 0xFF
            val reps = v and 0x7F
            require(reps <= pixelCount - pixel) { "plane overran the image (at pixel $pixel)" }
            if ((v and ON_FLAG) != 0) {
                for (k in 0 until reps) counts[pixel + k]++
            }
            pixel += reps
            if (pixel == pixelCount) break
        }
        return counts
    }

    /**
     * Map bit counts to grayscale levels. `step = 256 / antiAliasing`, `level = count * step - 1`
     * (clamped at 0) — so with AA off any count ≥ 1 is fully exposed (255), and with 8 AA levels a
     * count of 1..8 spreads across the range.
     */
    fun countsToGray(counts: ByteArray, antiAliasing: Int = 1): ByteArray {
        require(antiAliasing >= 1) { "antiAliasing must be ≥ 1" }
        val step = 256 / antiAliasing
        val lut = IntArray(256) { c -> val g = c * step; if (g > 0) (g - 1) and 0xFF else 0 }
        return ByteArray(counts.size) { lut[counts[it].toInt() and 0xFF].toByte() }
    }

    /**
     * Encode a whole layer mask as one plane per set bit of its values, **bit 0 first**.
     *
     * With anti-aliasing off the mask is binary, so this is a single plane (bit 0) and the decoder's
     * count is 0 or 1. With AA the caller writes one plane per level as its own layer record — the
     * layer table carries `layerCount * antiAliasLevel` entries.
     */
    fun encodeLayer(mask: ByteArray, bits: Int = 1): List<ByteArray> {
        require(bits in 1..8) { "bits must be 1..8" }
        return (0 until bits).map { bit ->
            val plane = ByteArray(mask.size) { i ->
                if (((mask[i].toInt() and 0xFF) shr bit) and 1 == 1) 255.toByte() else 0
            }
            encodePlane(plane)
        }
    }
}
