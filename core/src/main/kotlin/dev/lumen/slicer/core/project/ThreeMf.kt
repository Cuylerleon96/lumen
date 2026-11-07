// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.lumen.slicer.core.project

import dev.lumen.slicer.core.mesh.Mesh
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One model on the plate, as a 3MF project carries it.
 *
 * [mesh] must already be in PLATE coordinates — the same contract the STL path uses, where the app bakes
 * placement into the vertices. Baking sidesteps the 3MF transform convention (and its axis-order traps)
 * entirely, which is why the build item written here is the identity transform.
 */
data class ProjectObject(
    val id: Int,
    val name: String,
    val mesh: Mesh,
    /**
     * Per-OBJECT print settings: real OrcaSlicer process keys, e.g. `layer_height` -> `"0.2"`,
     * `sparse_infill_density` -> `"15%"`, `enable_support` -> `"1"`. The engine applies each of these to
     * that object alone, leaving every other object on the process preset.
     */
    val settings: Map<String, String> = emptyMap(),
)

/**
 * Writes an OrcaSlicer-compatible **3MF project**: one object per model, each free to carry its own
 * print settings.
 *
 * Every format fact here was read out of the vendored engine, not guessed —
 * `libslic3r/Format/bbs_3mf.cpp`, the `_BBS_3MF_Exporter` writer against the `_BBS_3MF_Importer` reader:
 *
 * - The parts are `[Content_Types].xml`, `_rels/.rels`, `3D/3dmodel.model` and
 *   `Metadata/model_settings.config` (`BBS_MODEL_CONFIG_FILE`).
 * - The importer applies an object's `<metadata key="…" value="…"/>` entries with
 *   `model_object->config.set_deserialize(key, value, …)`, so **any process key can be overridden per
 *   object**; only `name` and `module` are special-cased — everything else becomes that object's config.
 * - The version metadata (`BambuStudio:3mfVersion`) is OPTIONAL: the importer's version *check* is
 *   commented out upstream, so a file without it loads with `m_version = 0`.
 * - **An `<object>` in `model_settings.config` without a `<part>` child ends up with an EMPTY volume
 *   list.** The "treat the whole geometry as one volume" fallback only runs when there is NO metadata
 *   entry for the object at all, so an object with settings but no part can silently become an object
 *   with nothing to print. Every object written here therefore carries exactly one
 *   `<part id="1" subtype="normal_part">` with an identity matrix.
 */
object ThreeMf {

    const val CONTENT_TYPES_FILE = "[Content_Types].xml"
    const val RELATIONSHIPS_FILE = "_rels/.rels"
    const val MODEL_FILE = "3D/3dmodel.model"
    const val MODEL_CONFIG_FILE = "Metadata/model_settings.config"

    /** 3MF is a plain zip of XML parts; the core namespace is the 2015/02 core spec. */
    private const val MODEL_NS = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02"

    private const val IDENTITY_MATRIX = "1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1"

    /** Build items use the 3MF 3x4 row-major transform; identity because the vertices are already placed. */
    private const val IDENTITY_ITEM = "1 0 0 0 1 0 0 0 1 0 0 0"

    fun writeToBytes(objects: List<ProjectObject>): ByteArray {
        val out = java.io.ByteArrayOutputStream(1 shl 16)
        write(objects, out)
        return out.toByteArray()
    }

    fun write(objects: List<ProjectObject>, out: OutputStream) {
        require(objects.isNotEmpty()) { "a 3MF project needs at least one object" }
        require(objects.map { it.id }.toSet().size == objects.size) {
            // The importer rejects a duplicated object id outright ("Duplicated object id: N in
            // model_settings.config"), so catch it here where the message names the real problem.
            "object ids must be unique: ${objects.map { it.id }}"
        }
        require(objects.all { it.id >= 1 }) { "3MF object ids are 1-based" }

        ZipOutputStream(out).use { zip ->
            // [Content_Types].xml first: it is the OPC manifest readers look for before anything else.
            zip.putNextEntry(ZipEntry(CONTENT_TYPES_FILE))
            zip.write(contentTypes().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(RELATIONSHIPS_FILE))
            zip.write(relationships().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(MODEL_FILE))
            zip.write(modelXml(objects).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(MODEL_CONFIG_FILE))
            zip.write(modelSettingsXml(objects).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    internal fun contentTypes(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n")
        append(" <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n")
        append(" <Default Extension=\"model\" ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/>\n")
        append(" <Default Extension=\"png\" ContentType=\"image/png\"/>\n")
        append(" <Default Extension=\"gcode\" ContentType=\"text/x.gcode\"/>\n")
        append("</Types>")
    }

    internal fun relationships(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n")
        append(" <Relationship Target=\"/$MODEL_FILE\" Id=\"rel0\" ")
        append("Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/>\n")
        append("</Relationships>")
    }

    internal fun modelXml(objects: List<ProjectObject>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"$MODEL_NS\">\n")
        append(" <metadata name=\"Application\">Lumen</metadata>\n")
        append(" <resources>\n")
        for (o in objects) {
            append("  <object id=\"${o.id}\" type=\"model\">\n")
            append("   <mesh>\n")
            val p = o.mesh.positions
            append("    <vertices>\n")
            var i = 0
            while (i < p.size) {
                append("     <vertex x=\"${num(p[i])}\" y=\"${num(p[i + 1])}\" z=\"${num(p[i + 2])}\"/>\n")
                i += 3
            }
            append("    </vertices>\n")
            append("    <triangles>\n")
            // Bound by the mesh's own triangle count, NOT positions.size/3: the mesh holds NINE floats
            // per triangle, so dividing by three claims three times as many triangles and emits vertex
            // indices past the end of the vertex list — a structurally invalid file.
            val triangles = o.mesh.triangleCount
            for (t in 0 until triangles) {
                append("     <triangle v1=\"${t * 3}\" v2=\"${t * 3 + 1}\" v3=\"${t * 3 + 2}\"/>\n")
            }
            append("    </triangles>\n")
            append("   </mesh>\n")
            append("  </object>\n")
        }
        append(" </resources>\n")
        append(" <build>\n")
        for (o in objects) {
            append("  <item objectid=\"${o.id}\" transform=\"$IDENTITY_ITEM\"/>\n")
        }
        append(" </build>\n")
        append("</model>")
    }

    internal fun modelSettingsXml(objects: List<ProjectObject>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<config>\n")
        for (o in objects) {
            append("  <object id=\"${o.id}\">\n")
            append("    <metadata key=\"name\" value=\"${escape(o.name)}\"/>\n")
            // These are the per-object PRINT settings: the importer feeds each key to
            // DynamicPrintConfig::set_deserialize on this object's config.
            for (key in o.settings.keys.sorted()) {
                append("    <metadata key=\"${escape(key)}\" value=\"${escape(o.settings.getValue(key))}\"/>\n")
            }
            // Mandatory: without a part the object's volume list comes out empty (see the class note).
            append("    <part id=\"1\" subtype=\"normal_part\">\n")
            append("      <metadata key=\"matrix\" value=\"$IDENTITY_MATRIX\"/>\n")
            append("    </part>\n")
            append("  </object>\n")
        }
        append("</config>")
    }

    /**
     * Locale.ROOT on purpose: `String.format` without it renders decimals with the default locale's
     * separator, so a device set to e.g. German would emit `x="1,5000"` — a coordinate split in two,
     * producing a corrupt 3MF that only fails on some phones.
     */
    private fun num(v: Float): String = String.format(Locale.ROOT, "%.4f", v)

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}
