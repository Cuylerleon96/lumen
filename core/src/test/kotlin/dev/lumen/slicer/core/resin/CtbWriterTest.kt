package dev.lumen.slicer.core.resin

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class CtbWriterTest {

    private fun params(bottom: Int) = CtbWriter.Params(
        printerOutMmX = 143.43f, printerOutMmY = 89.6f, printerOutMmZ = 175f,
        overallHeightMm = 20f, layerHeightMm = 0.05f,
        exposureS = 2.5f, bottomExposureS = 25f, lightOffS = 7f, bottomLightOffS = 7f,
        bottomLayerCount = bottom, resolutionX = 4098, resolutionY = 2560,
        printTimeS = 1515,
    )

    @Test
    fun `writes a parseable header with correct offsets and counts`() {
        val w = 8; val h = 4
        // 3 layers: two solid rows + one empty
        val solid = ByteArray(w * h) { 127 }
        val empty = ByteArray(w * h) { 0 }
        val layers = listOf(solid, empty, solid)

        val out = ByteArrayOutputStream()
        CtbWriter.write(out, w, h, layers, params(bottom = 1))
        val bytes = out.toByteArray()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0x12FD0086, b.getInt(0x00), "magic")
        assertEquals(2, b.getInt(0x04), "version")
        assertEquals(4098f, b.getFloat(0x34), "resX")
        assertEquals(3, b.getInt(0x44), "layer_table_count")

        // ext_config offset must be inside the file and point at a plausible record
        val extOff = b.getInt(0x54)
        assert(extOff in 0x70 until bytes.size) { "ext_config_offset $extOff out of range" }
        // layer table offset must be inside the file
        val tableOff = b.getInt(0x40)
        assert(tableOff in 0x70 until bytes.size) { "layer_table_offset $tableOff out of range" }

        // each layer record: z = layerHeight*(i+1); data offset must be inside the file
        for (i in 0 until 3) {
            val rec = tableOff + i * 36
            assertEquals(0.05f * (i + 1), b.getFloat(rec), 1e-4f, "layer $i z")
            val dataOff = b.getInt(rec + 0x0C)
            val dataLen = b.getInt(rec + 0x10)
            assert(dataOff in 0x70 until bytes.size) { "layer $i data offset $dataOff out of range" }
            assert(dataLen > 0) { "layer $i empty data" }
        }
    }

    @Test
    fun `empty layer RLE-encodes smaller than solid layer`() {
        // a 64x64 all-zero layer is one run; a checkerboard is many single pixels
        val w = 64; val h = 64
        val empty = ByteArray(w * h) { 0 }
        val checker = ByteArray(w * h) { if ((it % 2) == 0) 127 else 0 }
        val emptyOut = ByteArrayOutputStream(); CtbWriter.write(emptyOut, w, h, listOf(empty), params(0))
        val checkOut = ByteArrayOutputStream(); CtbWriter.write(checkOut, w, h, listOf(checker), params(0))
        assert(emptyOut.size() < checkOut.size()) { "run-length should compress the empty layer more" }
    }
}
