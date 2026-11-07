package dev.lumen.slicer.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals

class ObjReaderTest {

    @Test
    fun `reads a quad face as two triangles`() {
        val obj = """
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            f 1 2 3 4
        """.trimIndent()
        val mesh = ObjReader.read(obj)
        assertEquals(2, mesh.triangleCount)
        assertEquals(18, mesh.positions.size)
    }

    @Test
    fun `handles v-vt-vn face tokens and negative indices`() {
        val obj = """
            v 0 0 0
            v 2 0 0
            v 0 2 0
            f 1/1/1 2/2/2 3/3/3
            f -3 -2 -1
        """.trimIndent()
        val mesh = ObjReader.read(obj)
        assertEquals(2, mesh.triangleCount)
    }
}
