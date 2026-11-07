package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh
import kotlin.test.Test
import kotlin.test.assertTrue

class ResinEstimatorTest {

    @Test
    fun `estimate scales with volume and density`() {
        val mesh = TestBox.of(20f, 20f, 20f) // 8000 mm³ = 8 ml
        val e = ResinEstimator.estimate(mesh, densityGPerMl = 1.1, pricePerKg = 30.0)
        assertTrue(kotlin.math.abs(e.volumeMl - 8.0) < 1e-3, "volumeMl=${e.volumeMl}")
        assertTrue(kotlin.math.abs(e.massG - 8.8) < 1e-3, "massG=${e.massG}")
        assertTrue(kotlin.math.abs(e.cost - 0.264) < 1e-3, "cost=${e.cost}")
    }
}

/** Minimal closed box for tests (kept local so this test has no cross-package dependency). */
private object TestBox {
    fun of(w: Float, d: Float, h: Float): Mesh {
        val v = Array(8) { i ->
            floatArrayOf(if (i and 1 != 0) w else 0f, if (i and 2 != 0) d else 0f, if (i and 4 != 0) h else 0f)
        }
        val quads = listOf(
            intArrayOf(0, 2, 3, 1), intArrayOf(4, 5, 7, 6), intArrayOf(0, 1, 5, 4),
            intArrayOf(2, 6, 7, 3), intArrayOf(0, 4, 6, 2), intArrayOf(1, 3, 7, 5),
        )
        val out = ArrayList<Float>()
        for (q in quads) for (t in listOf(intArrayOf(q[0], q[1], q[2]), intArrayOf(q[0], q[2], q[3]))) for (i in t) out.addAll(v[i].toList())
        return Mesh(out.toFloatArray())
    }
}
