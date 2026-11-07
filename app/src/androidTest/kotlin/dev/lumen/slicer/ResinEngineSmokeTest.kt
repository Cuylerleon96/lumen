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
        // The SL1 raster is TRANSPOSED relative to the panel: the PNG's WIDTH is the panel's Y pixel
        // count and its height is the X count. Evidence across two panels: the engine default
        // (display_pixels 2560x1440) comes out 1440x2560, and a 3840x2400 panel comes out 2400x3840.
        val sl1 = slice(box(40f, 30f, 20f), centred = true)

        val (w, h) = firstLayerSize(sl1)
        assertEquals("raster width is the panel's Y count", monoX6k.resolutionY, w)
        assertEquals("raster height is the panel's X count", monoX6k.resolutionX, h)
    }

    @Test
    fun thePanelGeometryChangesTheRasterSize() {
        // The falsifiable form: same model, different printer, different raster. If display_* were
        // still being dropped, both slices would come out at the engine default (1440x2560) and match.
        val mono4k = ResinPrinter.byName("Anycubic Photon Mono 4K")!!
        val small = firstLayerSize(slice(box(20f, 20f, 10f), centred = true, printer = mono4k))
        val large = firstLayerSize(slice(box(20f, 20f, 10f), centred = true, printer = monoX6k))

        assertTrue("panels must differ: $small vs $large", small != large)
        assertEquals("4K raster width", 2400, small.first)
        assertEquals("4K raster height", 3840, small.second)
        assertEquals("6K raster width", 3600, large.first)
        assertEquals("6K raster height", 5760, large.second)
    }

    @Test
    fun centringOnTheBedKeepsTheWholeModel() {
        // The FIRST version of this test was wrong twice over, which is worth recording:
        //  1. it compared populated LAYER COUNTS (535 vs 535) -- clipping removes material *within*
        //     layers, not layers themselves;
        //  2. it compared ARCHIVE SIZE, which came out byte-identical (881813 vs 881813) -- so the
        //     model lands in the same place in the raster whichever coordinates we hand the engine.
        //     (The standalone CLI harness DOES show the difference, so the engine is not arranging;
        //     the app's own path evidently normalises it. The margins below are printed so the
        //     behaviour is visible rather than assumed.)
        // The discriminating check is whether the ink touches a plate edge -- i.e. is anything clipped.
        val centred = slice(box(40f, 30f, 20f), centred = true)
        val atOrigin = slice(box(40f, 30f, 20f), centred = false)

        println("ResinEngineSmokeTest margins: centred=${margins(midLayer(centred))} origin=${margins(midLayer(atOrigin))}")
        assertFalse(
            "the app's own slice must not touch a plate edge",
            touchesEdge(midLayer(centred)),
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

    /** Ink bounding box as [left, right, top, bottom] pixel margins — 0 means touching that edge. */
    private fun margins(bmp: android.graphics.Bitmap): List<Int> {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] and 0xFF) > 127) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return listOf(-1, -1, -1, -1)
        return listOf(minX, w - 1 - maxX, minY, h - 1 - maxY)
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
