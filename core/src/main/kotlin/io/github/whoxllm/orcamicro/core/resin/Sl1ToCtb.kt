package io.github.whoxllm.orcamicro.core.resin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Converts a PrusaSlicer **SL1** (ZIP of 8-bit grayscale PNGs) into a **CTB** for Chitubox-family
 * printers. Uses [PngDecoder] + [CtbWriter]; no native code, pure core.
 *
 * The engine emits the SL1; this re-containers it so a printer that wants CTB gets CTB.
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
        val layers = readLayers(sl1)                 // bottom → top
        require(layers.isNotEmpty()) { "SL1 contained no layer images" }
        val w = layers.first().width
        val h = layers.first().height

        // engine grayscale is 0..255; CTB is 0..127
        val masks = layers.map { img -> ByteArray(img.gray.size) { ((img.gray[it].toInt() and 0xFF) shr 1).toByte() } }

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
            resolutionX = w,
            resolutionY = h,
            printTimeS = printTimeSec,
            bottomLiftDistMm = settings.liftDistanceMm.toFloat(),
            liftDistMm = settings.liftDistanceMm.toFloat(),
            bottomLiftSpeedMmpm = settings.bottomLiftSpeedMmPerMin.toFloat(),
            liftSpeedMmpm = settings.liftSpeedMmPerMin.toFloat(),
            retractSpeedMmpm = settings.retractSpeedMmPerMin.toFloat(),
            machineType = printer.name.take(24),
        )
        CtbWriter.write(out, w, h, masks, params)
    }

    /** Reads `<n>.png` members of the SL1 ZIP in ascending numeric order. */
    private fun readLayers(sl1: ByteArray): List<PngDecoder.Image> {
        val byName = sortedMapOf<Int, PngDecoder.Image>()
        ZipInputStream(ByteArrayInputStream(sl1)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name
                if (name.endsWith(".png", ignoreCase = true)) {
                    val idx = name.substringBeforeLast('.').filter { it.isDigit() }.toIntOrNull()
                    val bytes = ByteArrayOutputStream().also { it -> zip.copyTo(it) }.toByteArray()
                    if (idx != null) byName[idx] = PngDecoder.decode(bytes)
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        return byName.values.toList()
    }
}
