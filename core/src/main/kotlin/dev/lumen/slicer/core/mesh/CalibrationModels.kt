package dev.lumen.slicer.core.mesh

/**
 * Calibration / test-print models (L): procedurally generated STLs for verifying a printer's
 * dimensional accuracy and resin exposure — no cloud, no bundled assets.
 */
object CalibrationModels {

    /** Axis-aligned box centred at ([cx],[cy],[cz]) with the given size, as a 12-triangle mesh. */
    fun box(w: Float, d: Float, h: Float, cx: Float = 0f, cy: Float = 0f, cz: Float = 0f): Mesh {
        val x0 = cx - w / 2; val x1 = cx + w / 2
        val y0 = cy - d / 2; val y1 = cy + d / 2
        val z0 = cz - h / 2; val z1 = cz + h / 2
        val v = listOf(
            floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0),
            floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1),
        )
        val faces = intArrayOf(
            0, 2, 1, 0, 3, 2,   // bottom
            4, 5, 6, 4, 6, 7,   // top
            0, 1, 5, 0, 5, 4,   // front
            1, 2, 6, 1, 6, 5,   // right
            2, 3, 7, 2, 7, 6,   // back
            3, 0, 4, 3, 4, 7,   // left
        )
        val out = ArrayList<Float>(faces.size * 3)
        for (idx in faces) { out += v[idx][0]; out += v[idx][1]; out += v[idx][2] }
        return Mesh(out.toFloatArray())
    }

    /** A [sizeMm] cube sitting on Z=0 (verify XY/Z scale). */
    fun dimensionCube(sizeMm: Float = 20f): Mesh = box(sizeMm, sizeMm, sizeMm, 0f, 0f, sizeMm / 2)

    /**
     * A row of [steps] cubes of increasing size on Z=0 — an exposure/tolerance bar. Smaller cubes
     * need less exposure to resolve; a well-calibrated printer prints every step down to its limit.
     */
    fun stepBar(steps: Int = 8, startMm: Float = 0.3f, stepMm: Float = 0.3f, gapMm: Float = 1.0f): Mesh {
        val tris = ArrayList<Float>()
        var x = 0f
        for (i in 0 until steps) {
            val s = startMm + i * stepMm
            val b = box(s, s, s, 0f, 0f, s / 2)
            for (k in b.positions.indices step 3) {
                tris += b.positions[k] + x; tris += b.positions[k + 1]; tris += b.positions[k + 2]
            }
            x += s + gapMm
        }
        return Mesh(tris.toFloatArray())
    }
}
