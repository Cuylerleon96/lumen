package io.github.whoxllm.orcamicro.core.resin

import io.github.whoxllm.orcamicro.core.mesh.Mesh

/**
 * Resin usage estimate (D.7) for a **solid** mesh. Hollowing (the engine) reduces the real volume;
 * this is the fast upper-bound estimate shown before slicing.
 *
 * @param volumeMl cured-model volume in millilitres (1 ml = 1000 mm³)
 * @param massG    mass in grams (volume × density)
 * @param cost     cost in the caller's currency (mass × price/kg)
 */
data class ResinEstimate(val volumeMl: Double, val massG: Double, val cost: Double)

object ResinEstimator {
    const val DEFAULT_DENSITY_G_PER_ML = 1.10   // typical 405 nm resin

    /**
     * @param mesh solid mesh (mm)
     * @param densityGPerMl resin density (g/ml)
     * @param pricePerKg resin price per kilogram (0 = unknown)
     */
    fun estimate(mesh: Mesh, densityGPerMl: Double = DEFAULT_DENSITY_G_PER_ML, pricePerKg: Double = 0.0): ResinEstimate =
        estimate(mesh.volumeAbsMm3(), densityGPerMl, pricePerKg)

    /** @param volumeMm3 solid-model volume in mm³ */
    fun estimate(volumeMm3: Double, densityGPerMl: Double = DEFAULT_DENSITY_G_PER_ML, pricePerKg: Double = 0.0): ResinEstimate {
        val volumeMl = volumeMm3 / 1000.0
        val massG = volumeMl * densityGPerMl
        val cost = massG / 1000.0 * pricePerKg
        return ResinEstimate(volumeMl, massG, cost)
    }
}
