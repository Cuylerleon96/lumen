package io.github.whoxllm.orcamicro.core.resin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Converts a PrusaSlicer **SL1** (ZIP of 8-bit grayscale PNGs) into a **CTB** for Chitubox-family
 * printers. Uses [PngDecoder] + [CtbWriter]; no native code, pure core.
 *
 * Layers are decoded and RLE7-encoded **one at a time** — only the small compressed blobs are held,
 * so a 500+ layer print never loads ~2 GB of full-resolution masks into memory (an OOM trap on-device).
 */
object Sl1ToCtb {

    /**
     * @param sl1 the SL1 ZIP bytes (from the engine)
     * @param settings print settings (exposure etc.)
     * @param printer target printer (resolution, build area, name)
     * @param overallHeightMm model height
     * @param out destination
     */
    fun convert(
        sl1: ByteArray,
        settings: ResinSettings,
        printer: ResinPrinter,
        overallHeightMm: Double,
        printTimeSec: Int,
        out: OutputStream,
    ) {
        val named = ArrayList<Pair<String, ByteArray>>()
        var width = 0
        var height = 0
        ZipInputStream(ByteArrayInputStream(sl1)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".png", ignoreCase = true)) {
                    val baos = ByteArrayOutputStream()
                    zip.copyTo(baos)
                    val img = PngDecoder.decode(baos.toByteArray())
                    if (width == 0) { width = img.width; height = img.height }
                    // engine grayscale 0..255 → CTB 0..127
                    val mask = ByteArray(img.gray.size) { ((img.gray[it].toInt() and 0xFF) shr 1).toByte() }
                    named += e.name to CtbWriter.encodeRle7(mask)   // small blob; mask is freed next iteration
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        require(named.isNotEmpty()) { "SL1 contained no layer images" }
        // PrusaSlicer names layers <jobid><zero-padded index>.png — lexicographic order = layer order.
        val encoded = named.sortedBy { it.first }.map { it.second }

        val params = CtbWriter.Params(
            printerOutMmX = printer.buildWidthMm.toFloat(),
            printerOutMmY = printer.buildDepthMm.toFloat(),
            printerOutMmZ = printer.buildHeightMm.toFloat(),
            overallHeightMm = overallHeightMm.toFloat(),
            layerHeightMm = settings.layerHeightMm.toFloat(),
            exposureS = settings.exposureSec.toFloat(),
            bottomExposureS = settings.bottomExposureSec.toFloat(),
            lightOffS = settings.lightOffSec.toFloat(),
            bottomLightOffS = settings.bottomLightOffSec.toFloat(),
            bottomLayerCount = settings.bottomLayers,
            resolutionX = width,
            resolutionY = height,
            printTimeS = printTimeSec,
            bottomLiftDistMm = settings.liftDistanceMm.toFloat(),
            liftDistMm = settings.liftDistanceMm.toFloat(),
            bottomLiftSpeedMmpm = settings.bottomLiftSpeedMmPerMin.toFloat(),
            liftSpeedMmpm = settings.liftSpeedMmPerMin.toFloat(),
            retractSpeedMmpm = settings.retractSpeedMmPerMin.toFloat(),
            machineType = printer.name.take(24),
        )
        CtbWriter.writeEncoded(out, width, height, encoded, params)
    }
}
