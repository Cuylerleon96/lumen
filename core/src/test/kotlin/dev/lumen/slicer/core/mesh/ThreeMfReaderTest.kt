package dev.lumen.slicer.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals

class ThreeMfReaderTest {

    @Test
    fun `parses a minimal 3MF model`() {
        val xml = """
            <model>
              <resources>
                <object id="1">
                  <mesh>
                    <vertices>
                      <vertex x="0" y="0" z="0"/>
                      <vertex x="1" y="0" z="0"/>
                      <vertex x="0" y="1" z="0"/>
                      <vertex x="0" y="0" z="1"/>
                    </vertices>
                    <triangles>
                      <triangle v1="0" v2="1" v3="2"/>
                      <triangle v1="0" v2="1" v3="3"/>
                    </triangles>
                  </mesh>
                </object>
              </resources>
            </model>
        """.trimIndent()
        val mesh = ThreeMfReader.parse(xml)
        assertEquals(2, mesh.triangleCount)
        assertEquals(18, mesh.positions.size)
    }
}
