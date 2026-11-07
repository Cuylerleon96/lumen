package io.github.whoxllm.orcamicro.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalibrationModelsTest {

    @Test
    fun `dimension cube is the requested size`() {
        val m = CalibrationModels.dimensionCube(20f)
        val b = m.bounds()
        assertEquals(20f, b.size.x, 1e-3f)
        assertEquals(20f, b.size.y, 1e-3f)
        assertEquals(20f, b.size.z, 1e-3f)
        assertEquals(0f, b.min.z, 1e-3f)   // sits on the bed
        assertEquals(12, m.triangleCount)
    }

    @Test
    fun `step bar has one cube per step`() {
        val m = CalibrationModels.stepBar(steps = 5)
        assertEquals(5 * 12, m.triangleCount)
        assertTrue(m.bounds().size.x > 5f)   // spans multiple cubes
    }
}
