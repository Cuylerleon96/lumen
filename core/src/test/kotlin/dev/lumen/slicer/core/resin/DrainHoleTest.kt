package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.MeshPrimitives
import dev.lumen.slicer.core.mesh.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drain-hole drilling.
 *
 * Two independent observables, because they fail differently:
 *  1. **Volume** — a hole that was drilled removes the bore's volume; a hole that was silently
 *     skipped (bad margin, bad direction) leaves the original volume.
 *  2. **Ray crossings along the bore axis** — the property that actually matters to a print: the hole
 *     must be an *open passage*. Volume alone cannot see that (a sealed cavity and a through-hole can
 *     remove the same amount of material). A ray fired down the bore must cross **zero** surfaces,
 *     where the undrilled cube crosses two.
 *
 * Ray note: the probe is offset ~0.76 mm off the bore axis (0.7, 0.3) rather than fired exactly down
 * it. An on-axis ray passes through the fan *centre vertex* of the drill's end caps and along the
 * reference box's face diagonals, and Möller–Trumbore then counts one hit per adjacent triangle
 * (48 for a cap fan) — a counting artefact, not a geometry difference. 0.76 mm is well inside the
 * smallest bore tested (r = 1.5) and off the y = x diagonals, so it removes the degeneracy without
 * changing what is measured.
 */
class DrainHoleTest {

    private val ox = 0.7f
    private val oy = 0.3f

    /** Möller–Trumbore: how many triangles of [mesh] the ray `p + t*d` (t > 0) hits. */
    private fun rayHits(mesh: Mesh, p: Vec3, d: Vec3, tMax: Float = 1e4f): Int {
        val pos = mesh.positions
        val eps = 1e-6f
        var hits = 0
        var i = 0
        while (i < pos.size) {
            val ax = pos[i]; val ay = pos[i + 1]; val az = pos[i + 2]
            val bx = pos[i + 3]; val by = pos[i + 4]; val bz = pos[i + 5]
            val cx = pos[i + 6]; val cy = pos[i + 7]; val cz = pos[i + 8]
            val e1x = bx - ax; val e1y = by - ay; val e1z = bz - az
            val e2x = cx - ax; val e2y = cy - ay; val e2z = cz - az
            val hx = d.y * e2z - d.z * e2y
            val hy = d.z * e2x - d.x * e2z
            val hz = d.x * e2y - d.y * e2x
            val det = e1x * hx + e1y * hy + e1z * hz
            if (det > -eps && det < eps) { i += 9; continue }
            val inv = 1f / det
            val sx = p.x - ax; val sy = p.y - ay; val sz = p.z - az
            val u = (sx * hx + sy * hy + sz * hz) * inv
            if (u < 0f || u > 1f) { i += 9; continue }
            val qx = sy * e1z - sz * e1y
            val qy = sz * e1x - sx * e1z
            val qz = sx * e1y - sy * e1x
            val v = (d.x * qx + d.y * qy + d.z * qz) * inv
            if (v < 0f || u + v > 1f) { i += 9; continue }
            val t = (e2x * qx + e2y * qy + e2z * qz) * inv
            if (t > eps && t < tMax) hits++
            i += 9
        }
        return hits
    }

    /** Cross-section of the 48-gon the drill actually draws (99.857% of pi r^2). */
    private fun boreArea(r: Float, segments: Int = DrainHoles.SEGMENTS): Double =
        0.5 * segments * r * r * Math.sin(2.0 * Math.PI / segments)

    @Test
    fun `a through drill removes the bore volume and leaves an open passage`() {
        val cube = MeshPrimitives.box(20f, 20f, 20f) // 8000 mm3, spans -10..10
        val hole = DrainHole(x = 0f, y = 0f, z = 10f, dx = 0f, dy = 0f, dz = -1f, radiusMm = 2f)

        val drilled = DrainHoles.drill(cube, listOf(hole))

        val r = 2f
        assertEquals(8000.0 - boreArea(r) * 20.0, drilled.volumeMm3(), 0.5, "vol=${drilled.volumeMm3()}")

        // The bore is empty space, so a ray down its axis hits nothing...
        assertEquals(0, rayHits(drilled, Vec3(ox, oy, 30f), Vec3(0f, 0f, -1f)), "the bore should be open")
        // ...while the solid cube without the hole stops the ray twice.
        assertEquals(2, rayHits(cube, Vec3(ox, oy, 30f), Vec3(0f, 0f, -1f)))
        // A ray just outside the bore still hits the solid top and bottom faces.
        assertEquals(2, rayHits(drilled, Vec3(5f, oy, 30f), Vec3(0f, 0f, -1f)))
    }

    @Test
    fun `a shallow drill cuts a crater of the requested depth`() {
        val cube = MeshPrimitives.box(20f, 20f, 20f)
        val hole = DrainHole(x = 0f, y = 0f, z = 10f, dx = 0f, dy = 0f, dz = -1f, radiusMm = 3f, depthMm = 2f)

        val drilled = DrainHoles.drill(cube, listOf(hole), wallThicknessMm = 2f)

        // Bit runs from z = 10.5 down to z = 10.5 - (2 + 0.5) = 8.0, so 2 mm of material goes.
        assertEquals(8000.0 - boreArea(3f) * 2.0, drilled.volumeMm3(), 0.5, "vol=${drilled.volumeMm3()}")
        // Blind hole: the probe enters the crater mouth, leaves through the crater floor, then the
        // cube's bottom face -> 2 crossings, i.e. still sealed.
        assertEquals(2, rayHits(drilled, Vec3(ox, oy, 30f), Vec3(0f, 0f, -1f)))
    }

    @Test
    fun `two drills from different faces are two independent bores`() {
        val cube = MeshPrimitives.box(20f, 20f, 20f)
        // Axes 5 mm apart in Y: 5 > 2r = 4, so the two bores never intersect.
        val fromTop = DrainHole(0f, 0f, 10f, 0f, 0f, -1f, radiusMm = 2f)
        val fromSide = DrainHole(10f, 5f, 0f, -1f, 0f, 0f, radiusMm = 2f)

        val drilled = DrainHoles.drill(cube, listOf(fromTop, fromSide))

        assertEquals(8000.0 - 2.0 * boreArea(2f) * 20.0, drilled.volumeMm3(), 0.5, "vol=${drilled.volumeMm3()}")
        assertEquals(0, rayHits(drilled, Vec3(ox, oy, 30f), Vec3(0f, 0f, -1f)))
        assertEquals(0, rayHits(drilled, Vec3(30f, 5f + oy, oy), Vec3(-1f, 0f, 0f)))
    }

    @Test
    fun `two crossing drills share the Steinmetz volume only once`() {
        val cube = MeshPrimitives.box(20f, 20f, 20f)
        val r = 2f
        // Same centre, perpendicular axes -> the bores do intersect, so set algebra must discount
        // the shared block exactly once.
        val fromTop = DrainHole(0f, 0f, 10f, 0f, 0f, -1f, radiusMm = r)
        val fromSide = DrainHole(10f, 0f, 0f, -1f, 0f, 0f, radiusMm = r)

        val drilled = DrainHoles.drill(cube, listOf(fromTop, fromSide))

        // V(a - b - c) = V(a) - V(b) - V(c) + V(b n c); the intersection of two perpendicular equal
        // cylinders is the Steinmetz solid, 16 r^3 / 3 = 42.67 mm3 at r = 2. The drills are 48-gon
        // prisms, so the true intersection sits ~0.4% under that — hence the 1.5 mm3 tolerance.
        val steinmetz = 16.0 * r * r * r / 3.0
        assertEquals(
            8000.0 - 2.0 * boreArea(r) * 20.0 + steinmetz,
            drilled.volumeMm3(),
            1.5,
            "vol=${drilled.volumeMm3()}",
        )
        assertEquals(0, rayHits(drilled, Vec3(ox, oy, 30f), Vec3(0f, 0f, -1f)))
        assertEquals(0, rayHits(drilled, Vec3(30f, oy, oy), Vec3(-1f, 0f, 0f)))
    }

    @Test
    fun `an oblique drill still opens a passage`() {
        val cube = MeshPrimitives.box(20f, 20f, 20f)
        // 45 degrees in the XZ plane, tapped on the top face.
        val inv = 1f / kotlin.math.sqrt(2f)
        val hole = DrainHole(x = 5f, y = 0f, z = 10f, dx = -inv, dy = 0f, dz = -inv, radiusMm = 1.5f)

        val drilled = DrainHoles.drill(cube, listOf(hole))

        assertTrue(drilled.volumeMm3() < 8000.0, "the drill removed material")
        // Open along the drill's own axis, offset 0.7 mm in Y (perpendicular to the drill plane).
        val start = Vec3(5f, oy, 10f) + Vec3(inv, 0f, inv) * -25f
        assertEquals(0, rayHits(drilled, start, Vec3(inv, 0f, inv)))
    }

    @Test
    fun `drilling no holes is a no-op`() {
        val cube = MeshPrimitives.box(20f, 20f, 20f)

        val drilled = DrainHoles.drill(cube, emptyList())

        assertEquals(8000.0, drilled.volumeMm3(), 0.01)
        assertEquals(cube.triangleCount, drilled.triangleCount)
    }
}
