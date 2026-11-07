package io.github.whoxllm.orcamicro.core.mesh

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Minimal **3MF** reader (B): a 3MF is a ZIP containing `3D/3dmodel.model` (XML) with a `<mesh>` of
 * `<vertices>` (`<vertex x y z/>`) and `<triangles>` (`<triangle v1 v2 v3/>`). Reads the first mesh's
 * geometry; ignores build transforms + materials (STL-style flat normals are recomputed downstream).
 */
object ThreeMfReader {

    fun read(bytes: ByteArray): Mesh {
        val model = extractModelXml(bytes) ?: throw StlFormatException("3MF has no 3D/3dmodel.model")
        return parse(model)
    }

    private fun extractModelXml(bytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.equals("3D/3dmodel.model", ignoreCase = true) || e.name.endsWith(".model", ignoreCase = true)) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        return null
    }

    fun parse(xml: String): Mesh {
        val verts = ArrayList<Float>()
        val out = ArrayList<Float>()
        // vertices
        for (m in VERTEX.findAll(xml)) {
            verts += m.groupValues[1].toFloat()
            verts += m.groupValues[2].toFloat()
            verts += m.groupValues[3].toFloat()
        }
        // triangles (index into the vertices, 0-based in 3MF)
        for (m in TRIANGLE.findAll(xml)) {
            addTri(out, verts, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        if (out.isEmpty()) throw StlFormatException("The 3MF has no triangles")
        return Mesh(out.toFloatArray())
    }

    private fun addTri(out: ArrayList<Float>, verts: ArrayList<Float>, a: Int, b: Int, c: Int) {
        for (vi in intArrayOf(a, b, c)) {
            val j = vi * 3
            out += verts[j]; out += verts[j + 1]; out += verts[j + 2]
        }
    }

    private val VERTEX = Regex("""<vertex\s+x="([-\d.eE+]+)"\s+y="([-\d.eE+]+)"\s+z="([-\d.eE+]+)"""")
    private val TRIANGLE = Regex("""<triangle\s+v1="(\d+)"\s+v2="(\d+)"\s+v3="(\d+)"""")
}
