package dev.lumen.slicer.core.gcode

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arc path in [GcodeParser] — the **one parser branch with no test**, and the top remaining suspect
 * for the reported FDM preview tangle: a slicer with arc fitting on emits `G2`/`G3` instead of a chain of
 * `G1`s, and a wrong sweep or sign there produces exactly the symptom someone described — spikes and
 * slabs at odd angles rather than a coherent object. The calibration bar has no arcs, which is why the
 * reproduction attempt could not exercise it.
 *
 * The assertions are geometric rather than "compare with the implementation": every point of an arc
 * polyline must lie **on the circle**, in the correct direction, and end exactly on the commanded point.
 */
class GcodeArcTest {

    /** Parse a snippet and return the flat extrusion segments of its single layer. */
    private fun segments(gcode: String): FloatArray {
        val toolpaths = GcodeParser.parse(gcode.trimIndent().reader().buffered())
        assertTrue(toolpaths.layers.isNotEmpty(), "no layer was produced from:\n$gcode")
        return toolpaths.layers[0].extrusions
    }

    private fun points(segments: FloatArray): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>(segments.size / 3)
        var i = 0
        while (i + 5 < segments.size) {
            out += segments[i] to segments[i + 1]
            out += segments[i + 3] to segments[i + 4]
            i += 6
        }
        return out
    }

    /** Distance from the arc's centre, which for these fixtures is the origin. */
    private fun radius(p: Pair<Float, Float>) = hypot(p.first, p.second)

    @Test
    fun `a clockwise arc sweeps the long way round and stays on the circle`() {
        // G2 from (10,0) to (0,10) about the origin: clockwise means the 270 degree way (through the
        // bottom), NOT the 90 degree short way — getting that backwards is what flips a model's seam.
        // The lead-in is a TRAVEL (no E) so the extrusion list holds the arc alone; an `E` on it would
        // add a straight segment from the origin and the radius check below would be measuring that.
        val segs = segments(
            """
            G90
            M83
            G1 X10 Y0
            G2 X0 Y10 I-10 J0 E2.0
            """,
        )
        val pts = points(segs)

        assertTrue(pts.size / 2 in 80..128, "a 270 degree arc of r=10 is ~95 segments, got ${pts.size / 2}")
        for ((x, y) in pts) {
            assertEquals(10f, hypot(x, y), 0.2f, "every point of an arc lies on the circle (at $x, $y)")
        }
        assertEquals(10f, pts.first().first, 0.01f)
        assertEquals(0f, pts.first().second, 0.01f)
        assertEquals(0f, pts.last().first, 0.01f, "the arc must END on the commanded point")
        assertEquals(10f, pts.last().second, 0.01f, "the arc must END on the commanded point")
        assertTrue(pts.any { it.second <= -9f }, "it should pass the far side of the circle (y near -10)")
    }

    @Test
    fun `a counter-clockwise arc is the short way and stays on the circle`() {
        val segs = segments(
            """
            G90
            M83
            G1 X10 Y0
            G3 X0 Y10 I-10 J0 E2.0
            """,
        )
        val pts = points(segs)

        for ((x, y) in pts) {
            assertEquals(10f, hypot(x, y), 0.2f, "every point of an arc lies on the circle (at $x, $y)")
        }
        // A 90 degree arc: it should pass near (7.07, 7.07), i.e. up and to the right, and never dip
        // below the start/end points.
        assertTrue(pts.any { it.first > 6.5f && it.second > 6.5f }, "a CCW quarter arc passes through the first quadrant")
        assertTrue(pts.all { it.second >= -0.1f }, "and never goes below the diameter between its ends")
        // pts holds two endpoints per segment, so the segment count is half of it.
        assertTrue(pts.size / 2 in 20..40, "90 degrees of r=10 is ~32 segments, got ${pts.size / 2}")
    }

    @Test
    fun `an R-form arc is drawn straight rather than invented`() {
        // The parser deliberately treats the R form as a straight move (slicers rarely emit it). Pinned
        // so the choice is visible rather than accidental.
        val segs = segments(
            """
            G90
            M83
            G1 X10 Y0
            G2 X0 Y10 R10 E2.0
            """,
        )
        val pts = points(segs)

        assertEquals(2, pts.size, "one straight segment (two points), got ${pts.size}")
        assertEquals(0f, pts.last().first, 0.01f)
        assertEquals(10f, pts.last().second, 0.01f)
    }

    @Test
    fun `a degenerate arc with no radius does not crash or emit a NaN`() {
        // I=J=0 is malformed but appears in the wild; it must degrade to a single reliable segment.
        val segs = segments(
            """
            G90
            M83
            G1 X5 Y5 E0.5
            G2 X6 Y6 I0 J0 E1.0
            """,
        )
        val pts = points(segs)

        assertTrue(pts.isNotEmpty(), "a degenerate arc must still emit something")
        for ((x, y) in pts) {
            assertTrue(x.isFinite() && y.isFinite(), "no NaN/Inf may reach the vertex buffer (got $x, $y)")
            assertTrue(abs(x) < 1000f && abs(y) < 1000f, "and nothing may fly off to infinity (got $x, $y)")
        }
        assertEquals(6f, pts.last().first, 0.01f)
        assertEquals(6f, pts.last().second, 0.01f)
    }
}
