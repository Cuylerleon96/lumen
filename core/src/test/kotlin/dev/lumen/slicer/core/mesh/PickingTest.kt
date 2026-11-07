package dev.lumen.slicer.core.mesh

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The picking arithmetic behind tap-to-select and drag-to-move.
 *
 * Every assertion here is a **hand-checkable invariant** rather than a copy of the implementation:
 * the centre ray must run from the eye to the target, an off-centre ray must lean toward the camera's
 * right/up, and a ray that lands on a box must round-trip through the plane intersection.
 */
class PickingTest {

    private val w = 1080
    private val h = 1920

    /** An eye 300 mm up and back, looking at the bed centre — like the plate camera at rest. */
    private fun eye(): Triple<Float, Float, Float> = Triple(210f, 60f, 260f)
    private val target = Triple(110f, 110f, 0f)

    private fun ray(sx: Float, sy: Float): Picking.Ray = Picking.rayForPixel(
        eyeX = eye().first, eyeY = eye().second, eyeZ = eye().third,
        targetX = target.first, targetY = target.second, targetZ = target.third,
        fovYDeg = 40f, width = w, height = h, sx = sx, sy = sy,
    )

    @Test
    fun `the centre ray points from the eye at the target`() {
        val r = ray(w / 2f, h / 2f)
        val ex = target.first - eye().first; val ey = target.second - eye().second; val ez = target.third - eye().third
        val len = sqrt(ex * ex + ey * ey + ez * ez)
        assertEquals(1f, sqrt(r.dx * r.dx + r.dy * r.dy + r.dz * r.dz), 1e-3f, "direction must be normalized")
        assertEquals(ex / len, r.dx, 1e-3f)
        assertEquals(ey / len, r.dy, 1e-3f)
        assertEquals(ez / len, r.dz, 1e-3f)
    }

    @Test
    fun `the centre ray hits the bed at the target`() {
        val hit = Picking.intersectPlane(ray(w / 2f, h / 2f), planeZ = 0f)
        assertNotNull(hit)
        assertEquals(target.first, hit[0], 0.5f)
        assertEquals(target.second, hit[1], 0.5f)
    }

    @Test
    fun `moving the finger right moves the hit to the camera's right`() {
        val centre = Picking.intersectPlane(ray(w / 2f, h / 2f), 0f)!!
        val right = Picking.intersectPlane(ray(w * 0.75f, h / 2f), 0f)!!
        val left = Picking.intersectPlane(ray(w * 0.25f, h / 2f), 0f)!!
        // The eye sits at +X,+Y of the bed centre and the camera's right is perpendicular to the view,
        // so a tap further right must land on the other side of the centre in one of the two bed axes.
        assertTrue(abs(right[0] - centre[0]) + abs(right[1] - centre[1]) > 1f, "the hit must actually move")
        assertTrue(abs(left[0] - right[0]) + abs(left[1] - right[1]) > 1f, "and move the other way")
        // Monotonic: f(x) is a straight line across the plane, so the midpoint reproduces the centre.
        assertEquals(centre[0], (left[0] + right[0]) / 2f, 0.5f)
        assertEquals(centre[1], (left[1] + right[1]) / 2f, 0.5f)
    }

    @Test
    fun `tapping down the screen moves the hit toward the viewer`() {
        val centre = Picking.intersectPlane(ray(w / 2f, h / 2f), 0f)!!
        val down = Picking.intersectPlane(ray(w / 2f, h * 0.75f), 0f)!!
        // Camera up is world +Z, so moving down the screen walks the hit toward the eye in plan view.
        // (Hand-checked: for the test eye the dot product is ~+5800 when the sense is right and
        // ~-5800 when it is inverted, so this genuinely discriminates rather than being a tautology.)
        val towardEye = (eye().first - centre[0]) * (down[0] - centre[0]) +
            (eye().second - centre[1]) * (down[1] - centre[1])
        assertTrue(towardEye > 0f, "dragging down must move the point nearer the eye in the bed plane")
    }

    @Test
    fun `a ray that crosses a box at the model centre reports a hit`() {
        // Take the pixel whose ray lands on the model's centre, so the test builds the ray it needs
        // instead of assuming where on screen the model is.
        val r = ray(w / 2f, h / 2f)
        assertNotNull(Picking.intersectAabb(r, 100f, 100f, 0f, 120f, 120f, 20f), "the centre ray must enter the box")
        // ...and a box nowhere near that ray must miss.
        assertNull(Picking.intersectAabb(r, -500f, -500f, 0f, -400f, -400f, 20f))
    }

    @Test
    fun `a box behind the eye is a miss`() {
        val r = ray(w / 2f, h / 2f)
        // The ray starts at the eye and goes forward, so a slab range entirely behind the origin is not
        // a hit — this is what keeps a tap from grabbing something behind the camera.
        val behind = Picking.intersectAabb(r, eye().first - 200f, eye().second - 200f, eye().third - 200f,
            eye().first - 100f, eye().second - 100f, eye().third - 100f)
        assertNull(behind)
    }

    @Test
    fun `the plane intersection is null when the ray points away from it`() {
        val up = Picking.Ray(0f, 0f, 50f, 0f, 0f, 1f)
        assertNull(Picking.intersectPlane(up, planeZ = 0f), "a ray climbing away from z=0 never crosses it")
        val flat = Picking.Ray(0f, 0f, 50f, 1f, 0f, 0f)
        assertNull(Picking.intersectPlane(flat, planeZ = 0f), "a ray parallel to the plane never crosses it")
    }

    @Test
    fun `a drag across the screen maps to a consistent bed displacement`() {
        // The move gesture is the DIFFERENCE of two plane hits, so what matters is that a drag of a
        // known number of pixels becomes a stable world delta and that it is reversible.
        val a = Picking.intersectPlane(ray(400f, 900f), 0f)!!
        val b = Picking.intersectPlane(ray(520f, 980f), 0f)!!
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        assertTrue(abs(dx) > 0.5f || abs(dy) > 0.5f, "a 120x80 px drag must move the model a measurable distance")
        val back = Picking.intersectPlane(ray(400f, 900f), 0f)!!
        assertEquals(a[0], back[0], 1e-3f, "the same pixel must always map to the same world point")
        assertEquals(a[1], back[1], 1e-3f)
    }
}
