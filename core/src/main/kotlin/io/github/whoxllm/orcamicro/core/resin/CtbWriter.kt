package io.github.whoxllm.orcamicro.core.resin

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room **CTB** (Chitubox / Elegoo / Creality / Phrozen) writer.
 *
 * Format per the open reverse-engineering spec (cbiffle/catibo `cbddlp-ctb.adoc`): fixed 0x70-byte
 * header + ExtConfig + ExtConfig2, two RGB565 (RLE15) previews, a table of 36-byte LayerHeaders,
 * then RLE7 layer blobs. **Unencrypted** (encryption_key = 0 disables the vendor cipher — the spec
 * documents that proprietary software honors key=0).
 *
 * Layer pixels are **7-bit (0 empty … 127 full)**; with AA off, only 0/127.
 */
object CtbWriter {

    private const val MAGIC_CTB = 0x12FD0086
    private const val HEADER_SIZE = 0x70
    private const val EXT_CONFIG_SIZE = 0x30
    private const val EXT_CONFIG2_SIZE = 0x3C
    private const val IMAGE_HEADER_SIZE = 0x20
    private const val LAYER_HEADER_SIZE = 36
    private const val LARGE_PREVIEW_W = 400; private const val LARGE_PREVIEW_H = 300
    private const val SMALL_PREVIEW_W = 200; private const val SMALL_PREVIEW_H = 125

    /** Print parameters written into the header/ExtConfig and each layer record. */
    data class Params(
        val printerOutMmX: Float,
        val printerOutMmY: Float,
        val printerOutMmZ: Float,
        val overallHeightMm: Float,
        val layerHeightMm: Float,
        val exposureS: Float,
        val bottomExposureS: Float,
        val lightOffS: Float,
        val bottomLightOffS: Float,
        val bottomLayerCount: Int,
        val resolutionX: Int,
        val resolutionY: Int,
        val printTimeS: Int,
        val bottomLiftDistMm: Float = 5f,
        val liftDistMm: Float = 5f,
        val bottomLiftSpeedMmpm: Float = 60f,
        val liftSpeedMmpm: Float = 60f,
        val retractSpeedMmpm: Float = 150f,
        val resinVolumeMl: Float = 0f,
        val resinMassG: Float = 0f,
        val machineType: String = "mslicer",
    )

    /**
     * @param layers each layer is `width*height` bytes, values 0..127 (row-major, top-left origin)
     */
    fun write(out: OutputStream, width: Int, height: Int, layers: List<ByteArray>, p: Params) {
        require(layers.all { it.size == width * height }) { "layer size != ${width}x$height" }
        writeEncoded(out, width, height, layers.map { encodeRle7(it) }, p)
    }

    /**
     * Streaming-friendly variant: [encodedLayers] are already RLE7-encoded blobs (see [encodeRle7]).
     * Callers that decode layers one at a time (e.g. from an SL1) use this to avoid holding every
     * full-resolution mask in memory at once.
     */
    fun writeEncoded(out: OutputStream, width: Int, height: Int, encodedLayers: List<ByteArray>, p: Params) {
        val layers = encodedLayers

        val buf = ByteArrayOutputStream()
        // --- fixed header (0x70) ---
        val hdr = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        hdr.putInt(0x00, MAGIC_CTB)
        hdr.putInt(0x04, 2)                       // version
        hdr.putFloat(0x08, p.printerOutMmX)
        hdr.putFloat(0x0C, p.printerOutMmY)
        hdr.putFloat(0x10, p.printerOutMmZ)
        // 0x14..0x18 zeroes
        hdr.putFloat(0x1C, p.overallHeightMm)
        hdr.putFloat(0x20, p.layerHeightMm)
        hdr.putFloat(0x24, p.exposureS)
        hdr.putFloat(0x28, p.bottomExposureS)
        hdr.putFloat(0x2C, p.lightOffS)
        hdr.putInt(0x30, p.bottomLayerCount)
        hdr.putFloat(0x34, p.resolutionX.toFloat())
        hdr.putFloat(0x38, p.resolutionY.toFloat())
        // 0x3C large_preview_offset, 0x40 layer_table_offset, 0x44 layer_table_count,
        // 0x48 small_preview_offset, 0x4C print_time_s — patched below
        hdr.putInt(0x50, 1)                       // projection = mirrored (LCD)
        hdr.putInt(0x54, 0)                       // ext_config_offset (patched)
        hdr.putInt(0x58, EXT_CONFIG_SIZE)
        hdr.putInt(0x5C, 1)                       // level_set_count
        hdr.putShort(0x60, 0xFF.toShort())        // pwm_level
        hdr.putShort(0x62, 0xFF.toShort())        // bot_pwm_level
        hdr.putInt(0x64, 0)                       // encryption_key = 0 (disable cipher)
        hdr.putInt(0x68, 0)                       // ext_config2_offset (patched)
        hdr.putInt(0x6C, EXT_CONFIG2_SIZE)
        buf.write(hdr.array())

        // --- ExtConfig (0x30) ---
        val ext = ByteBuffer.allocate(EXT_CONFIG_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        ext.putFloat(0x00, p.bottomLiftDistMm)
        ext.putFloat(0x04, p.bottomLiftSpeedMmpm)
        ext.putFloat(0x08, p.liftDistMm)
        ext.putFloat(0x0C, p.liftSpeedMmpm)
        ext.putFloat(0x10, p.retractSpeedMmpm)
        ext.putFloat(0x14, p.resinVolumeMl)
        ext.putFloat(0x18, p.resinMassG)
        ext.putFloat(0x1C, 0f)                    // resin_cost
        ext.putFloat(0x20, p.bottomLightOffS)
        ext.putFloat(0x24, p.lightOffS)
        ext.putInt(0x28, p.bottomLayerCount)
        val extConfigOffset = buf.size()
        buf.write(ext.array())

        // --- machine-type string (ASCII) ---
        val machineBytes = p.machineType.toByteArray(Charsets.US_ASCII)
        val machineTypeOffset = buf.size()
        buf.write(machineBytes)

        // --- ExtConfig2 (0x3C) ---
        val ext2 = ByteBuffer.allocate(EXT_CONFIG2_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        ext2.putInt(0x1C, machineTypeOffset)
        ext2.putInt(0x20, machineBytes.size)
        ext2.putInt(0x24, 0xF)                    // encryption_mode (ctb)
        ext2.putInt(0x28, 0)                      // mysterious_id
        ext2.putInt(0x2C, 1)                      // antialias_level
        ext2.putInt(0x30, 0x01060300)             // software_version
        ext2.putInt(0x34, 0x200)
        val extConfig2Offset = buf.size()
        buf.write(ext2.array())

        // --- previews (ImageHeader + RLE15) ---
        val largePreviewOffset = buf.size()
        val largeData = preview(blankPreview(LARGE_PREVIEW_W, LARGE_PREVIEW_H))
        buf.write(imageHeader(LARGE_PREVIEW_W, LARGE_PREVIEW_H, buf.size() + IMAGE_HEADER_SIZE, largeData.size))
        buf.write(largeData)

        val smallPreviewOffset = buf.size()
        val smallData = preview(blankPreview(SMALL_PREVIEW_W, SMALL_PREVIEW_H))
        buf.write(imageHeader(SMALL_PREVIEW_W, SMALL_PREVIEW_H, buf.size() + IMAGE_HEADER_SIZE, smallData.size))
        buf.write(smallData)

        // --- layer table ---
        val layerTableOffset = buf.size()
        val layerTableCount = layers.size
        val tableStart = buf.size()
        buf.write(ByteArray(layerTableCount * LAYER_HEADER_SIZE)) // reserve; patched as we write data

        // --- layer data blobs ---
        val table = ByteBuffer.wrap(buf.toByteArray(), tableStart, layerTableCount * LAYER_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        for ((i, layer) in layers.withIndex()) {
            val bottom = i < p.bottomLayerCount
            val z = p.layerHeightMm * (i + 1)
            val expo = if (bottom) p.bottomExposureS else p.exposureS
            val off = if (bottom) p.bottomLightOffS else p.lightOffS
            val data = layer   // already RLE7-encoded
            val dataOffset = buf.size()
            buf.write(data)
            val rec = i * LAYER_HEADER_SIZE
            table.putFloat(rec + 0x00, z)
            table.putFloat(rec + 0x04, expo)
            table.putFloat(rec + 0x08, off)
            table.putInt(rec + 0x0C, dataOffset)
            table.putInt(rec + 0x10, data.size)
        }
        table.position(0)
        val finalBytes = buf.toByteArray()
        System.arraycopy(table.array(), 0, finalBytes, tableStart, layerTableCount * LAYER_HEADER_SIZE)

        // --- patch header offsets ---
        val patch = ByteBuffer.wrap(finalBytes).order(ByteOrder.LITTLE_ENDIAN)
        patch.putInt(0x3C, largePreviewOffset)
        patch.putInt(0x40, layerTableOffset)
        patch.putInt(0x44, layerTableCount)
        patch.putInt(0x48, smallPreviewOffset)
        patch.putInt(0x4C, p.printTimeS)
        patch.putInt(0x54, extConfigOffset)
        patch.putInt(0x68, extConfig2Offset)

        out.write(finalBytes)
        out.flush()
    }

    private fun imageHeader(w: Int, h: Int, dataOffset: Int, dataLen: Int): ByteArray {
        val b = ByteBuffer.allocate(IMAGE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x00, w); b.putInt(0x04, h); b.putInt(0x08, dataOffset); b.putInt(0x0C, dataLen)
        return b.array()
    }

    private fun blankPreview(w: Int, h: Int): IntArray = IntArray(w * h) { 0 } // black RGB565

    /** RGB565 preview with the run flag in green's LSB (RLE15). */
    private fun preview(pixels: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < pixels.size) {
            var run = 1
            while (i + run < pixels.size && pixels[i + run] == pixels[i] && run < 0xFFE) run++
            val rgb = pixels[i]
            if (run == 1) {
                writeShortLE(out, rgb)
            } else {
                writeShortLE(out, rgb or 0x20)   // set run flag (bit 5)
                writeShortLE(out, 0x3000 or (run - 1))
            }
            i += run
        }
        return out.toByteArray()
    }

    private fun writeShortLE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
    }

    /** RLE7: bits 6:0 = value, bit 7 = run flag; run length follows, big-endian with MSB prefixes. */
    fun encodeRle7(layer: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < layer.size) {
            val v = layer[i].toInt() and 0x7F
            var run = 1
            while (i + run < layer.size && (layer[i + run].toInt() and 0x7F) == v) run++
            if (run == 1) {
                out.write(v)                    // MSB 0 → single pixel
            } else {
                out.write(v or 0x80)            // MSB 1 → run follows
                // run length in the smallest big-endian form that fits (7/14/21/28-bit)
                when {
                    run < (1 shl 7) -> out.write(run and 0x7F)
                    run < (1 shl 14) -> {
                        out.write(0x80 or ((run shr 8) and 0x3F)); out.write(run and 0xFF)
                    }
                    run < (1 shl 21) -> {
                        out.write(0xC0 or ((run shr 16) and 0x1F)); out.write((run shr 8) and 0xFF); out.write(run and 0xFF)
                    }
                    else -> {
                        out.write(0xE0 or ((run shr 24) and 0x0F)); out.write((run shr 16) and 0xFF)
                        out.write((run shr 8) and 0xFF); out.write(run and 0xFF)
                    }
                }
            }
            i += run
        }
        return out.toByteArray()
    }
}
