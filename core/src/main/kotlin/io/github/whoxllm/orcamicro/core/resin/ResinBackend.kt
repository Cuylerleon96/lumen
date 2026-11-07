package io.github.whoxllm.orcamicro.core.resin

import io.github.whoxllm.orcamicro.core.Technology
import io.github.whoxllm.orcamicro.core.mesh.Mesh
import io.github.whoxllm.orcamicro.core.slicing.SliceOutcome
import io.github.whoxllm.orcamicro.core.slicing.SliceRequest
import io.github.whoxllm.orcamicro.core.slicing.SlicingBackend
import java.io.OutputStream

/** A resin (MSLA) slice request: a placed mesh, a printer and its settings. */
class ResinSliceRequest(
    val mesh: Mesh,
    val printer: ResinPrinter,
    val settings: ResinSettings,
    override val outputName: String,
) : SliceRequest

/**
 * The resin backend (ADR 0004). Today it wraps the naive [MeshRasterizer] + [Sl1Writer] to emit an
 * SL1 archive; the PrusaSlicer SLA engine (ADR 0003-B) will replace the rasterizer **behind this same
 * interface**, so callers (ViewModel, preview, export) never change.
 */
object ResinBackend : SlicingBackend {
    override val technology = Technology.RESIN
    override val displayName = "Resin (MSLA)"

    override fun supports(request: SliceRequest): Boolean = request is ResinSliceRequest

    override fun slice(
        out: OutputStream,
        request: SliceRequest,
        onProgress: (percent: Int, text: String) -> Unit,
    ): SliceOutcome {
        val r = request as? ResinSliceRequest
            ?: return SliceOutcome.Layers(request.outputName, 0, listOf("ResinBackend requires a ResinSliceRequest"))
        val layers = MeshRasterizer.layerCount(r.mesh, r.settings)
        onProgress(0, "Preparing $layers layers")
        Sl1Writer.write(out, r.outputName, r.printer, r.settings, layers) { i ->
            val image = MeshRasterizer.rasterizeLayer(r.mesh, r.printer, r.settings, i)
            onProgress(((i + 1) * 100) / layers, "Layer ${i + 1}/$layers")
            image
        }
        return SliceOutcome.Layers(r.outputName, layers)
    }
}
