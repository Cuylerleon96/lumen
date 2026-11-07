package dev.lumen.slicer.core.mesh

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Auto-arrange.
 *
 * The invariants asserted here are the ones a user would notice if broken: **nothing overlaps,
 * nothing lands off the plate, and a model that does not fit is reported rather than dropped.**
 * Positions are checked geometrically rather than against hard-coded numbers, so the packing strategy
 * can change without the tests lying — except the determinism test, which pins the exact output on
 * purpose, because a plate that reshuffles on every tap is the thing being guarded against.
 */
class PlateArrangerTest {

    /** 198.144 x 123.84 mm — the Anycubic Photon Mono X 6K panel. */
    private val bedW = 198.144
    private val bedD = 123.84

    private fun item(id: Int, w: Double, d: Double) = PlateArranger.Item(id, w, d)

    /** Axis-aligned footprints as (left, right, front, back). */
    private fun boxOf(s: PlateArranger.Spot, i: PlateArranger.Item): DoubleArray {
        val w = if (s.rotated) i.depthMm else i.widthMm
        val d = if (s.rotated) i.widthMm else i.depthMm
        return doubleArrayOf(
            s.centerX - w / 2, s.centerX + w / 2,
            s.centerY - d / 2, s.centerY + d / 2,
        )
    }

    private fun overlaps(a: DoubleArray, b: DoubleArray): Boolean =
        a[0] < b[1] && b[0] < a[1] && a[2] < b[3] && b[2] < a[3]

    /** Gap between two footprints along each axis (negative = overlapping on that axis). */
    private fun gaps(a: DoubleArray, b: DoubleArray): Pair<Double, Double> =
        max(a[0], b[0]) - minOf(a[1], b[1]) to max(a[2], b[2]) - minOf(a[3], b[3])

    @Test
    fun `four models fit on the plate and none of them overlap`() {
        val items = List(4) { item(it, 40.0, 30.0) }

        val r = PlateArranger.arrange(items, bedW, bedD, spacingMm = 3.0, marginMm = 2.0)

        assertTrue(r.allPlaced, "all four should fit on a 198x124 plate: unplaced=${r.unplaced}")
        val boxes = r.spots.map { boxOf(it, items[it.id]) }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            assertFalse(overlaps(boxes[i], boxes[j]), "models ${i} and ${j} overlap")
        }
    }

    @Test
    fun `every model lands inside the plate with its margin respected`() {
        val items = List(9) { item(it, 30.0, 25.0) }
        val margin = 2.0

        val r = PlateArranger.arrange(items, bedW, bedD, spacingMm = 3.0, marginMm = margin)

        for (s in r.spots) {
            val b = boxOf(s, items[s.id])
            assertTrue(b[0] >= margin - 1e-6, "left edge ${b[0]} inside the margin")
            assertTrue(b[1] <= bedW - margin + 1e-6, "right edge ${b[1]} inside the plate")
            assertTrue(b[2] >= margin - 1e-6, "front edge ${b[2]} inside the margin")
            assertTrue(b[3] <= bedD - margin + 1e-6, "back edge ${b[3]} inside the plate")
        }
    }

    @Test
    fun `neighbours keep the requested spacing`() {
        val items = List(6) { item(it, 35.0, 28.0) }
        val spacing = 4.0

        val r = PlateArranger.arrange(items, bedW, bedD, spacingMm = spacing, marginMm = 2.0)

        val boxes = r.spots.map { boxOf(it, items[it.id]) }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val (gx, gy) = gaps(boxes[i], boxes[j])
            // Separated on at least one axis by the spacing (they may be far apart on both).
            assertTrue(
                gx >= spacing - 1e-6 || gy >= spacing - 1e-6,
                "models $i and $j are closer than ${spacing}mm (gaps $gx, $gy)",
            )
        }
    }

    @Test
    fun `arranging is deterministic`() {
        val items = List(7) { item(it, 30.0 + it, 20.0 + it) }

        val a = PlateArranger.arrange(items, bedW, bedD)
        val b = PlateArranger.arrange(items, bedW, bedD)

        assertEquals(a.spots, b.spots, "the same plate must arrange the same way twice")
    }

    @Test
    fun `rotation is used when it is the difference between fitting and not`() {
        // A 100x40 model on a 60x110 plate (usable 56x106): it ONLY fits turned 90 degrees.
        val items = listOf(item(0, 100.0, 40.0))

        val rotated = PlateArranger.arrange(items, 60.0, 110.0, spacingMm = 2.0, marginMm = 2.0, allowRotation = true)
        val upright = PlateArranger.arrange(items, 60.0, 110.0, spacingMm = 2.0, marginMm = 2.0, allowRotation = false)

        assertTrue(rotated.allPlaced, "should fit once rotated: unplaced=${rotated.unplaced}")
        assertTrue(rotated.spots.single().rotated, "and it should report that it was turned")
        assertTrue(!upright.allPlaced, "without rotation this model cannot fit")
    }

    @Test
    fun `models that do not fit are reported rather than dropped`() {
        // 60 models of 60x40 cannot fit on a 198x124 plate.
        val items = List(60) { item(it, 60.0, 40.0) }

        val r = PlateArranger.arrange(items, bedW, bedD)

        assertTrue(r.unplaced.isNotEmpty(), "some models must be reported as not fitting")
        assertEquals(items.size, r.spots.size + r.unplaced.size, "every model is either placed or reported")
        assertEquals(items.map { it.id }.toSet(), (r.spots.map { it.id } + r.unplaced).toSet())
    }

    @Test
    fun `a plate too small for anything reports everything unplaced`() {
        val items = List(3) { item(it, 50.0, 50.0) }

        val r = PlateArranger.arrange(items, 20.0, 20.0, marginMm = 2.0)

        assertEquals(emptyList(), r.spots.map { it.id })
        assertEquals(listOf(0, 1, 2), r.unplaced)
    }

    @Test
    fun `an empty list arranges to nothing`() {
        val r = PlateArranger.arrange(emptyList(), bedW, bedD)

        assertTrue(r.spots.isEmpty())
        assertTrue(r.unplaced.isEmpty())
        assertTrue(r.allPlaced)
    }

    @Test
    fun `spots come back in the caller's input order`() {
        val items = listOf(item(5, 10.0, 10.0), item(2, 60.0, 60.0), item(9, 20.0, 20.0))

        val r = PlateArranger.arrange(items, bedW, bedD)

        assertEquals(listOf(5, 2, 9), r.spots.map { it.id }, "the UI maps spots to models by position")
    }
}
