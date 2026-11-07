package dev.lumen.slicer.core.mesh

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Screen ⇄ world picking for the plate view: the arithmetic that lets a finger **tap to select** a
 * model and **drag to move** it in the bed plane.
 *
 * It lives in `:core` (no `android.opengl`, so it is unit-testable) and describes the camera
 * analytically — an eye point, a target, a vertical field of view and a viewport. That mirrors exactly
 * what the GL camera builds with `setLookAtM` + `perspectiveM`, including the **world +Z up** vector:
 * on a printer Z is up, so the model moves in the XY plane.
 */
object Picking {

    /** A world-space ray. [dx]/[dy]/[dz] are normalized by [rayForPixel]. */
    class Ray(val ox: Float, val oy: Float, val oz: Float, val dx: Float, val dy: Float, val dz: Float)

    /**
     * The ray through pixel ([sx], [sy]) — **screen y grows downward**, like the touch events.
     *
     * @param fovYDeg the vertical field of view used to build the projection (40° in this app).
     */
    fun rayForPixel(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        targetX: Float, targetY: Float, targetZ: Float,
        fovYDeg: Float,
        width: Int, height: Int,
        sx: Float, sy: Float,
    ): Ray {
        var fx = targetX - eyeX; var fy = targetY - eyeY; var fz = targetZ - eyeZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-6f)
        fx /= fl; fy /= fl; fz /= fl

        // right = forward × worldUp, with worldUp = (0,0,1) — so (fy, -fx, 0).
        var rx = fy; var ry = -fx; var rz = 0f
        var rl = sqrt(rx * rx + ry * ry + rz * rz)
        if (rl < 1e-6f) {
            // Looking straight down (or up) the cross product degenerates. The view pitch is clamped
            // short of 90° precisely so this cannot happen; fall back to world axes rather than
            // returning a NaN ray.
            rx = 1f; ry = 0f; rz = 0f; rl = 1f
        }
        rx /= rl; ry /= rl; rz /= rl

        // up = right × forward
        val ux = ry * fz - rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy - ry * fx

        val tanHalf = tan(Math.toRadians(fovYDeg / 2.0)).toFloat()
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        val ndcX = (2f * sx / width - 1f) * aspect
        val ndcY = 1f - 2f * sy / height

        var dx = fx + rx * ndcX * tanHalf + ux * ndcY * tanHalf
        var dy = fy + ry * ndcX * tanHalf + uy * ndcY * tanHalf
        var dz = fz + rz * ndcX * tanHalf + uz * ndcY * tanHalf
        val dl = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9f)
        dx /= dl; dy /= dl; dz /= dl
        return Ray(eyeX, eyeY, eyeZ, dx, dy, dz)
    }

    /**
     * Where [ray] crosses the horizontal plane `z = planeZ`. Null when the ray points away from it
     * (or lies in it) — the caller treats that as "nothing to grab", not as an error.
     */
    fun intersectPlane(ray: Ray, planeZ: Float, out: FloatArray = FloatArray(3)): FloatArray? {
        if (abs(ray.dz) < 1e-6f) return null
        val t = (planeZ - ray.oz) / ray.dz
        if (t <= 0f) return null
        out[0] = ray.ox + ray.dx * t
        out[1] = ray.oy + ray.dy * t
        out[2] = planeZ
        return out
    }

    /**
     * Distance along [ray] at which it first enters the axis-aligned box, or null when it misses.
     * The slab method: clip the ray's parameter range against each axis; an empty range is a miss.
     */
    fun intersectAabb(
        ray: Ray,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
    ): Float? {
        var tMin = 0f
        var tMax = Float.MAX_VALUE
        fun slab(origin: Float, direction: Float, lo: Float, hi: Float): Boolean {
            if (abs(direction) < 1e-6f) return origin in lo..hi   // parallel: inside on this axis or a miss
            var t1 = (lo - origin) / direction
            var t2 = (hi - origin) / direction
            if (t1 > t2) { val swap = t1; t1 = t2; t2 = swap }
            if (t1 > tMin) tMin = t1
            if (t2 < tMax) tMax = t2
            return tMin <= tMax
        }
        if (!slab(ray.ox, ray.dx, minX, maxX)) return null
        if (!slab(ray.oy, ray.dy, minY, maxY)) return null
        if (!slab(ray.oz, ray.dz, minZ, maxZ)) return null
        return tMin
    }
}
