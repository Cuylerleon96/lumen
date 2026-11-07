package dev.lumen.slicer.core.resin

import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes PrusaSlicer-format **SL1** archives: a ZIP holding one 8-bit grayscale PNG per layer plus
 * a `config.ini` with the exposure/settings. This is the open resin container — the equivalent of
 * G-code for a filament printer — and the first output format this app targets.
 *
 * Layers are pulled one at a time via [layerAt] so a full print never has to sit in RAM (a single
 * 4098×2560 layer is ~10 MB; a 200-layer print would be ~2 GB if buffered).
 *
 * Layout (matches PrusaSlicer, verified against its SL1 writer):
 *   config.ini, config.json, prusaslicer.ini, prusaslicer.json, then `<project>00000.png`, ...
 */
object Sl1Writer {
    fun write(
        out: OutputStream,
        jobName: String,
        printer: ResinPrinter,
        settings: ResinSettings,
        layerCount: Int,
        layerAt: (Int) -> LayerImage,
    ) {
        val project = sanitise(jobName)
        ZipOutputStream(out).use { zip ->
            entry(zip, "config.ini", configIni(project, printer, settings, layerCount).toByteArray(Charsets.UTF_8))
            entry(zip, "config.json", "{}".toByteArray(Charsets.UTF_8))
            entry(zip, "prusaslicer.ini", "[print]\n".toByteArray(Charsets.UTF_8))
            entry(zip, "prusaslicer.json", "{}".toByteArray(Charsets.UTF_8))
            for (i in 0 until layerCount) {
                val layer = layerAt(i)
                val name = project + String.format("%05d", i) + ".png"
                entry(zip, name, PngEncoder.encode(layer.width, layer.height, layer.pixels))
            }
        }
    }

    /** Builds the `config.ini` text (the field names PrusaSlicer and UVTools read). */
    fun configIni(project: String, printer: ResinPrinter, s: ResinSettings, layerCount: Int): String {
        val slowLayers = minOf(s.bottomLayers, layerCount)
        val fadeLayers = minOf(s.transitionLayers, (layerCount - slowLayers).coerceAtLeast(0))
        val fastLayers = (layerCount - slowLayers - fadeLayers).coerceAtLeast(0)
        var printTime = 0.0
        for (i in 0 until layerCount) {
            val bottom = i < slowLayers
            val exposure = if (bottom) s.bottomExposureSec else s.exposureSec
            val lightOff = if (bottom) s.bottomLightOffSec else s.lightOffSec
            printTime += exposure + lightOff + s.motionTimeSec(bottom)
        }
        return buildString {
            line("action", "print")
            line("jobDir", project)
            line("fileCreationTimestamp", Instant.now().toString())
            line("layerHeight", s.layerHeightMm)
            line("expTime", s.exposureSec)
            line("expTimeFirst", s.bottomExposureSec)
            line("numFade", fadeLayers)
            line("numFast", fastLayers)
            line("numSlow", slowLayers)
            line("printTime", printTime.toInt())
            line("printerModel", printer.name)
            line("printerProfile", printer.name)
            line("printerVariant", "default")
            line("prusaSlicerVersion", "MobileSlicer")
            line("usedMaterial", 0)
        }
    }

    private fun StringBuilder.line(key: String, value: Any) {
        append(key).append(" = ").append(value.toString()).append('\n')
    }

    private fun entry(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    /** Keeps layer filenames safe: letters, digits, dot, dash and underscore only. */
    private fun sanitise(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "print" }
}
