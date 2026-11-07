package io.github.whoxllm.orcamicro.core.resin

import kotlinx.serialization.Serializable

/**
 * Resin exposure and motion parameters. Mirrors the fields the SL1 `config.ini` carries,
 * plus the few extras (anti-aliasing) a masked-SLA slicer needs.
 */
@Serializable
data class ResinSettings(
    val layerHeightMm: Double = 0.05,
    /** Exposure for normal (non-bottom) layers, in seconds. */
    val exposureSec: Double = 2.5,
    /** Exposure for the bottom layers, in seconds. */
    val bottomExposureSec: Double = 25.0,
    /** Number of bottom (adhesion) layers. */
    val bottomLayers: Int = 5,
    /** Fade/transition layers blended between the bottom exposure and the normal exposure. */
    val transitionLayers: Int = 8,
    val lightOffSec: Double = 7.0,
    val bottomLightOffSec: Double = 7.0,
    /** Anti-aliasing levels (1 = off). Higher = grayscale edge smoothing. */
    val antiAliasLevels: Int = 1,
) {
    /** Exposure for layer [index] (0-based), applying bottom layers then the fade ramp. */
    fun exposureFor(index: Int): Double {
        if (index < bottomLayers) return bottomExposureSec
        val fadeEnd = bottomLayers + transitionLayers
        if (index < fadeEnd) {
            val t = (index - bottomLayers + 1).toDouble() / (transitionLayers + 1)
            return bottomExposureSec + (exposureSec - bottomExposureSec) * t
        }
        return exposureSec
    }
}
