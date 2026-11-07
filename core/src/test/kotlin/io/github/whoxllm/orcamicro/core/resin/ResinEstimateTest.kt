package io.github.whoxllm.orcamicro.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResinEstimateTest {

    @Test
    fun exposureRampUsesBottomThenTransitionThenNormal() {
        val s = ResinSettings(bottomExposureSec = 25.0, exposureSec = 2.5, bottomLayers = 3, transitionLayers = 2)
        assertEquals(25.0, s.exposureFor(0))
        assertEquals(25.0, s.exposureFor(2))
        // transition layers interpolate between bottom and normal exposure
        assertTrue(s.exposureFor(3) > s.exposureSec && s.exposureFor(3) < s.bottomExposureSec)
        assertTrue(s.exposureFor(4) > s.exposureSec && s.exposureFor(4) < s.bottomExposureSec)
        assertEquals(2.5, s.exposureFor(5))
    }

    @Test
    fun estimatedTimeScalesWithHeightAndIsPositive() {
        val s = ResinSettings()
        val short = s.estimatedPrintTimeSec(10.0)
        val tall = s.estimatedPrintTimeSec(100.0)
        assertTrue(short > 0.0)
        assertTrue(tall > short)
        // 10 mm at 0.05 mm = 200 layers; time should be at least the sum of the exposures
        assertTrue(short >= 200 * s.exposureSec)
    }

    @Test
    fun engineJsonCarriesCoreKeys() {
        val json = ResinSettings(layerHeightMm = 0.03, exposureSec = 2.0, bottomExposureSec = 20.0, transitionLayers = 6).toEngineJson()
        assertTrue(json.contains("\"layer_height\":\"0.03\""))
        assertTrue(json.contains("\"exposure_time\":\"2.0\""))
        assertTrue(json.contains("\"initial_exposure_time\":\"20.0\""))
        assertTrue(json.contains("\"faded_layers\":\"6\""))
    }
}
