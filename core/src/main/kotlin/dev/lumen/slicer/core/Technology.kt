package dev.lumen.slicer.core

import kotlinx.serialization.Serializable

/**
 * Which slicer the user picked on first launch. Persisted, and switchable later.
 * The FDM path uses OrcaSlicer's engine; the resin path uses the SLA pipeline.
 */
@Serializable
enum class Technology(
    val displayName: String,
    val blurb: String,
) {
    FDM(
        "FDM / filament",
        "Fused-filament printers — Bambu, Prusa, Voron, Ender and ~950 more. Slices to G-code.",
    ),
    RESIN(
        "Resin / MSLA",
        "Masked-SLA resin printers — Elegoo, Anycubic, Phrozen. Slices to per-layer images (.sl1).",
    );

    companion object {
        fun fromName(name: String?): Technology? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
