package dev.lumen.slicer.render

import android.opengl.GLES20
import dev.lumen.slicer.core.mesh.Mesh

/**
 * The bed with the placed model on it, plus the **selection** and **live drag** state.
 *
 * The mesh is uploaded once per placement; a drag is applied as a uniform offset/rotation rather than
 * by rebuilding the mesh, so a finger never waits on a re-upload of a multi-hundred-thousand-triangle
 * buffer. The caller commits the transform when the finger lifts, which produces one new placed mesh.
 */
class PlateRenderer : SceneRenderer() {
    private lateinit var meshProgram: GlProgram

    @Volatile private var mesh: Mesh? = null
    @Volatile private var fits = true
    @Volatile private var selected = false
    @Volatile private var liveOffset = FloatArray(3)
    @Volatile private var liveAngle = 0f
    @Volatile private var pivot = FloatArray(3)

    private var meshBuffer: GlBuffer? = null
    private var uploaded: Mesh? = null
    private var outline: GlBuffer? = null
    private var outlineBuiltFor: Mesh? = null

    /** The model as placed on the bed; [selected] tints it and draws its footprint. */
    fun setModel(mesh: Mesh?, fits: Boolean, selected: Boolean) {
        this.mesh = mesh
        this.fits = fits
        this.selected = selected
    }

    /** Live, uncommitted drag transform: a bed-plane offset in mm and a rotation about world Z. */
    fun setLive(offsetXMm: Float, offsetYMm: Float, angleDeg: Float) {
        liveOffset = floatArrayOf(offsetXMm, offsetYMm, 0f)
        liveAngle = angleDeg
    }

    override fun onCreateResources() {
        meshProgram = GlProgram(Shaders.MESH_VERTEX, Shaders.MESH_FRAGMENT)
        meshBuffer = null
        uploaded = null
        outline = null
        outlineBuiltFor = null
    }

    override fun drawScene() {
        val current = mesh
        if (current !== uploaded) {
            meshBuffer?.delete()
            meshBuffer = current?.let { m -> val (buf, count) = meshVertices(m); GlBuffer(buf, count) }
            uploaded = current
            if (current != null) {
                val b = current.bounds()
                // Rotate about the footprint's centre: a turntable on the plate, which is what the
                // committed placement's rotationZ does too.
                pivot = floatArrayOf((b.min.x + b.max.x) / 2f, (b.min.y + b.max.y) / 2f, 0f)
            }
        }
        val buffer = meshBuffer ?: return
        meshProgram.use()
        GLES20.glUniformMatrix4fv(meshProgram.uniform("uMvp"), 1, false, mvp, 0)
        GLES20.glUniform4fv(
            meshProgram.uniform("uColor"), 1,
            when {
                !fits -> OUTSIDE_COLOR
                selected -> SELECTED_COLOR
                else -> MODEL_COLOR
            }, 0,
        )
        GLES20.glUniform3f(meshProgram.uniform("uOffset"), liveOffset[0], liveOffset[1], liveOffset[2])
        GLES20.glUniform3f(meshProgram.uniform("uPivot"), pivot[0], pivot[1], pivot[2])
        GLES20.glUniform1f(meshProgram.uniform("uAngle"), liveAngle)
        val position = meshProgram.attribute("aPosition")
        val normal = meshProgram.attribute("aNormal")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.id)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, buffer.count)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(normal)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        if (selected) current?.let { drawFootprint(it) }
    }

    /**
     * A rectangle round the model's footprint, in the selection colour. It exists because a thin or
     * small model is hard to see as "selected" from its tint alone — and because it is the thing the
     * finger is dragging, so it has to follow the finger exactly (same uniforms, no re-upload).
     */
    private fun drawFootprint(mesh: Mesh) {
        if (outlineBuiltFor !== mesh) {
            outline?.delete()
            val b = mesh.bounds()
            val z = 0.12f
            val corners = floatArrayOf(
                b.min.x, b.min.y, z, b.max.x, b.min.y, z,
                b.max.x, b.min.y, z, b.max.x, b.max.y, z,
                b.max.x, b.max.y, z, b.min.x, b.max.y, z,
                b.min.x, b.max.y, z, b.min.x, b.min.y, z,
            )
            outline = GlBuffer(floatBytes(corners), 8)
            outlineBuiltFor = mesh
        }
        outline?.let { drawLines(it, SELECTED_COLOR, 3f, liveOffset, pivot, liveAngle) }
    }

    private companion object {
        val MODEL_COLOR = rgba(0x2BB59B)
        val OUTSIDE_COLOR = rgba(0xE5484D)
        /** Selected: a lighter, brighter teal so it reads as "picked up" without hiding the shape. */
        val SELECTED_COLOR = rgba(0x6FE3C8)
    }
}
