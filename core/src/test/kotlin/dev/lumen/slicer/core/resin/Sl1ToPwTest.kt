package dev.lumen.slicer.core.resin

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

/**
 * SL1 → Anycubic re-container.
 *
 * The SL1 fixtures are built with **javax.imageio**, not with this project's own PNG code, so the test
 * does not share an encoder bug with the thing it is testing — the same reason the RLE assertions are
 * hand-computed rather than round-tripped.
 */
class Sl1ToPwTest {

    private val monoX = ResinPrinter.byName("Anycubic Photon Mono X")!!
    private val monoX6k = ResinPrinter.byName("Anycubic Photon Mono X 6K")!!
    private val tiny = ResinPrinter("Tiny test panel", 16, 16, 50.0, 20.0)

    @Test
    fun `the anycubic machines declare their variant extension`() {
        // The variant is chosen by EXTENSION, so each machine has to carry its own.
        assertEquals(ResinFormat.ANYCUBIC, monoX.format)
        assertEquals("pwmx", monoX.outputExtension)
        assertEquals(ResinFormat.ANYCUBIC, monoX6k.format)
        assertEquals("pwmb", monoX6k.outputExtension)
        // A machine whose format has no variant override still resolves to the format's own extension.
        assertEquals("ctb", ResinPrinter.byName("Elegoo Saturn (4K)")!!.outputExtension)
    }

    @Test
    fun `only pws selects the bit-plane codec`() {
        // Getting this wrong produces a structurally valid file that prints a wrong image.
        assertEquals(AnycubicPwWriter.Codec.PW0, Sl1ToPw.codecFor("pwmx"))
        assertEquals(AnycubicPwWriter.Codec.PW0, Sl1ToPw.codecFor("pwmb"))
        assertEquals(AnycubicPwWriter.Codec.PW0, Sl1ToPw.codecFor("pw0"))
        assertEquals(AnycubicPwWriter.Codec.PWS, Sl1ToPw.codecFor("pws"))
        assertEquals(AnycubicPwWriter.Codec.PWS, Sl1ToPw.codecFor("PWS"))
    }

    @Test
    fun `an SL1 becomes a file the printer's own family starts with`() {
        val sl1 = sl1(tiny, listOf(mask(tiny, 0x00, 0xFF), mask(tiny, 0x00, 0x00), mask(tiny, 0xFF, 0xFF)))

        val out = ByteArrayOutputStream()
        Sl1ToPw.convert(sl1, ResinSettings(), tiny, 10.0, 60, out, extension = "pwmx")

        val bytes = out.toByteArray()
        assertEquals("ANYCUBIC", String(bytes, 0, 8), "every variant starts with the same 12-byte mark")
        assertTrue(bytes.size > 200, "expected a real file, got ${bytes.size} bytes")
        // The machine table carries the printer name and the panel geometry.
        val asText = String(bytes, Charsets.ISO_8859_1)
        assertTrue(asText.contains(tiny.name), "the machine table should name the printer")
    }

    @Test
    fun `the software table appears only at version 517 and up`() {
        // Version 516 is the default because it is the highest BOTH target machines accept, and it
        // predates the SOFTWARE/MODEL tables. Asserting the boundary keeps a future change from
        // silently writing tables an older machine would choke on.
        val sl1 = sl1(tiny, listOf(mask(tiny, 0x00, 0xFF)))
        fun at(v: Int): String {
            val out = ByteArrayOutputStream()
            Sl1ToPw.convert(sl1, ResinSettings(), tiny, 10.0, 60, out, extension = "pwmb", version = v)
            return String(out.toByteArray(), Charsets.ISO_8859_1)
        }

        assertFalse(at(516).contains("Lumen"), "516 predates the SOFTWARE table")
        assertTrue(at(517).contains("Lumen"), "517 carries the software credit")
        assertTrue(at(516).contains(tiny.name), "the MACHINE table exists from 516")
    }

    @Test
    fun `a raster that does not match the panel is refused rather than scaled`() {
        // The engine slicing for its own default panel is exactly the failure this guards: the file
        // would claim one geometry while carrying another resolution's pixels.
        val wrong = ResinPrinter("Claims 32x32", 32, 32, 50.0, 20.0)
        val sl1 = sl1(tiny, listOf(mask(tiny, 0x00, 0xFF)))

        val e = assertFailsWith<IllegalArgumentException> {
            Sl1ToPw.convert(sl1, ResinSettings(), wrong, 10.0, 60, ByteArrayOutputStream(), extension = "pwmx")
        }
        assertTrue(e.message!!.contains("does not match"), e.message!!)
    }

    @Test
    fun `an SL1 with no layers is refused`() {
        assertFailsWith<IllegalArgumentException> {
            Sl1ToPw.convert(emptySl1(), ResinSettings(), tiny, 10.0, 60, ByteArrayOutputStream())
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** A layer mask with a band of [on] in the top half and [off] below. */
    private fun mask(p: ResinPrinter, on: Int, off: Int): ByteArray =
        ByteArray(p.resolutionX * p.resolutionY) { i -> if (i < p.resolutionX * p.resolutionY / 2) on.toByte() else off.toByte() }

    private fun sl1(p: ResinPrinter, masks: List<ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            masks.forEachIndexed { i, m ->
                zip.putNextEntry(ZipEntry(String.format("%08d.png", i)))
                val img = BufferedImage(p.resolutionX, p.resolutionY, BufferedImage.TYPE_BYTE_GRAY)
                img.raster.setPixels(0, 0, p.resolutionX, p.resolutionY, IntArray(m.size) { m[it].toInt() and 0xFF })
                ImageIO.write(img, "png", zip)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("config.ini"))
            zip.write("layerHeight = 0.05\nexpTime = 2.5\n".toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun emptySl1(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("config.ini"))
            zip.write("layerHeight = 0.05\n".toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }
}
