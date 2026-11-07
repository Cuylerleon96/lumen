package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.slicing.SliceOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ResinBackendTest {
    /** A solid box [sx]×[sy]×[sz] mm, sitting on the plate (z 0..sz). */
    private fun box(sx: Float, sy: Float, sz: Float): Mesh {
        val x0 = -sx / 2; val y0 = -sy / 2
        val x1 = sx / 2; val y1 = sy / 2; val z1 = sz
        val v = arrayOf(
            floatArrayOf(x0, y0, 0f), floatArrayOf(x1, y0, 0f), floatArrayOf(x1, y1, 0f), floatArrayOf(x0, y1, 0f),
            floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1),
        )
        val faces = intArrayOf(
            0, 1, 2, 0, 2, 3, 4, 6, 5, 4, 7, 6, 0, 4, 5, 0, 5, 1,
            1, 5, 6, 1, 6, 2, 2, 6, 7, 2, 7, 3, 3, 7, 4, 3, 4, 0,
        )
        val p = FloatArray(faces.size * 3)
        var o = 0
        for (f in faces) { p[o++] = v[f][0]; p[o++] = v[f][1]; p[o++] = v[f][2] }
        return Mesh(p)
    }

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
}
