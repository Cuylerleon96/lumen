// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.lumen.slicer.core.project

import dev.lumen.slicer.core.mesh.Mesh
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 3MF project writer.
 *
 * The interesting assertions here are the ones a round-trip through our own code CANNOT catch: the
 * literal expected XML (so a structural change is visible in the diff), the part-with-every-object rule
 * (the engine's empty-volume trap), and the locale test — a decimal comma would corrupt every
 * coordinate on a phone configured for a comma locale while passing on an English one.
 */
class ThreeMfTest {

    private fun meshOf(vararg triangles: FloatArray): Mesh =
        Mesh(triangles.flatMap { it.toList() }.toFloatArray())

    private val unitTriangle = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)

    private fun entry(bytes: ByteArray, name: String): String {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == name) return zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        throw AssertionError("no entry '$name' in ${entryNames(bytes)}")
    }

    private fun entryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                names += e.name
                e = zip.nextEntry
            }
        }
        return names
    }

    @Test
    fun `writes exactly the four parts the engine reads, content types first`() {
        val bytes = ThreeMf.writeToBytes(listOf(ProjectObject(1, "a", meshOf(unitTriangle))))
        assertEquals(
            listOf(
                ThreeMf.CONTENT_TYPES_FILE,
                ThreeMf.RELATIONSHIPS_FILE,
                ThreeMf.MODEL_FILE,
                ThreeMf.MODEL_CONFIG_FILE,
            ),
            entryNames(bytes),
        )
    }

    @Test
    fun `relationships point at the model part the way the importer looks for it`() {
        val rels = entry(
            ThreeMf.writeToBytes(listOf(ProjectObject(1, "a", meshOf(unitTriangle)))),
            ThreeMf.RELATIONSHIPS_FILE,
        )
        assertTrue(rels.contains("Target=\"/3D/3dmodel.model\""), rels)
        assertTrue(rels.contains("http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"), rels)
    }

    @Test
    fun `one object and one build item per model, ids echoed into the settings file`() {
        val bytes = ThreeMf.writeToBytes(
            listOf(
                ProjectObject(1, "left", meshOf(unitTriangle)),
                ProjectObject(2, "right", meshOf(unitTriangle)),
            ),
        )
        val model = entry(bytes, ThreeMf.MODEL_FILE)
        assertEquals(2, Regex("<object id=\"\\d+\" type=\"model\">").findAll(model).count(), model)
        assertEquals(2, Regex("<item objectid=\"\\d+\"").findAll(model).count(), model)

        val settings = entry(bytes, ThreeMf.MODEL_CONFIG_FILE)
        assertTrue(settings.contains("<object id=\"1\">"), settings)
        assertTrue(settings.contains("<object id=\"2\">"), settings)
    }

    @Test
    fun `every object in the settings file carries a part, because one without it loses its volume`() {
        val bytes = ThreeMf.writeToBytes(
            listOf(
                ProjectObject(1, "with-settings", meshOf(unitTriangle), mapOf("layer_height" to "0.2")),
                ProjectObject(2, "without-settings", meshOf(unitTriangle)),
            ),
        )
        val settings = entry(bytes, ThreeMf.MODEL_CONFIG_FILE)
        assertEquals(2, Regex("<part id=\"1\" subtype=\"normal_part\">").findAll(settings).count(), settings)
        // One part per object: count parts and objects and require they match.
        assertEquals(
            Regex("<object id=\"\\d+\">").findAll(settings).count(),
            Regex("<part id=").findAll(settings).count(),
            settings,
        )
    }

    @Test
    fun `per-object print settings are written as metadata keys the importer deserialises`() {
        val bytes = ThreeMf.writeToBytes(
            listOf(
                ProjectObject(
                    1, "body", meshOf(unitTriangle),
                    mapOf("layer_height" to "0.1", "sparse_infill_density" to "15%", "enable_support" to "1"),
                ),
            ),
        )
        val settings = entry(bytes, ThreeMf.MODEL_CONFIG_FILE)
        assertTrue(settings.contains("<metadata key=\"layer_height\" value=\"0.1\"/>"), settings)
        assertTrue(settings.contains("<metadata key=\"sparse_infill_density\" value=\"15%\"/>"), settings)
        assertTrue(settings.contains("<metadata key=\"enable_support\" value=\"1\"/>"), settings)
        // Sorted, so the file is byte-identical for identical input (no reshuffling between runs).
        assertTrue(
            settings.indexOf("enable_support") < settings.indexOf("layer_height"),
            settings,
        )
    }

    @Test
    fun `a one-triangle object writes the exact xml, indices and all`() {
        val model = ThreeMf.modelXml(listOf(ProjectObject(1, "cube", meshOf(unitTriangle))))
        val expected =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<model unit=\"millimeter\" xml:lang=\"en-US\" " +
                "xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n" +
                " <metadata name=\"Application\">Lumen</metadata>\n" +
                " <resources>\n" +
                "  <object id=\"1\" type=\"model\">\n" +
                "   <mesh>\n" +
                "    <vertices>\n" +
                "     <vertex x=\"0.0000\" y=\"0.0000\" z=\"0.0000\"/>\n" +
                "     <vertex x=\"1.0000\" y=\"0.0000\" z=\"0.0000\"/>\n" +
                "     <vertex x=\"0.0000\" y=\"1.0000\" z=\"0.0000\"/>\n" +
                "    </vertices>\n" +
                "    <triangles>\n" +
                "     <triangle v1=\"0\" v2=\"1\" v3=\"2\"/>\n" +
                "    </triangles>\n" +
                "   </mesh>\n" +
                "  </object>\n" +
                " </resources>\n" +
                " <build>\n" +
                "  <item objectid=\"1\" transform=\"1 0 0 0 1 0 0 0 1 0 0 0\"/>\n" +
                " </build>\n" +
                "</model>"
        assertEquals(expected, model)
    }

    @Test
    fun `the settings file for one object names it and carries an identity part matrix`() {
        val settings = ThreeMf.modelSettingsXml(listOf(ProjectObject(1, "F1 body", meshOf(unitTriangle))))
        val expected =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<config>\n" +
                "  <object id=\"1\">\n" +
                "    <metadata key=\"name\" value=\"F1 body\"/>\n" +
                "    <part id=\"1\" subtype=\"normal_part\">\n" +
                "      <metadata key=\"matrix\" value=\"1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1\"/>\n" +
                "    </part>\n" +
                "  </object>\n" +
                "</config>"
        assertEquals(expected, settings)
    }

    @Test
    fun `geometry is one vertex per float-triple and unshared triangle indices`() {
        val mesh = meshOf(
            unitTriangle,
            floatArrayOf(2f, 2f, 2f, 3f, 2f, 2f, 2f, 3f, 2f),
            floatArrayOf(4f, 4f, 4f, 5f, 4f, 4f, 4f, 5f, 4f),
        )
        val model = ThreeMf.modelXml(listOf(ProjectObject(1, "three", mesh)))
        assertEquals(3, mesh.triangleCount)
        assertEquals(9, Regex("<vertex ").findAll(model).count())
        assertEquals(3, Regex("<triangle ").findAll(model).count())
        assertTrue(model.contains("<triangle v1=\"6\" v2=\"7\" v3=\"8\"/>"), model)
    }

    @Test
    fun `coordinates keep a dot under a comma-decimal locale`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val model = ThreeMf.modelXml(listOf(ProjectObject(1, "g", meshOf(floatArrayOf(1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)))))
            assertTrue(model.contains("x=\"1.5000\""), model)
            assertFalse(model.contains("1,5000"), model)
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `names and settings are xml-escaped so a stray ampersand cannot corrupt the file`() {
        val settings = ThreeMf.modelSettingsXml(
            listOf(ProjectObject(1, "left & <right>", meshOf(unitTriangle), mapOf("name" to "\"quoted\""))),
        )
        assertTrue(settings.contains("value=\"left &amp; &lt;right&gt;\""), settings)
        assertTrue(settings.contains("value=\"&quot;quoted&quot;\""), settings)
        assertFalse(settings.contains("left & <right>"), settings)
    }

    @Test
    fun `duplicate object ids are refused here rather than by the engine`() {
        val e = assertFailsWith<IllegalArgumentException> {
            ThreeMf.writeToBytes(
                listOf(
                    ProjectObject(1, "a", meshOf(unitTriangle)),
                    ProjectObject(1, "b", meshOf(unitTriangle)),
                ),
            )
        }
        assertTrue(e.message!!.contains("unique"), e.message)
    }

    @Test
    fun `an empty project is refused`() {
        assertFailsWith<IllegalArgumentException> { ThreeMf.writeToBytes(emptyList()) }
    }

    /**
     * Writes a real project to `build/three-mf/sample.3mf` so the output can be checked by something
     * that is NOT this test suite — `scripts/three_mf_validate.py`, which unzips it with Python's
     * zipfile and parses the XML with ElementTree. A round-trip through our own reader would share any
     * misunderstanding we have of the format, which is exactly how a wrong file ships.
     */
    @Test
    fun `writes a sample project for the independent python validator`() {
        val dir = java.io.File("build/three-mf")
        dir.mkdirs()
        java.io.File(dir, "sample.3mf").writeBytes(
            ThreeMf.writeToBytes(
                listOf(
                    ProjectObject(1, "left & low", meshOf(unitTriangle), mapOf("layer_height" to "0.2")),
                    ProjectObject(
                        2, "right", meshOf(unitTriangle, floatArrayOf(5f, 5f, 0f, 6f, 5f, 0f, 5f, 6f, 0f)),
                        mapOf("layer_height" to "0.1", "sparse_infill_density" to "15%"),
                    ),
                ),
            ),
        )
        assertTrue(java.io.File(dir, "sample.3mf").length() > 0)
    }
}
