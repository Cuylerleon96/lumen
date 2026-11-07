package io.github.whoxllm.orcamicro.core.resin

import io.github.whoxllm.orcamicro.core.mesh.Mesh
import kotlin.math.floor

/**
 * Naive MSLA rasterizer: intersects the mesh with each layer plane and scanline-fills the
 * cross-section into an 8-bit grayscale image. **No supports, no hollowing, no anti-aliasing** —
 * binary layers only. It exists to produce a printable (unsupported) .sl1 and prove the format
 * end to end; the real PrusaSlicer SLA engine replaces it for production slicing.
 *
 * Layers are produced one at a time so the caller can stream them straight into the archive.
 * Assumes square pixels and the model sitting on the plate (z ≥ 0).
 */
object MeshRasterizer {

    /** Number of layers spanning the model's height for the given layer height. */
    fun layerCount(mesh: Mesh, settings: ResinSettings): Int {
        val top = mesh.bounds().max.z.toDouble()
        return (top / settings.layerHeightMm).toInt().coerceAtLeast(1)
    }

    /** Rasterizes layer [index] (0-based). Layer i is centred at z = (i + 0.5)·layerHeight. */
    fun rasterizeLayer(mesh: Mesh, printer: ResinPrinter, settings: ResinSettings, index: Int): LayerImage {
        val h = settings.layerHeightMm
        val z = (index + 0.5) * h
        val b = mesh.bounds()
        val w = printer.resolutionX
        val ht = printer.resolutionY
        val mmPerPx = printer.pixelSizeMicrons / 1000.0
        val cx = (b.min.x + b.max.x) / 2.0
        val cy = (b.min.y + b.max.y) / 2.0

        val segments = ArrayList<DoubleArray>(mesh.triangleCount)
        cut(mesh, z, segments)

        val px = ByteArray(w * ht)
        fill(segments, w, ht, mmPerPx, cx, cy, px)
        return LayerImage(w, ht, px)
    }

    /** Collects the triangle/plane intersection segments at height [z] as [x1,y1,x2,y2] quads. */
    private fun cut(mesh: Mesh, z: Double, out: MutableList<DoubleArray>) {
        val p = mesh.positions
        var i = 0
        while (i < p.size) {
            cutTriangle(
                p[i].toDouble(), p[i + 1].toDouble(), p[i + 2].toDouble(),
                p[i + 3].toDouble(), p[i + 4].toDouble(), p[i + 5].toDouble(),
                p[i + 6].toDouble(), p[i + 7].toDouble(), p[i + 8].toDouble(),
                z, out,
            )
            i += 9
        }
    }

    private fun cutTriangle(
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        z: Double, out: MutableList<DoubleArray>,
    ) {
        val px = doubleArrayOf(x0, x1, x2)
        val py = doubleArrayOf(y0, y1, y2)
        val pz = doubleArrayOf(z0, z1, z2)
        val ix = DoubleArray(3)
        val iy = DoubleArray(3)
        var n = 0
        for (e in 0 until 3) {
            val a = e
            val b = (e + 1) % 3
            val da = pz[a] - z
            val db = pz[b] - z
            if ((da < 0 && db >= 0) || (da >= 0 && db < 0)) {
                val t = da / (da - db)
                ix[n] = px[a] + (px[b] - px[a]) * t
                iy[n] = py[a] + (py[b] - py[a]) * t
                n++
            }
        }
        if (n == 2) out.add(doubleArrayOf(ix[0], iy[0], ix[1], iy[1]))
    }

    /** Even-odd scanline fill of the segments into [px] (row-major, +Y is up). */
    private fun fill(
        segments: List<DoubleArray>, w: Int, ht: Int,
        mmPerPx: Double, cx: Double, cy: Double, px: ByteArray,
    ) {
        val halfH = ht / 2.0
        val halfW = w / 2.0
        val xs = ArrayList<Double>(8)
        for (row in 0 until ht) {
            val y = cy + (halfH - row - 0.5) * mmPerPx
            xs.clear()
            for (s in segments) {
                val y1 = s[1]
                val y2 = s[3]
                if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
                    val t = (y - y1) / (y2 - y1)
                    xs.add(s[0] + (s[2] - s[0]) * t)
                }
            }
            if (xs.size < 2) continue
            xs.sort()
            var k = 0
            while (k + 1 < xs.size) {
                val ca = floor((xs[k] - cx) / mmPerPx + halfW).toInt().coerceIn(0, w - 1)
                val cb = floor((xs[k + 1] - cx) / mmPerPx + halfW).toInt().coerceIn(0, w - 1)
                var c = ca
                val base = row * w
                while (c <= cb) {
                    px[base + c] = 0xFF.toByte()
                    c++
                }
                k += 2
            }
        }
    }
}
