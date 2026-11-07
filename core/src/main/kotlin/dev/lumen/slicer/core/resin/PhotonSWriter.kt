package dev.lumen.slicer.core.resin

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Anycubic Photon S writer (`.photon`).
 *
 * Clean-room from the open UVTools specification (`UVtools.Core/FileFormats/AnycubicPhotonSFile.cs`,
 * GPL — used as a **reference only**, no code copied). The whole file is **big-endian**, which is the
 * opposite of CTB: a writer that gets this wrong produces a file no printer will read.
 *
 * Layout:
 * ```
 * Header          (u32 2 · u16 49 · f64 pixelSize · f64 layerHeight · f64 exposure · f64 lightOff
 *                  · f64 bottomExposure · u32 bottomLayers · f64 liftHeight · f64 liftSpeed
 *                  · f64 retractSpeed · f64 volumeMl · u32 previewW=224 · u32 42
 *                  · u32 previewH=168 · u32 10)
 * Preview         224 x 168 RGB565 (2 bytes/px, 75 264 bytes)
 * u32             layer count
 * per layer       LayerDef (u32 44944 · u32 0 · u32 0 · u32 resX · u32 resY · u32 dataSize
 *                  · u32 2684702720) followed by that layer's RLE bytes
 * ```
 * `dataSize` is not a byte count: the spec defines `rleSize = (dataSize >> 3) - 4`, so we write
 * `dataSize = (rleSize + 4) << 3`.
 *
 * The RLE is **one byte per run**, and the run length is **bit-reversed** inside it:
 * bit 0 is the colour (1 = exposed), bits 1..7 hold `reverse7(length - 1)`. Runs are split into
 * chunks of at most [RLE_LIMIT] pixels. Decoding is
 * `length = reverse7((b and 0xFE) shr 1) + 1`, `colour = (b and 1) * 255`.
 *
 * Layers are streamed one at a time: a full-res print held as raw masks is GBs and would OOM a phone.
 */
object PhotonSWriter {

    /** Runs longer than this are split into several bytes. */
    const val RLE_LIMIT = 128

    const val PREVIEW_W = 224
    const val PREVIEW_H = 168

    /** Pixels at or above this are "on" (exposed). */
    const val ON_THRESHOLD = 128

    private const val TAG1 = 2
    private const val TAG2 = 49
    private const val PREVIEW_TAG = 42
    private const val PREVIEW_TAG2 = 10
    private const val LAYER_TAG1 = 44944
    private const val LAYER_TAG2 = 2684702720L

    /** u32 tag + u16 tag + 9 x f64 + 6 x u32 = 98 bytes. Allocate exactly, and assert on overflow. */
    const val HEADER_BYTES = 98

    /**
     * Write a Photon S file.
     *
     * @param volumeMl resin volume for the header's `volumeMl` field (0 = unknown; printers show it
     *   as a stat, they do not print from it).
     * @param layerAt lazy mask provider — the caller decodes/encodes one layer at a time.
     */
    fun write(
        out: OutputStream,
        printer: ResinPrinter,
        settings: ResinSettings,
        layerCount: Int,
        volumeMl: Double = 0.0,
        layerAt: (Int) -> LayerImage,
    ) {
        val buf = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)

        // ---- header ----
        buf.putInt(TAG1)
        buf.putShort(TAG2.toShort())
        buf.putDouble(round3(printer.pixelSizeMicrons / 1000.0)) // XY pixel size, mm
        buf.putDouble(settings.layerHeightMm)
        buf.putDouble(settings.exposureSec)
        buf.putDouble(settings.lightOffSec)
        buf.putDouble(settings.bottomExposureSec)
        buf.putInt(settings.bottomLayers)
        buf.putDouble(settings.liftDistanceMm)
        buf.putDouble(settings.liftSpeedMmPerMin / 60.0) // the format stores mm/s
        buf.putDouble(settings.retractSpeedMmPerMin / 60.0)
        buf.putDouble(volumeMl)
        buf.putInt(PREVIEW_W)
        buf.putInt(PREVIEW_TAG)
        buf.putInt(PREVIEW_H)
        buf.putInt(PREVIEW_TAG2)
        out.write(buf.array(), 0, buf.position())

        // ---- preview (middle layer is the most representative) ----
        val middle = layerAt(layerCount / 2)
        out.write(PreviewRenderer.rgb565(middle.pixels, middle.width, middle.height, PREVIEW_W, PREVIEW_H, bigEndian = false))

        // ---- layer count + layers ----
        out.write(u32(layerCount))
        for (i in 0 until layerCount) {
            val rle = encodeRle(layerAt(i))
            val def = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)
            def.putInt(LAYER_TAG1)
            def.putInt(0)
            def.putInt(0)
            def.putInt(printer.resolutionX)
            def.putInt(printer.resolutionY)
            def.putInt((rle.size + 4) shl 3) // dataSize = (rleSize + 4) * 8
            def.putInt(LAYER_TAG2.toInt())
            out.write(def.array())
            out.write(rle)
        }
    }

    /** Encode one layer's mask as PhotonS RLE (see the class note for the byte format). */
    fun encodeRle(image: LayerImage): ByteArray {
        val px = image.pixels
        val out = java.io.ByteArrayOutputStream(px.size / 8 + 64)
        var i = 0
        while (i < px.size) {
            val on = (px[i].toInt() and 0xFF) >= ON_THRESHOLD
            var run = 1
            while (i + run < px.size && ((px[i + run].toInt() and 0xFF) >= ON_THRESHOLD) == on) run++
            i += run
            var remaining = run
            while (remaining > 0) {
                val chunk = if (remaining > RLE_LIMIT) RLE_LIMIT else remaining
                out.write(reverseBits(chunk - 1) or (if (on) 1 else 0))
                remaining -= chunk
            }
        }
        return out.toByteArray()
    }

    /**
     * Reverse **all 8 bits** of [v] — not the 7 that hold the length. `length - 1` is at most 127, so
     * its bit 7 is always 0, and the 8-bit reverse therefore always lands a 0 in bit 0: that is
     * exactly the bit the colour flag is OR'd into. Reversing only 7 bits (a plausible-looking
     * "optimisation") produces a file whose every run decodes to half its true length.
     */
    fun reverseBits(v: Int): Int {
        var x = v and 0xFF
        var r = 0
        for (k in 0 until 8) {
            r = (r shl 1) or (x and 1)
            x = x shr 1
        }
        return r
    }

    private fun u32(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0
}
