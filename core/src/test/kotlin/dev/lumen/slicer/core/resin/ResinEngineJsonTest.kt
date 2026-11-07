package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The settings JSON handed to the native SLA engine.
 *
 * The panel geometry has to be in here. Without it the engine keeps its built-in default (120 x 68 mm,
 * 2560 px — the Prusa SL1) and slices every printer at that resolution, so an SL1 for a Mono X 6K
 * comes out at a quarter of its panel. A missing key is silent: the shim logs and skips unknown keys,
 * and the slice still succeeds — it is just the wrong size. Hence asserting the keys are present.
 */
class ResinEngineJsonTest {

    private val monoX6k = ResinPrinter.byName("Anycubic Photon Mono X 6K")!!

    @Test
    fun `the printer's panel geometry travels with the settings`() {
        val json = ResinSettings().toEngineJson(monoX6k)

        // The PANEL, not the published printable area: 5760 x 34.4 um = 198.144 mm. The raster has to
        // cover the whole panel, so this is what the engine must be told.
        assertTrue(json.contains("\"display_width\":\"198.144\""), "panel width in mm: $json")
        assertTrue(json.contains("\"display_height\":\"123.84\""), "panel depth in mm: $json")
        assertTrue(json.contains("\"display_pixels_x\":\"5760\""), "panel pixels across: $json")
        assertTrue(json.contains("\"display_pixels_y\":\"3600\""), "panel pixels down: $json")
    }

    @Test
    fun `the support settings travel too`() {
        val s = ResinSettings(
            supportsEnabled = true,
            supportDensityPercent = 80,
            supportPillarDiameterMm = 1.2,
            supportContactDiameterMm = 0.4,
            supportObjectGapMm = 0.3,
        )

        val json = s.toEngineJson(monoX6k)

        assertTrue(json.contains("\"supports_enable\":\"1\""))
        assertTrue(json.contains("\"support_points_density_relative\":\"80\""))
        assertTrue(json.contains("\"support_pillar_diameter\":\"1.2\""))
        assertTrue(json.contains("\"support_head_front_diameter\":\"0.4\""))
        // support_object_elevation, not support_object_xy_distance: the latter does not exist in
        // PrusaSlicer's SLA config, so the engine reported "Unknown option" and dropped it silently.
        assertTrue(json.contains("\"support_object_elevation\":\"0.3\""))
    }

    @Test
    fun `supports off is sent as zero, not omitted`() {
        val json = ResinSettings(supportsEnabled = false).toEngineJson(monoX6k)

        assertTrue(json.contains("\"supports_enable\":\"0\""), "off must be explicit: $json")
    }

    @Test
    fun `hollowing and drain holes agree with each other`() {
        val json = ResinSettings(hollowing = true, wallThicknessMm = 1.5).toEngineJson(monoX6k)

        assertTrue(json.contains("\"hollowing_enable\":\"1\""))
        assertTrue(json.contains("\"hollowing_min_thickness\":\"1.5\""))
    }

    @Test
    fun `without a printer the panel keys are absent rather than defaulted`() {
        // Only the host tests use this form; the app always passes a printer. Asserting the absence
        // keeps the two forms from drifting into each other silently.
        val json = ResinSettings().toEngineJson()

        assertFalse(json.contains("display_width"), "no printer, no panel claim: $json")
        assertTrue(json.contains("layer_height"), "the rest of the settings still travel")
    }
}
