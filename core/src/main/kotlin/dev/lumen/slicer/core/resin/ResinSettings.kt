package dev.lumen.slicer.core.resin

import kotlinx.serialization.Serializable

/**
 * Resin exposure and motion parameters. Mirrors the fields the SL1 `config.ini` carries,
 * plus the motion parameters (lift/retract/rest) that the CTB/GOO writers need and the
 * few extras (anti-aliasing) a masked-SLA slicer needs.
 *
 * Motion is modelled per Lychee/Chitubox conventions: a layer cycle = expose -> rest (after
 * print) -> lift (peel) -> rest (after lift) -> retract -> rest (after retract). Bottom layers
 * use slower lift/retract speeds for adhesion.
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
    /** Resin density in g/ml — for the volume→weight estimate. */
    val materialDensityGPerMl: Double = 1.1,
    /** Resin price per kilogram — for the cost estimate (0 = unknown). */
    val pricePerKg: Double = 0.0,

    // ---- Hollowing (D.4) ----
    /** Hollow the model (saves resin + reduces peel force). */
    val hollowing: Boolean = false,
    /** Shell wall thickness in mm. */
    val wallThicknessMm: Double = 2.0,
    /** Voxel closing distance in mm (fills thin features so the shell stays connected). */
    val hollowingClosingDistanceMm: Double = 0.5,
    /**
     * Drain holes drilled through the shell before slicing (D.4). Applied by the backend, not the UI:
     * the engine hollows whatever mesh it is handed, so a hole must be part of the solid *before*
     * hollowing turns it into a passage.
     */
    val drainHoles: List<DrainHole> = emptyList(),

    // ---- Layer repair (K) ----
    /** Remove floating specks + fill pinholes in each layer before export. */
    val repairLayers: Boolean = false,
    /** A connected component smaller than this many pixels is treated as a speck. */
    val minSpeckPixels: Int = 4,

    // ---- Supports (D.3) ----
    /** Generate supports (auto + editable). */
    val supportsEnabled: Boolean = true,
    /** Relative support-point density (percent of the auto default). */
    val supportDensityPercent: Int = 100,
    /** Support head (contact) front diameter in mm. */
    val supportContactDiameterMm: Double = 0.4,
    /** Support pillar diameter in mm. */
    val supportPillarDiameterMm: Double = 0.8,
    /** XY gap between supports and the model in mm. */
    val supportObjectGapMm: Double = 0.2,

    // ---- Motion (Z) ----
    /** Z distance lifted off the film to peel, in mm. */
    val liftDistanceMm: Double = 5.0,
    /** Z distance retracted back toward the film, in mm. */
    val retractDistanceMm: Double = 5.0,
    /** Lift (peel) speed for normal layers, mm/min. */
    val liftSpeedMmPerMin: Double = 60.0,
    /** Retract speed for normal layers, mm/min. */
    val retractSpeedMmPerMin: Double = 150.0,
    /** Lift speed for bottom layers, mm/min (slower for adhesion). */
    val bottomLiftSpeedMmPerMin: Double = 30.0,
    /** Retract speed for bottom layers, mm/min. */
    val bottomRetractSpeedMmPerMin: Double = 60.0,
    /** Rest after exposure, before lift (aka "wait after print"), seconds. */
    val restBeforeLiftSec: Double = 0.0,
    /** Rest at the top of the lift, before retract ("wait after lift"), seconds. */
    val restAfterLiftSec: Double = 0.0,
    /** Rest after retract, before the next exposure ("wait after retract"), seconds. */
    val restAfterRetractSec: Double = 1.0,
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

    /** Seconds of Z motion + rest for one layer cycle (excludes exposure and light-off). */
    fun motionTimeSec(bottom: Boolean): Double {
        val lift = if (bottom) bottomLiftSpeedMmPerMin else liftSpeedMmPerMin
        val retract = if (bottom) bottomRetractSpeedMmPerMin else retractSpeedMmPerMin
        val liftSec = if (lift > 0) liftDistanceMm / lift * 60.0 else 0.0
        val retractSec = if (retract > 0) retractDistanceMm / retract * 60.0 else 0.0
        return restBeforeLiftSec + liftSec + restAfterLiftSec + retractSec + restAfterRetractSec
    }

    /** Settings as a JSON object of PrusaSlicer SLA config keys (consumed by the JNI engine bridge). */
    /**
     * The engine's settings JSON.
     *
     * [printer] is required for a correct slice: without it the engine keeps its built-in default
     * panel (120 x 68 mm, 2560 px — the Prusa SL1) and slices every printer at that resolution. The
     * panel geometry has to travel with the settings because it *is* the bed: `display_width`/
     * `display_height` in mm are the build area the engine clips against, and
     * `display_pixels_x`/`display_pixels_y` are the raster size the printer will read back.
     */
    fun toEngineJson(printer: ResinPrinter? = null): String = buildString {
        append('{')
        append("\"layer_height\":\"$layerHeightMm\"")
        append(",\"exposure_time\":\"$exposureSec\"")
        append(",\"initial_exposure_time\":\"$bottomExposureSec\"")
        append(",\"faded_layers\":\"$transitionLayers\"")
        append(",\"hollowing_enable\":\"${if (hollowing) 1 else 0}\"")
        append(",\"hollowing_min_thickness\":\"$wallThicknessMm\"")
        append(",\"hollowing_closing_distance\":\"$hollowingClosingDistanceMm\"")
        append(",\"supports_enable\":\"${if (supportsEnabled) 1 else 0}\"")
        append(",\"support_points_density_relative\":\"$supportDensityPercent\"")
        append(",\"support_head_front_diameter\":\"$supportContactDiameterMm\"")
        append(",\"support_pillar_diameter\":\"$supportPillarDiameterMm\"")
        // PrusaSlicer's SLA config has NO support/object XY-distance key (checked against the
        // vendored PrintConfig: the distance keys are support_base_safety_distance and
        // support_max_pillar_link_distance, neither of which is this). The real concept for "keep the
        // supports off the model" is support_object_elevation -- how far the supports lift the object.
        // The old key was invented, so the engine reported "Unknown option" and dropped it silently.
        append(",\"support_object_elevation\":\"$supportObjectGapMm\"")
        if (printer != null) {
            append(",\"display_width\":\"${printer.buildWidthMm}\"")
            append(",\"display_height\":\"${printer.buildDepthMm}\"")
            append(",\"display_pixels_x\":\"${printer.resolutionX}\"")
            append(",\"display_pixels_y\":\"${printer.resolutionY}\"")
        }
        append('}')
    }

    /**
     * Estimated total print time (seconds) for a model [heightMm] tall: per-layer exposure +
     * light-off + Z motion/rest, using bottom speeds for the first [bottomLayers]. D.7 (partial).
     */
    fun estimatedPrintTimeSec(heightMm: Double): Double {
        val layers = (heightMm / layerHeightMm).toInt().coerceAtLeast(1)
        var total = 0.0
        for (i in 0 until layers) {
            val bottom = i < bottomLayers
            total += exposureFor(i) + (if (bottom) bottomLightOffSec else lightOffSec) + motionTimeSec(bottom)
        }
        return total
    }

    /** Resin volume/weight/cost for a solid mesh of [volumeMm3] (hollowing reduces the real volume). */
    fun estimateResin(volumeMm3: Double): ResinEstimate = ResinEstimator.estimate(volumeMm3, materialDensityGPerMl, pricePerKg)
}
