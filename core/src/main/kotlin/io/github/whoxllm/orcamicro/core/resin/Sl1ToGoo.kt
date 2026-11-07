package io.github.whoxllm.orcamicro.core.resin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Converts a PrusaSlicer **SL1** (ZIP of 8-bit grayscale PNGs) into a **GOO** for Elegoo/Phrozen
 * printers. Only the small compressed PNGs are held; each mask is decoded on demand (one at a time),
 * so a 500+ layer print never loads gigabytes of masks.
 */
object Sl1ToGoo {

    fun convert(
        sl1: ByteArray,
        settings: ResinSettings,
        printer: ResinPrinter,
        overallHeightMm: Double,
        printTimeSec: Int,
        out: OutputStream,
    ) {
        val named = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(sl1)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".png", ignoreCase = true)) {
                    val baos = ByteArrayOutputStream()
                    zip.copyTo(baos)
                    named += e.name to baos.toByteArray()   // compressed PNG (small)
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        require(named.isNotEmpty()) { "SL1 contained no layer images" }
        val sorted = named.sortedBy { it.first }   // PrusaSlicer names layers <jobid><index>.png
        val first = PngDecoder.decode(sorted[0].second)
        val width = first.width
        val height = first.height

        GooWriter.write(
            layerCount = sorted.size,
            maskAt = { i ->
                val img = PngDecoder.decode(sorted[i].second)
                var m = ByteArray(img.gray.size) { ((img.gray[it].toInt() and 0xFF) shr 1).toByte() }   // 0..255 → 0..127
                if (settings.repairLayers) {
                    m = LayerRepair.removeSpecks(m, img.width, img.height, settings.minSpeckPixels)
                    m = LayerRepair.fillHoles(m, img.width, img.height)
                }
                m
            },
            width = width,
            height = height,
            layerHeightMm = settings.layerHeightMm,
            exposureSec = settings.exposureSec,
            bottomExposureSec = settings.bottomExposureSec,
            bottomLayers = settings.bottomLayers,
            lightOffSec = settings.lightOffSec,
            machineZmm = overallHeightMm,
            plateWidthMm = printer.buildWidthMm,
            plateDepthMm = printer.buildDepthMm,
            out = out,
        )
    }
}
