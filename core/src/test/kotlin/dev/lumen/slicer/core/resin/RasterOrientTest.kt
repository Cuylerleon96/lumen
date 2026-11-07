package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the **measured** raster orientation.
 *
 * The rule is not reasoned about — it was measured by slicing three size-tagged markers at world
 * (0,0), (+30,0) and (0,+20) mm through the standalone engine CLI and reading where the ink landed
 * (see [RasterOrient]). So these tests encode the measured world→pixel mapping **twice** — once as
 * the engine's transposed raster and once in the panel's own frame — and assert the rotation
 * reconciles them. That is deliberately not "call the rotate function and compare with the rotate
 * function": a wrong direction fails, which is the whole point, because getting it backwards mirrors
 * every print.
 */
class RasterOrientTest {

    private val panel = ResinPrinter("Orient test panel", 40, 24, 50.0, 30.0)

    /** Ink as a function of the WORLD position only — the same pattern addressed in either frame. */
    private fun world(x: Int, y: Int): Byte = if ((x + 2 * y) % 7 < 3) 0xFF.toByte() else 0

    @Test
    fun `a 90 degree clockwise rotation is what the engine's raster needs`() {
        // Hand-computed, so the direction is pinned by numbers rather than by the implementation:
        //   1 2 3   ->   4 1
        //   4 5 6        5 2
        //                6 3
        val rotated = RasterOrient.rotateClockwise(byteArrayOf(1, 2, 3, 4, 5, 6), width = 3, height = 2)
        assertEquals(2, rotated.width, "a 3x2 source becomes 2 wide")
        assertEquals(3, rotated.height, "…and 3 high")
        assertEquals(listOf<Byte>(4, 1, 5, 2, 6, 3), rotated.gray.toList())
    }

    @Test
    fun `world X runs down the image reversed and world Y across it directly`() {
        // The engine's raster: width = panel Y pixel count, height = panel X count, X down-reversed.
        val engine = ByteArray(panel.resolutionX * panel.resolutionY)
        for (v in 0 until panel.resolutionX) {
            for (u in 0 until panel.resolutionY) {
                engine[v * panel.resolutionY + u] = world(panel.resolutionX - 1 - v, u)
            }
        }
        assertTrue(
            RasterOrient.isEngineTransposed(panel.resolutionY, panel.resolutionX, panel.resolutionX, panel.resolutionY),
            "the engine's raster is the panel's transpose",
        )

        val oriented = RasterOrient.forPanel(engine, panel.resolutionY, panel.resolutionX, panel)

        // The same world pattern addressed in the panel's own frame: row = Y, col = X.
        val expected = ByteArray(panel.resolutionX * panel.resolutionY)
        for (y in 0 until panel.resolutionY) {
            for (x in 0 until panel.resolutionX) {
                expected[y * panel.resolutionX + x] = world(x, y)
            }
        }
        assertEquals(panel.resolutionX, oriented.width)
        assertEquals(panel.resolutionY, oriented.height)
        assertEquals(expected.toList(), oriented.gray.toList(), "the rotated raster must match the panel frame")
    }

    @Test
    fun `the counter-rotation would not pass`() {
        // Control: if the implemented direction were counter-clockwise, the rotation would produce
        // this array instead, and it must NOT match the panel frame — otherwise the test above is
        // asserting something that any rotation satisfies.
        val engine = ByteArray(panel.resolutionX * panel.resolutionY)
        for (v in 0 until panel.resolutionX) {
            for (u in 0 until panel.resolutionY) {
                engine[v * panel.resolutionY + u] = world(panel.resolutionX - 1 - v, u)
            }
        }
        val ccw = ByteArray(engine.size)
        for (v in 0 until panel.resolutionX) {
            for (u in 0 until panel.resolutionY) {
                ccw[(panel.resolutionY - 1 - u) * panel.resolutionX + v] = engine[v * panel.resolutionY + u]
            }
        }
        val actual = RasterOrient.forPanel(engine, panel.resolutionY, panel.resolutionX, panel)
        assertNotEquals(ccw.toList(), actual.gray.toList(), "a counter-clockwise rotation must be distinguishable")
    }

    @Test
    fun `a raster for another panel is refused rather than scaled`() {
        val e = assertFailsWith<IllegalArgumentException> {
            RasterOrient.forPanel(ByteArray(64 * 48), 64, 48, panel)
        }
        assertTrue(e.message!!.contains("does not match"), e.message!!)
        assertTrue(e.message!!.contains(panel.name), "the message must name the printer: ${e.message}")
    }

    @Test
    fun `a raster already in the panel's frame is passed through untouched`() {
        val img = ByteArray(panel.resolutionX * panel.resolutionY) { (it % 251).toByte() }
        val same = RasterOrient.forPanel(img, panel.resolutionX, panel.resolutionY, panel)
        assertEquals(panel.resolutionX, same.width)
        assertEquals(panel.resolutionY, same.height)
        assertEquals(img.toList(), same.gray.toList())
    }
}
