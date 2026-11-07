package dev.lumen.slicer.core.resin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Re-container an engine SL1 into the Anycubic Photon family (PW0/PWS).
 *
 * Same architecture as [Sl1ToCtb] and [Sl1ToGoo]: the engine emits SL1 and a converter rewrites the
 * container, so every printer format shares one slice path.
 *
 * Two things this format needs that the others do not:
 *  - **The extension selects the codec, not a header byte.** Only `.pws` uses the bit-plane RLE;
 *    `.pw0`/`.pwmx`/`.pwms`/`.pwmb` use the nibble codec. The extension is therefore an argument here
 *    and must match the filename the caller writes — a mismatch produces a file the printer misreads
 *    rather than rejects. The target machines: Mono X = `pwmx`, Mono X 6K = `pwmb`.
 *  - **The machine table carries the panel geometry**, so the printer passed in has to be the real one.
 */
object Sl1ToPw {

    /**
     * Version to write. **516 is the highest version both target machines accept** (the Mono X takes
     * 1/515/516, the 6K takes 515/516/517), and it is the first version whose machine table carries the
     * panel geometry the printer needs.
     */
    const val DEFAULT_VERSION: Int = AnycubicPwWriter.VERSION_516

    /** The codec the extension implies. `.pws` is the only bit-plane variant. */
    fun codecFor(extension: String): AnycubicPwWriter.Codec =
        if (extension.equals("pws", ignoreCase = true)) AnycubicPwWriter.Codec.PWS
        else AnycubicPwWriter.Codec.PW0

    /**
     * @param sl1 the SL1 ZIP bytes (from the engine)
     * @param settings print settings (exposure, motion, repair, AA)
     * @param printer target printer — its panel geometry goes into the machine table
     * @param overallHeightMm model height (informational; the file stores per-layer Z)
     * @param printTimeSec estimated print time
     * @param extension the filename extension the caller will use; selects the codec
     * @param preview565 optional thumbnail, raw RGB565 little-endian, top-down
     */
    fun convert(
        sl1: ByteArray,
        settings: ResinSettings,
        printer: ResinPrinter,
        overallHeightMm: Double,
        printTimeSec: Int,
        out: OutputStream,
        extension: String = "pwmx",
        version: Int = DEFAULT_VERSION,
        preview565: ByteArray? = null,
    ) {
        // Hold the COMPRESSED layer PNGs, not decoded masks. A 6K print's raw masks are 20.7 MB each
        // (~10 GB across a print); the PNGs are a fraction of that and are slices of the SL1 we were
        // already handed. maskAt decodes one layer at a time, which is what the writer is built for.
        val layerPngs = ArrayList<ByteArray>()
        var width = 0
        var height = 0
        ZipInputStream(ByteArrayInputStream(sl1)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".png", ignoreCase = true)) {
                    val baos = ByteArrayOutputStream()
                    zip.copyTo(baos)
                    val bytes = baos.toByteArray()
                    if (width == 0) {
                        val img = PngDecoder.decode(bytes)
                        width = img.width
                        height = img.height
                    }
                    layerPngs += bytes
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        require(layerPngs.isNotEmpty()) { "SL1 contained no layer images" }

        // The machine table describes the printer's real panel, and the raster has to match it or the
        // printer would scale or crop the image. A mismatch means the engine sliced for a different
        // panel -- i.e. display_pixels_x/y never reached it.
        require(width == printer.resolutionX && height == printer.resolutionY) {
            "SL1 raster ${width}x$height does not match ${printer.name} " +
                "(${printer.resolutionX}x${printer.resolutionY}); the engine was sliced for another panel"
        }

        val params = AnycubicPwWriter.Params(
            printerName = printer.name,
            resolutionX = width,
            resolutionY = height,
            pixelSizeMicrons = printer.pixelSizeMicrons,
            displayWidthMm = printer.buildWidthMm.toFloat(),
            displayHeightMm = printer.buildDepthMm.toFloat(),
            machineZmm = printer.buildHeightMm.toFloat(),
            layerHeightMm = settings.layerHeightMm.toFloat(),
            exposureSec = settings.exposureSec.toFloat(),
            bottomExposureSec = settings.bottomExposureSec.toFloat(),
            bottomLayerCount = settings.bottomLayers,
            lightOffSec = settings.lightOffSec.toFloat(),
            liftDistanceMm = settings.liftDistanceMm.toFloat(),
            liftSpeedMmPerMin = settings.liftSpeedMmPerMin.toFloat(),
            retractSpeedMmPerMin = settings.retractSpeedMmPerMin.toFloat(),
            bottomLiftSpeedMmPerMin = settings.bottomLiftSpeedMmPerMin.toFloat(),
            bottomRetractSpeedMmPerMin = settings.bottomRetractSpeedMmPerMin.toFloat(),
            antiAliasing = settings.antiAliasLevels.coerceAtLeast(1),
            transitionLayerCount = settings.transitionLayers,
            printTimeSec = printTimeSec,
            supportsEnabled = settings.supportsEnabled,
            supportsDensity = settings.supportDensityPercent.toFloat(),
        )

        AnycubicPwWriter.write(
            out = out,
            layerCount = layerPngs.size,
            maskAt = { i -> decodeRepaired(layerPngs[i], width, height, settings) },
            params = params,
            version = version,
            codec = codecFor(extension),
            preview565 = preview565,
        )
    }

    /** Decode one layer, apply the same repair the CTB path applies, and hand back an 8-bit mask. */
    private fun decodeRepaired(png: ByteArray, width: Int, height: Int, settings: ResinSettings): ByteArray {
        var mask = PngDecoder.decode(png).gray
        if (settings.repairLayers) {
            mask = LayerRepair.removeSpecks(mask, width, height, settings.minSpeckPixels)
            mask = LayerRepair.fillHoles(mask, width, height)
        }
        return mask
    }
}
