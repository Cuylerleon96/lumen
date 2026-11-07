package dev.lumen.slicer.render

import android.opengl.GLES20
import dev.lumen.slicer.core.gcode.Feature
import dev.lumen.slicer.core.gcode.ToolpathLayer
import dev.lumen.slicer.core.gcode.Toolpaths
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * What the preview shows: layers up to [layer], optionally just that one, plus travels and feature
 * filters. [lineWidthMm] is the extrusion width the toolpaths are drawn at (from the print settings),
 * so the preview reads like the real print rather than hairlines.
 */
data class PreviewView(
    val layer: Int,
    val singleLayer: Boolean = false,
    val showTravels: Boolean = false,
    val hiddenFeatures: Set<Feature> = emptySet(),
    val lineWidthMm: Float = 0.42f,
)

/**
 * Draws sliced toolpaths as coloured **quads of the configured extrusion width** (world units), one
 * colour per feature. Each extrusion segment becomes a 2-triangle ribbon; travels stay thin lines.
 * All extrusions go into one vertex buffer in layer order, so "layers up to N" is a single draw call.
 */
class ToolpathRenderer : SceneRenderer() {
    @Volatile private var toolpaths: Toolpaths? = null
    @Volatile private var view = PreviewView(0)

    private var extrusions: GlBuffer? = null
    private var travels: GlBuffer? = null
    private var layerStarts = IntArray(1)
    private var travelStarts = IntArray(1)
    private var builtFor: Triple<Toolpaths, Set<Feature>, Float>? = null

    fun setToolpaths(toolpaths: Toolpaths?) { this.toolpaths = toolpaths }
    fun setView(view: PreviewView) { this.view = view }

    override fun onCreateResources() {
        extrusions = null
        travels = null
        builtFor = null
    }

    override fun drawScene() {
        val paths = toolpaths ?: return
        val current = view
        val key = Triple(paths, current.hiddenFeatures, current.lineWidthMm)
        if (builtFor != key) build(paths, current.hiddenFeatures, current.lineWidthMm)
        val layers = paths.layers.size
        if (layers == 0) return
        val layer = current.layer.coerceIn(0, layers - 1)
        val buffer = extrusions ?: return

        if (!current.singleLayer && layer > 0) {
            drawTriangles(buffer, 0, layerStarts[layer], 0.55f)
        }
        // The current layer goes on top of everything below it.
        drawTriangles(buffer, layerStarts[layer], layerStarts[layer + 1] - layerStarts[layer], 1f)
        if (current.showTravels) {
            travels?.let { drawTravels(it, travelStarts[layer], travelStarts[layer + 1] - travelStarts[layer]) }
        }
    }

    private fun build(paths: Toolpaths, hidden: Set<Feature>, lineWidthMm: Float) {
        extrusions?.delete()
        travels?.delete()
        val layers = paths.layers
        layerStarts = IntArray(layers.size + 1)
        travelStarts = IntArray(layers.size + 1)
        var visibleSeg = 0
        var travelCount = 0
        for ((i, layer) in layers.withIndex()) {
            layerStarts[i] = visibleSeg * VERTS_PER_SEGMENT
            travelStarts[i] = travelCount * 2
            visibleSeg += if (hidden.isEmpty()) layer.extrusionCount else layer.features.count { Feature.entries[it.toInt()] !in hidden }
            travelCount += layer.travelCount
        }
        layerStarts[layers.size] = visibleSeg * VERTS_PER_SEGMENT
        travelStarts[layers.size] = travelCount * 2

        val half = (lineWidthMm / 2f).coerceAtLeast(0.02f)
        // 3 floats of position + 4 bytes of colour per vertex.
        val data = directBuffer(maxOf(1, visibleSeg * VERTS_PER_SEGMENT) * VERTEX_BYTES)
        val colors = Feature.entries.map { packColor(it.rgb) }
        for (layer in layers) appendExtrusions(data, layer, hidden, colors, half)
        extrusions = GlBuffer(data, visibleSeg * VERTS_PER_SEGMENT)

        val travelData = directBuffer(maxOf(1, travelCount * 2) * 12)
        val travelFloats = travelData.asFloatBuffer()
        for (layer in layers) travelFloats.put(layer.travels)
        travels = GlBuffer(travelData, travelCount * 2)
        builtFor = Triple(paths, hidden, lineWidthMm)
    }

    private fun appendExtrusions(data: ByteBuffer, layer: ToolpathLayer, hidden: Set<Feature>, colors: List<Int>, half: Float) {
        val segments = layer.extrusions
        for (s in 0 until layer.extrusionCount) {
            val feature = layer.features[s].toInt()
            if (hidden.isNotEmpty() && Feature.entries[feature] in hidden) continue
            val color = colors[feature]
            val o = s * ToolpathLayer.FLOATS_PER_SEGMENT
            val x0 = segments[o]; val y0 = segments[o + 1]; val z = segments[o + 2]
            val x1 = segments[o + 3]; val y1 = segments[o + 4]
            var dx = x1 - x0; var dy = y1 - y0
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-6f) { dx = 1f; dy = 0f } else { dx /= len; dy /= len }
            val nx = -dy * half; val ny = dx * half
            // ribbon: A=p0+n, B=p0-n, C=p1-n, D=p1+n  → triangles (A,B,C) + (A,C,D)
            vertex(data, x0 + nx, y0 + ny, z, color)
            vertex(data, x0 - nx, y0 - ny, z, color)
            vertex(data, x1 - nx, y1 - ny, z, color)
            vertex(data, x0 + nx, y0 + ny, z, color)
            vertex(data, x1 - nx, y1 - ny, z, color)
            vertex(data, x1 + nx, y1 + ny, z, color)
        }
    }

    private fun vertex(data: ByteBuffer, x: Float, y: Float, z: Float, color: Int) {
        data.putFloat(x).putFloat(y).putFloat(z).putInt(color)
    }

    /** RGBA bytes in memory order, as a native-order int. */
    private fun packColor(rgb: Int): Int {
        val bytes = directBuffer(4)
        bytes.put(((rgb shr 16) and 0xff).toByte()).put(((rgb shr 8) and 0xff).toByte()).put((rgb and 0xff).toByte()).put(0xff.toByte())
        return bytes.getInt(0)
    }

    private fun drawTriangles(buffer: GlBuffer, first: Int, count: Int, brightness: Float) {
        if (count <= 0) return
        lineProgram.use()
        GLES20.glUniformMatrix4fv(lineProgram.uniform("uMvp"), 1, false, mvp, 0)
        GLES20.glUniform1f(lineProgram.uniform("uUseVertexColor"), 1f)
        GLES20.glUniform1f(lineProgram.uniform("uBrightness"), brightness)
        GLES20.glUniform4f(lineProgram.uniform("uColor"), 1f, 1f, 1f, 1f)
        val position = lineProgram.attribute("aPosition")
        val color = lineProgram.attribute("aColor")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.id)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(color)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, VERTEX_BYTES, 0)
        GLES20.glVertexAttribPointer(color, 4, GLES20.GL_UNSIGNED_BYTE, true, VERTEX_BYTES, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, first, count)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(color)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawTravels(buffer: GlBuffer, first: Int, count: Int) {
        if (count <= 0) return
        lineProgram.use()
        GLES20.glLineWidth(1f)
        GLES20.glUniformMatrix4fv(lineProgram.uniform("uMvp"), 1, false, mvp, 0)
        GLES20.glUniform1f(lineProgram.uniform("uUseVertexColor"), 0f)
        GLES20.glUniform1f(lineProgram.uniform("uBrightness"), 1f)
        GLES20.glUniform4fv(lineProgram.uniform("uColor"), 1, TRAVEL_COLOR, 0)
        val position = lineProgram.attribute("aPosition")
        val color = lineProgram.attribute("aColor")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.id)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(color)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glDrawArrays(GLES20.GL_LINES, first, count)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private companion object {
        const val VERTEX_BYTES = 16
        const val VERTS_PER_SEGMENT = 6
        val TRAVEL_COLOR = rgba(0x38489B)
    }
}
