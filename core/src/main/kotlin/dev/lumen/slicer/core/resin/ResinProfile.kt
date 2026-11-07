package dev.lumen.slicer.core.resin

/**
 * A starting exposure profile for a specific resin (E). Community values are a **starting point** —
 * every machine + resin batch differs, so the user tunes from here (or runs an exposure test, L).
 */
data class ResinProfile(
    val name: String,
    val layerHeightMm: Double,
    val exposureSec: Double,
    val bottomExposureSec: Double,
    val bottomLayers: Int,
    val lightOffSec: Double,
    val densityGPerMl: Double = 1.1,
) {
    /** Overlay this profile's values onto [s], leaving motion + unrelated settings untouched. */
    fun applyTo(s: ResinSettings): ResinSettings = s.copy(
        layerHeightMm = layerHeightMm,
        exposureSec = exposureSec,
        bottomExposureSec = bottomExposureSec,
        bottomLayers = bottomLayers,
        lightOffSec = lightOffSec,
        bottomLightOffSec = lightOffSec,
        materialDensityGPerMl = densityGPerMl,
    )

    companion object {
        /** Common 405 nm resins, standard 0.05 mm. Approximate community defaults. */
        val builtIn: List<ResinProfile> = listOf(
            ResinProfile("Elegoo Standard Grey", 0.05, 2.5, 25.0, 5, 7.0),
            ResinProfile("Elegoo Water Washable", 0.05, 3.0, 30.0, 6, 7.0),
            ResinProfile("Elegoo ABS-Like Grey", 0.05, 2.6, 30.0, 6, 7.0),
            ResinProfile("Siraya Tech Fast Grey", 0.05, 2.0, 20.0, 5, 6.0),
            ResinProfile("Siraya Tech Blu (tough)", 0.05, 6.0, 40.0, 6, 8.0),
            ResinProfile("Anycubic Standard Grey", 0.05, 2.5, 25.0, 5, 7.0),
            ResinProfile("Generic 405 nm", 0.05, 2.5, 25.0, 5, 7.0),
        )

        fun byName(name: String?): ResinProfile? = builtIn.firstOrNull { it.name == name }
    }
}
