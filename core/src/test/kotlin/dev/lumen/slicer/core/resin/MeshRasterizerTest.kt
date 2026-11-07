package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeshRasterizerTest {
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
        for (f in faces) {
            p[o++] = v[f][0]; p[o++] = v[f][1]; p[o++] = v[f][2]
        }
        return Mesh(p)
    }

    @Test
    fun layerCountSpansTheModelHeight() {
        assertEquals(200, MeshRasterizer.layerCount(box(10f, 10f, 10f), ResinSettings(layerHeightMm = 0.05)))
        assertEquals(100, MeshRasterizer.layerCount(box(10f, 10f, 5f), ResinSettings(layerHeightMm = 0.05)))
    }

    @Test
    fun aCrossSectionFillsTheExpectedArea() {
        // A 10 mm cube in a 20 mm (200 px @ 0.1 mm/px) build area covers ~1/4 of the plate.
        val printer = ResinPrinter("t", 200, 200, 100.0, 10.0)
        val layer = MeshRasterizer.rasterizeLayer(box(10f, 10f, 10f), printer, ResinSettings(), index = 100)
        val coverage = layer.coverage()
        assertTrue(coverage in 0.20..0.30, "coverage=$coverage")
    }

    @Test
    fun emptyModelProducesEmptyLayers() {
        val printer = ResinPrinter("t", 64, 64, 100.0, 10.0)
        val layer = MeshRasterizer.rasterizeLayer(Mesh(FloatArray(0)), printer, ResinSettings(), index = 0)
        assertEquals(0.0, layer.coverage())
    }
}
