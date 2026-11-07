package io.github.whoxllm.orcamicro.core.resin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fixture test: if a real engine-produced SL1 exists at /tmp/mslicer-sl1/cube.sl1, convert it to CTB
 * and leave the output for external validation (see scripts/ctb_validate.py). No-op when absent.
 */
class Sl1ToCtbFixtureTest {

    @Test
    fun `convert real SL1 to CTB`() {
        val input = File("/tmp/mslicer-sl1/cube.sl1")
        if (!input.isFile) return
        val out = File("/tmp/mslicer-sl1/cube.ctb")
        val printer = ResinPrinter.builtIn.first { it.name.startsWith("Elegoo Mars 3") }
        out.outputStream().use { o ->
            Sl1ToCtb.convert(input.readBytes(), ResinSettings(), printer, 20.0, 1515, o)
        }
        assertTrue(out.length() > 1000, "CTB should be non-trivial, got ${out.length()}")
    }
}
