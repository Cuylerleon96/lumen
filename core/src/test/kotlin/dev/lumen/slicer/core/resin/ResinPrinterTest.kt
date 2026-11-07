package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Printer geometry.
 *
 * The check that matters: **resolution × pixel pitch must reproduce the machine's published build
 * volume.** A pitch copied off a marketing page can be rounded or simply wrong and nothing about the
 * number looks suspicious — the Mono X 6K was carried at 34.45 µm, which derives to 198.43 × 124.02 mm
 * against a published 195.84 × 122.4 mm, i.e. a 2.5 mm error in the usable area (and therefore in
 * every fit-check and every rasterised pixel's size). Only the arithmetic catches that.
 *
 * 34.0 µm is the value that makes 5760 × 0.034 = 195.84 exactly, which is the published figure.
 */
class ResinPrinterTest {

    private fun assertVolume(name: String, wMm: Double, dMm: Double) {
        val p = ResinPrinter.byName(name) ?: error("no built-in printer named $name")
        assertEquals(wMm, p.buildWidthMm, 0.05, "$name width: got ${p.buildWidthMm}")
        assertEquals(dMm, p.buildDepthMm, 0.05, "$name depth: got ${p.buildDepthMm}")
    }

    @Test
    fun `the anycubic machines match their published build volumes`() {
        assertVolume("Anycubic Photon Mono X", 192.0, 120.0)
        assertVolume("Anycubic Photon Mono X 6K", 195.84, 122.4)
    }

    @Test
    fun `the machines this project is developed against come first`() {
        assertEquals("Anycubic Photon Mono X", ResinPrinter.builtIn.first().name)
        assertTrue(ResinPrinter.byName("Anycubic Photon Mono X 6K") != null)
    }

    @Test
    fun `the 4K and 6K machines are distinct entries`() {
        val x = ResinPrinter.byName("Anycubic Photon Mono X")!!
        val x6k = ResinPrinter.byName("Anycubic Photon Mono X 6K")!!
        assertTrue(x.resolutionX != x6k.resolutionX, "4K and 6K panels are not the same geometry")
        assertTrue(x.pixelSizeMicrons != x6k.pixelSizeMicrons)
        assertTrue(x6k.buildWidthMm > x.buildWidthMm, "the 6K panel is the wider one")
    }

    @Test
    fun `every built-in printer has plausible geometry`() {
        for (p in ResinPrinter.builtIn) {
            assertTrue(p.resolutionX > 0 && p.resolutionY > 0, p.name)
            assertTrue(p.pixelSizeMicrons in 10.0..100.0, "${p.name}: ${p.pixelSizeMicrons} µm")
            assertTrue(p.buildHeightMm > 50.0, p.name)
            assertTrue(p.buildWidthMm > 50.0 && p.buildDepthMm > 50.0, p.name)
            // A 4K-class panel is never under 100 mm wide; catches a decimal slip in the pitch.
            assertTrue(p.buildWidthMm < 400.0, "${p.name}: ${p.buildWidthMm} mm looks wrong")
        }
    }
}
