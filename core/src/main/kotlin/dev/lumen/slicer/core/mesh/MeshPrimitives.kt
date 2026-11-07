package dev.lumen.slicer.core.mesh

import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural mesh primitives. [cylinder] is the tool used by drain-hole placement (subtract a
 * cylinder through the shell) and by the calibration models.
 */
object MeshPrimitives {

    /**
     * A capped cylinder, axis along **Z**, centred at ([cx],[cy]) with its base at [cz].
     * @param radiusMm radius
     * @param heightMm height (Z)
     * @param segments radial resolution (≥3)
     */
    fun cylinder(radiusMm: Float, heightMm: Float, segments: Int = 48, cx: Float = 0f, cy: Float = 0f, cz: Float = 0f): Mesh {
        require(segments >= 3) { "segments must be ≥ 3" }
        val out = ArrayList<Float>()
        val z0 = cz
        val z1 = cz + heightMm
        fun ring(z: Float) = FloatArray(segments * 2) { k ->
            val i = k / 2
            val a = (2.0 * Math.PI * i / segments)
            if (k % 2 == 0) cx + (radiusMm * cos(a)).toFloat() else cy + (radiusMm * sin(a)).toFloat()
        }
        val bottom = ring(z0)
        val top = ring(z1)
        fun emit(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, dx: Float, dy: Float, dz: Float) {
            out += ax; out += ay; out += az
            out += bx; out += by; out += bz
            out += dx; out += dy; out += dz
        }
        for (i in 0 until segments) {
            val j = (i + 1) % segments
            val bx = bottom[i * 2]; val by = bottom[i * 2 + 1]
            val bxj = bottom[j * 2]; val byj = bottom[j * 2 + 1]
            val tx = top[i * 2]; val ty = top[i * 2 + 1]
            val txj = top[j * 2]; val tyj = top[j * 2 + 1]
            // side (two triangles, outward winding)
            emit(bx, by, z0, bxj, byj, z0, txj, tyj, z1)
            emit(bx, by, z0, txj, tyj, z1, tx, ty, z1)
            // bottom cap (fan to centre), facing down
            emit(cx, cy, z0, bxj, byj, z0, bx, by, z0)
            // top cap (fan to centre), facing up
            emit(cx, cy, z1, tx, ty, z1, txj, tyj, z1)
        }
        return Mesh(out.toFloatArray())
    }

    /**
     * An axis-aligned box centred at ([cx],[cy],[cz]) — outward-wound, 12 triangles.
     * The CSG tests' reference solid.
     */
    fun box(sizeXmm: Float, sizeYmm: Float, sizeZmm: Float, cx: Float = 0f, cy: Float = 0f, cz: Float = 0f): Mesh {
        val x0 = cx - sizeXmm / 2f; val x1 = cx + sizeXmm / 2f
        val y0 = cy - sizeYmm / 2f; val y1 = cy + sizeYmm / 2f
        val z0 = cz - sizeZmm / 2f; val z1 = cz + sizeZmm / 2f
        val out = ArrayList<Float>(12 * 9)
        fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, dx: Float, dy: Float, dz: Float) {
            out += ax; out += ay; out += az
            out += bx; out += by; out += bz
            out += dx; out += dy; out += dz
        }
        // bottom (-Z), top (+Z)
        tri(x0, y0, z0, x0, y1, z0, x1, y1, z0); tri(x0, y0, z0, x1, y1, z0, x1, y0, z0)
        tri(x0, y0, z1, x1, y0, z1, x1, y1, z1); tri(x0, y0, z1, x1, y1, z1, x0, y1, z1)
        // front (-Y), back (+Y)
        tri(x0, y0, z0, x1, y0, z0, x1, y0, z1); tri(x0, y0, z0, x1, y0, z1, x0, y0, z1)
        tri(x0, y1, z0, x0, y1, z1, x1, y1, z1); tri(x0, y1, z0, x1, y1, z1, x1, y1, z0)
        // left (-X), right (+X)
        tri(x0, y0, z0, x0, y0, z1, x0, y1, z1); tri(x0, y0, z0, x0, y1, z1, x0, y1, z0)
        tri(x1, y0, z0, x1, y1, z0, x1, y1, z1); tri(x1, y0, z0, x1, y1, z1, x1, y0, z1)
        return Mesh(out.toFloatArray())
    }

    /**
     * A capped cylinder of [radiusMm] / [lengthMm] whose axis runs from [origin] along
     * [direction] (it need not be unit — it is normalised). This is the drill bit.
     */
    fun cylinderAlong(
        radiusMm: Float,
        lengthMm: Float,
        origin: Vec3,
        direction: Vec3,
        segments: Int = 48,
    ): Mesh {
        val n = direction.normalized()
        val ref = if (kotlin.math.abs(n.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(1f, 0f, 0f)
        val u = ref.cross(n).normalized()
        val v = n.cross(u)
        val local = cylinder(radiusMm, lengthMm, segments) // along +Z from the origin
        return local.map { x, y, z, out ->
            out[0] = origin.x + u.x * x + v.x * y + n.x * z
            out[1] = origin.y + u.y * x + v.y * y + n.y * z
            out[2] = origin.z + u.z * x + v.z * y + n.z * z
        }
    }
}
