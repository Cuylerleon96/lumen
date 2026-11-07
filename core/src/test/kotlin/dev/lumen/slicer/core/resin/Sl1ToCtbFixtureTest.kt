package dev.lumen.slicer.core.resin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Converts the bundled real engine-produced SL1 (`resources/engine-cube.sl1`) to CTB and writes it
 * under `build/ctb-fixture/` for independent validation (scripts/ctb_validate.py). No-op if absent.
 *
 * The fixture is a real engine raster — 90 layers of 1440x2560 PNGs, i.e. the engine's DEFAULT
 * 2560x1440 panel, transposed — so it must be converted for a printer with THAT panel. Handling it
 * as if it were one of the target machines is the mistake this test used to encode.
 */
class Sl1ToCtbFixtureTest {

    /** The engine's own default panel (its built-in 2560x1440), which is what the fixture was sliced on. */
    private val engineDefault = ResinPrinter("Engine default 2560x1440", 2560, 1440, 46.875, 150.0)

    @Test
    fun `convert bundled engine SL1 to CTB`() {
        val sl1 = javaClass.getResourceAsStream("/engine-cube.sl1")?.readBytes() ?: return
        val out = File("build/ctb-fixture/cube.ctb")
        out.parentFile?.mkdirs()
        out.outputStream().use { o ->
            Sl1ToCtb.convert(sl1, ResinSettings(), engineDefault, 20.0, 1515, o)
        }
        println("CTB written: ${out.absolutePath} (${out.length()} bytes)")
        assertTrue(out.length() > 1000, "CTB should be non-trivial, got ${out.length()}")
    }
}
