package dev.lumen.slicer

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.MeshPrimitives
import dev.lumen.slicer.core.mesh.Placement
import dev.lumen.slicer.core.mesh.StlWriter
import dev.lumen.slicer.core.resin.ResinPlacement
import dev.lumen.slicer.core.resin.ResinPrinter
import dev.lumen.slicer.core.resin.ResinSettings
import dev.lumen.slicer.core.resin.SlaEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/**
 * The resin (SLA) path, on device, against the real native engine.
 *
 * The FDM smoke test next door could not have caught either of the two bugs found by slicing a real
 * model, because neither is about the FDM engine: the resin model was placed on the wrong point of the
 * bed, and the printer's panel geometry was never sent to the engine. Both were silent — the slice
 * succeeded, it was just wrong — so they need assertions on the *output*, which is what this is.
 */
@RunWith(AndroidJUnit4::class)
class ResinEngineSmokeTest {

    private val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    private val monoX6k = ResinPrinter.byName("Anycubic Photon Mono X 6K")!!

    @Test
    fun slaEngineLoads() {
        assertTrue("SLA engine failed to load", SlaEngine.ensureLoaded())
    }

    @Test
    fun slicesAtTheSelectedPrintersPanelResolution() {
        // 5760x3600 on a Mono X 6K. Before the fix the engine kept its default panel (2560 px) and
        // every printer came out at the SL1's resolution -- a quarter of this panel.
        val sl1 = slice(box(40f, 30f, 20f), centred = true)

        val (w, h) = firstLayerSize(sl1)
        assertEquals("panel pixels across", monoX6k.resolutionX, w)
        assertEquals("panel pixels down", monoX6k.resolutionY, h)
    }

    @Test
    fun thePanelGeometryChangesTheRasterSize() {
        // The falsifiable form: same model, different printer, different raster. If display_* were
        // still being dropped, both slices would come out at the engine default and match.
        val mono4k = ResinPrinter.byName("Anycubic Photon Mono 4K")!!
        val small = firstLayerSize(slice(box(20f, 20f, 10f), centred = true, printer = mono4k))
        val large = firstLayerSize(slice(box(20f, 20f, 10f), centred = true, printer = monoX6k))

        assertTrue("panels must differ: $small vs $large", small != large)
        assertEquals(3840, small.first)
        assertEquals(5760, large.first)
    }

    @Test
    fun centringOnTheBedKeepsTheWholeModel() {
        // The bug, as an assertion -- and the FIRST version of this test was wrong, which is worth
        // recording: it compared the number of populated LAYERS and got 535 vs 535, because clipping
        // a model removes material *within* layers, not layers themselves. A clipped slice still has
        // every layer populated, just with less ink. The discriminating quantity is ink (or archive
        // size) plus whether the ink touches a plate edge.
        val centred = slice(box(40f, 30f, 20f), centred = true)
        val atOrigin = slice(box(40f, 30f, 20f), centred = false)

        assertTrue(
            "centred must carry more material: ${centred.length()} vs ${atOrigin.length()} bytes",
            centred.length() > atOrigin.length() * 1.5,
        )
        assertFalse("a bed-centred model must not touch a plate edge", touchesEdge(midLayer(centred)))
        assertTrue(
            "an origin-centred model on a corner-origin bed is clipped at the plate edge",
            touchesEdge(midLayer(atOrigin)),
        )
    }

    // ---------------------------------------------------------------- helpers

    /** The middle layer's bitmap, downsampled — enough to see where the ink sits. */
    private fun midLayer(sl1: File): android.graphics.Bitmap {
        ZipFile(sl1).use { zip ->
            val layers = zip.entries().asSequence().filter { it.name.endsWith(".png") }.sortedBy { it.name }.toList()
            val bytes = zip.getInputStream(layers[layers.size / 2]).use { it.readBytes() }
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: error("could not decode a layer")
        }
    }

    /** True when any ink sits on the outermost row/column — i.e. the model runs off the plate. */
    private fun touchesEdge(bmp: android.graphics.Bitmap): Boolean {
        val w = bmp.width; val h = bmp.height
        val row = IntArray(w); val col = IntArray(h)
        bmp.getPixels(row, 0, w, 0, 0, w, 1)                       // top row
        if (row.any { (it and 0xFF) > 127 }) return true
        bmp.getPixels(row, 0, w, 0, h - 1, w, 1)                   // bottom row
        if (row.any { (it and 0xFF) > 127 }) return true
        bmp.getPixels(col, 0, 1, 0, 0, 1, h)                       // left column
        if (col.any { (it and 0xFF) > 127 }) return true
        bmp.getPixels(col, 0, 1, w - 1, 0, 1, h)                   // right column
        return col.any { (it and 0xFF) > 127 }
    }

    private fun slice(mesh: Mesh, centred: Boolean, printer: ResinPrinter = monoX6k): File {
        val settings = ResinSettings(layerHeightMm = 0.05)
        val (cx, cy) = if (centred) ResinPlacement.bedCentre(printer) else 0f to 0f
        val placed = Placement(centerX = cx, centerY = cy).place(mesh)

        val dir = File(context.cacheDir, "resin-smoke").apply { mkdirs() }
        val stl = File(dir, "m.stl")
        val sl1 = File(dir, "m.sl1").apply { delete() }
        stl.outputStream().use { StlWriter.write(placed, it) }

        val rc = SlaEngine.nativeSlice(stl.absolutePath, sl1.absolutePath, settings.toEngineJson(printer))
        assertEquals("nativeSlice rc", 0, rc)
        assertTrue("no SL1 written", sl1.isFile && sl1.length() > 0)
        return sl1
    }

    /** (width, height) of the first layer PNG, read from its IHDR. */
    private fun firstLayerSize(sl1: File): Pair<Int, Int> {
        ZipFile(sl1).use { zip ->
            val entry = zip.entries().asSequence().filter { it.name.endsWith(".png") }.minByOrNull { it.name }
                ?: error("no layer PNGs in ${sl1.name}")
            val png = zip.getInputStream(entry).use { ins ->
                val head = ByteArray(24)
                var read = 0
                while (read < 24) {
                    val n = ins.read(head, read, 24 - read)
                    if (n < 0) break
                    read += n
                }
                head
            }
            val bb = ByteBuffer.wrap(png, 16, 8)
            return bb.int to bb.int
        }
    }

    private fun box(w: Float, d: Float, h: Float) = MeshPrimitives.box(w, d, h, 0f, 0f, h / 2f)
}
