package io.github.whoxllm.orcamicro.core.slicing

import io.github.whoxllm.orcamicro.core.Technology
import java.io.OutputStream

/**
 * One technology-agnostic slice request (ADR 0004). Concrete requests carry what their backend
 * needs — FDM: resolved preset paths; resin: mesh + printer + settings. A plain interface (not
 * sealed) so **any** backend — including experimental ones in other packages — can define its own.
 */
interface SliceRequest {
    val outputName: String
}

/** The result of a slice: G-code (FDM) or a layer-image archive (resin). */
sealed interface SliceOutcome {
    val warnings: List<String>

    data class Gcode(
        val path: String,
        val layerCount: Int = 0,
        override val warnings: List<String> = emptyList(),
    ) : SliceOutcome

    data class Layers(
        val path: String,
        val layerCount: Int,
        override val warnings: List<String> = emptyList(),
    ) : SliceOutcome
}

/**
 * A pluggable slicing engine (ADR 0004). FDM (Orca `libslic3r`) and Resin (PrusaSlicer SLA) are
 * implementations; **experimental slicing methods are added as more backends**. The UI and preview
 * speak only [SliceRequest]/[SliceOutcome], so they never branch on technology.
 */
interface SlicingBackend {
    val technology: Technology
    val displayName: String

    /** Whether this backend can handle [request]. */
    fun supports(request: SliceRequest): Boolean

    /** Slices [request] and writes the output to [out], reporting (percent, text) progress. */
    fun slice(out: OutputStream, request: SliceRequest, onProgress: (percent: Int, text: String) -> Unit): SliceOutcome
}
