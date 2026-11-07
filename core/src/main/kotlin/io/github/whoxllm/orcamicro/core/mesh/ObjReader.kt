package io.github.whoxllm.orcamicro.core.mesh

/**
 * Minimal **Wavefront OBJ** reader (B): reads `v` vertices + `f` faces, fan-triangulating polygons.
 * Face tokens may be `v`, `v/vt`, `v//vn`, or `v/vt/vn`; indices are 1-based (negative = relative).
 * Only geometry is used — STL-style flat normals are recomputed downstream.
 */
object ObjReader {

    fun read(text: String): Mesh {
        val verts = ArrayList<Float>()
        val out = ArrayList<Float>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("v ") -> {
                    val p = line.substring(2).trim().split(WHITESPACE)
                    verts += p[0].toFloat(); verts += p[1].toFloat(); verts += p[2].toFloat()
                }
                line.startsWith("f ") -> {
                    val p = line.substring(2).trim().split(WHITESPACE)
                    val idx = p.map { resolveIndex(it.substringBefore('/'), verts.size / 3) }
                    for (i in 1 until idx.size - 1) addTri(out, verts, idx[0], idx[i], idx[i + 1])
                }
            }
        }
        if (out.isEmpty()) throw StlFormatException("The OBJ file has no faces")
        return Mesh(out.toFloatArray())
    }

    /** 1-based (positive) or relative (negative) OBJ index → 0-based vertex index. */
    private fun resolveIndex(token: String, vertexCount: Int): Int {
        val i = token.toInt()
        return if (i > 0) i - 1 else vertexCount + i
    }

    private fun addTri(out: ArrayList<Float>, verts: ArrayList<Float>, a: Int, b: Int, c: Int) {
        for (vi in intArrayOf(a, b, c)) {
            val j = vi * 3
            out += verts[j]; out += verts[j + 1]; out += verts[j + 2]
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
