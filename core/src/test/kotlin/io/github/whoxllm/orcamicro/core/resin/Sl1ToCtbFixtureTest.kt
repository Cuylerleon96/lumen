package io.github.whoxllm.orcamicro.core.resin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Converts the bundled real engine-produced SL1 (`resources/engine-cube.sl1`) to CTB and writes it
 * under `build/ctb-fixture/` for independent validation (scripts/ctb_validate.py). No-op if absent.
 */
class Sl1ToCtbFixtureTest {

    @Test
    fun `convert bundled engine SL1 to CTB`() {
        val sl1 = javaClass.getResourceAsStream("/engine-cube.sl1")?.readBytes() ?: return
        val out = File("build/ctb-fixture/cube.ctb")
        out.parentFile?.mkdirs()
        out.outputStream().use { o ->
            Sl1ToCtb.convert(sl1, ResinSettings(), ResinPrinter.builtIn.first(), 20.0, 1515, o)
        }
        println("CTB written: ${out.absolutePath} (${out.length()} bytes)")
        assertTrue(out.length() > 1000, "CTB should be non-trivial, got ${out.length()}")
    }
}
