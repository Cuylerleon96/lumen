package dev.lumen.slicer.core.resin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class Sl1WriterTest {
    private val printer = ResinPrinter("Test printer", 8, 8, 100.0, 10.0)

    @Test
    fun writesValidSl1ArchiveWithLayerPngs() {
        val buf = ByteArrayOutputStream()
        Sl1Writer.write(buf, "my model", printer, ResinSettings(layerHeightMm = 0.1), layerCount = 3) {
            LayerImage(printer.resolutionX, printer.resolutionY, ByteArray(64) { 0xFF.toByte() })
        }

        val names = mutableListOf<String>()
        val zip = ZipInputStream(ByteArrayInputStream(buf.toByteArray()))
        var e = zip.nextEntry
        while (e != null) {
            names.add(e.name)
            e = zip.nextEntry
        }
        assertTrue(names.contains("config.ini"), "names=$names")
        // Whitespace in the job name must be sanitised for the layer filenames.
        assertTrue(names.contains("my_model00000.png"), "names=$names")
        assertTrue(names.contains("my_model00002.png"), "names=$names")
    }

    @Test
    fun configIniCarriesTheExposureFields() {
        val text = Sl1Writer.configIni("p", printer, ResinSettings(), layerCount = 100)
        for (key in listOf("action", "jobDir", "layerHeight", "expTime", "expTimeFirst", "numFade", "numSlow", "printTime", "usedMaterial")) {
            assertTrue(text.contains("$key = "), "missing $key in:\n$text")
        }
    }

    @Test
    fun motionTimeUsesBottomSpeedsForBottomLayers() {
        val s = ResinSettings(
            liftDistanceMm = 6.0, retractDistanceMm = 6.0,
            liftSpeedMmPerMin = 60.0, retractSpeedMmPerMin = 120.0,
            bottomLiftSpeedMmPerMin = 30.0, bottomRetractSpeedMmPerMin = 60.0,
            restBeforeLiftSec = 0.0, restAfterLiftSec = 0.0, restAfterRetractSec = 1.0,
        )
        // normal: 6/60*60 + 6/120*60 + 1 = 6 + 3 + 1 = 10
        assertEquals(10.0, s.motionTimeSec(bottom = false), 1e-9)
        // bottom: 6/30*60 + 6/60*60 + 1 = 12 + 6 + 1 = 19
        assertEquals(19.0, s.motionTimeSec(bottom = true), 1e-9)
    }

    @Test
    fun exposureRampsFromBottomToNormal() {
        val s = ResinSettings(exposureSec = 2.0, bottomExposureSec = 20.0, bottomLayers = 2, transitionLayers = 4)
        assertEquals(20.0, s.exposureFor(0))
        assertEquals(20.0, s.exposureFor(1))
        val mid = s.exposureFor(3)
        assertTrue(mid in 2.0..20.0)
        assertEquals(2.0, s.exposureFor(50))
    }
}
