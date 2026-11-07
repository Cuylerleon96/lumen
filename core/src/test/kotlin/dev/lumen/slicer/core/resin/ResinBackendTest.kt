package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.MeshPrimitives
import dev.lumen.slicer.core.slicing.SliceOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ResinBackendTest {
    /**
     * A solid box [sx]×[sy]×[sz] mm, sitting on the plate (z 0..sz).
     *
     * Outward-wound via [MeshPrimitives.box]. The hand-rolled version this replaced was wound
     * inside-out (signed volume −160 mm³ for the 4×4×10 box): invisible to the rasterizer, whose
     * even-odd fill ignores winding, but it made every drain-hole assertion read "the hole did
     * nothing" — CSG classifies inside/outside from winding alone. See `CsgTest`.
     */
    private fun box(sx: Float, sy: Float, sz: Float): Mesh =
        MeshPrimitives.box(sx, sy, sz, 0f, 0f, sz / 2f)

    @Test
    fun slicesThroughTheBackendInterface() {
        val printer = ResinPrinter("t", 64, 64, 100.0, 10.0)
        val settings = ResinSettings(layerHeightMm = 0.5) // 10 mm box -> 20 layers
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        val request = ResinSliceRequest(box(10f, 10f, 10f), printer, settings, "backend_test")

        assertTrue(ResinBackend.supports(request))
        val outcome = ResinBackend.slice(out, request) { p, _ -> progress.add(p) }

        assertTrue(outcome is SliceOutcome.Layers)
        assertEquals(20, (outcome as SliceOutcome.Layers).layerCount)

        val names = mutableListOf<String>()
        val zip = ZipInputStream(ByteArrayInputStream(out.toByteArray()))
        var e = zip.nextEntry
        while (e != null) { names.add(e.name); e = zip.nextEntry }
        assertTrue(names.contains("config.ini"), "names=$names")
        assertEquals(20, names.count { it.endsWith(".png") }, "names=$names")
        assertEquals(100, progress.last(), "progress=$progress")
    }

    @Test
    fun reportsTheTechnology() {
        assertEquals(dev.lumen.slicer.core.Technology.RESIN, ResinBackend.technology)
    }

    /**
     * A drain hole has to reach the slice. Observable: the exposed area of a middle layer.
     *
     * Printer 64x64 px at 100 µm = a 6.4 x 6.4 mm bed, 0.1 mm per pixel, so a 1 mm radius bore is
     * ~314 px (π·10²) out of the 1600 px a 4 x 4 mm cross-section fills — a ~7.7 point drop in
     * coverage. Anything that silently drops the holes leaves the two coverages identical.
     */
    @Test
    fun drainHolesReachTheSlice() {
        val printer = ResinPrinter("t", 64, 64, 100.0, 10.0)
        val settings = ResinSettings(layerHeightMm = 0.5)
        val solid = ResinSliceRequest(box(4f, 4f, 10f), printer, settings, "solid")
        val drilled = ResinSliceRequest(
            box(4f, 4f, 10f),
            printer,
            settings.copy(drainHoles = listOf(DrainHole(0f, 0f, 10f, 0f, 0f, -1f, radiusMm = 1f))),
            "drilled",
        )

        val solidCoverage = middleLayerCoverage(solid)
        val drilledCoverage = middleLayerCoverage(drilled)

        assertTrue(drilledCoverage < solidCoverage, "solid=$solidCoverage drilled=$drilledCoverage")
        val expectedDrop = Math.PI * 10.0 * 10.0 / (64.0 * 64.0)
        assertEquals(expectedDrop, solidCoverage - drilledCoverage, 0.01, "solid=$solidCoverage drilled=$drilledCoverage")
    }

    /** Slice [request] and report the exposed fraction of a layer halfway up the print. */
    private fun middleLayerCoverage(request: ResinSliceRequest): Double {
        val out = ByteArrayOutputStream()
        ResinBackend.slice(out, request) { _, _ -> }
        val names = mutableListOf<String>()
        val pngs = mutableMapOf<String, ByteArray>()
        val zip = ZipInputStream(ByteArrayInputStream(out.toByteArray()))
        var e = zip.nextEntry
        while (e != null) {
            if (e.name.endsWith(".png")) {
                names.add(e.name)
                pngs[e.name] = zip.readBytes()
            }
            e = zip.nextEntry
        }
        val middle = names.sorted()[names.size / 2]
        val img = PngDecoder.decode(pngs.getValue(middle))
        return LayerImage(img.width, img.height, img.gray).coverage()
    }
}
