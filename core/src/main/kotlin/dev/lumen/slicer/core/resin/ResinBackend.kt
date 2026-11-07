package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.Technology
import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.StlWriter
import dev.lumen.slicer.core.slicing.SliceOutcome
import dev.lumen.slicer.core.slicing.SliceRequest
import dev.lumen.slicer.core.slicing.SlicingBackend
import java.io.File
import java.io.OutputStream

/** A resin (MSLA) slice request: a placed mesh, a printer and its settings. */
class ResinSliceRequest(
    val mesh: Mesh,
    val printer: ResinPrinter,
    val settings: ResinSettings,
    override val outputName: String,
    /** Writable dir for temp STL/SL1 files (the app's cacheDir); null -> system temp. */
    val workDir: java.io.File? = null,
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

        // Drain holes are part of the solid before anything slices it (see drillDrainHoles).
        val mesh = drillDrainHoles(r, onProgress)

        // Prefer the real PrusaSlicer SLA engine (supports + hollowing + rasterize); fall back to the
        // naive rasterizer when the native library isn't present (host tests / non-arm64).
        if (SlaEngine.ensureLoaded()) {
            sliceWithEngine(r, mesh, out, onProgress)?.let { return it }
        }

        val layers = MeshRasterizer.layerCount(mesh, r.settings)
        onProgress(0, "Preparing $layers layers")
        Sl1Writer.write(out, r.outputName, r.printer, r.settings, layers) { i ->
            val image = MeshRasterizer.rasterizeLayer(mesh, r.printer, r.settings, i)
            onProgress(((i + 1) * 100) / layers, "Layer ${i + 1}/$layers")
            image
        }
        return SliceOutcome.Layers(r.outputName, layers)
    }

    /**
     * Apply the drain holes to the mesh before slicing. Drilling happens *here* rather than in the
     * UI so every caller (slice, preview, export) gets the same solid: the SLA engine hollows
     * whatever it is handed, so the hole has to exist before hollowing turns it into a passage —
     * post-processing finished layers cannot reconnect a sealed cavity.
     */
    private fun drillDrainHoles(r: ResinSliceRequest, onProgress: (percent: Int, text: String) -> Unit): Mesh {
        val holes = r.settings.drainHoles
        if (holes.isEmpty()) return r.mesh
        onProgress(1, "Drilling ${holes.size} drain hole${if (holes.size == 1) "" else "s"}")
        return DrainHoles.drill(r.mesh, holes, r.settings.wallThicknessMm.toFloat())
    }

    /** Run the native SLA engine (mesh -> SL1). Returns null on failure so the caller can fall back. */
    private fun sliceWithEngine(
        r: ResinSliceRequest,
        mesh: Mesh,
        out: OutputStream,
        onProgress: (percent: Int, text: String) -> Unit,
    ): SliceOutcome? {
        val stl = if (r.workDir != null) File.createTempFile("mslice", ".stl", r.workDir) else File.createTempFile("mslice", ".stl")
        val sl1 = if (r.workDir != null) File.createTempFile("mslice", ".sl1", r.workDir) else File.createTempFile("mslice", ".sl1")
        try {
            onProgress(2, "Preparing mesh")
            stl.outputStream().use { StlWriter.write(mesh, it) }
            println("ResinBackend: wrote STL ${stl.absolutePath} (${stl.length()} bytes)")
            onProgress(5, "Slicing (SLA engine)")
            val rc = SlaEngine.nativeSlice(stl.absolutePath, sl1.absolutePath, r.settings.toEngineJson(r.printer))
            println("ResinBackend: nativeSlice rc=$rc sl1Bytes=${sl1.length()}")
            if (rc != 0 || !sl1.exists() || sl1.length() == 0L) return null
            onProgress(95, "Writing archive")
            sl1.inputStream().use { it.copyTo(out) }
            onProgress(100, "Done")
            return SliceOutcome.Layers(r.outputName, MeshRasterizer.layerCount(mesh, r.settings))
        } catch (t: Throwable) {
            println("ResinBackend: engine slice failed: $t")
            return null
        } finally {
            stl.delete(); sl1.delete()
        }
    }
}
