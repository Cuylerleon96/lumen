package dev.lumen.slicer.core.mesh

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orientation helpers — "align to face" / lay-flat (tracker B: `"align to face"/lay-flat`).
 *
 * Lay-flat answers the question a user actually has when a model arrives at an arbitrary angle: *which
 * way is down?* The rule used here is the one that needs no AI and no printing knowledge — **rest the
 * model on its largest flat face**. That is what a person does by hand: look for the big flat bit and
 * put it on the plate. It is deliberately not "minimise supports" (a much harder optimisation, and one
 * that needs the support generator in the loop).
 *
 * Two things make this honest rather than magic:
 *  - A face is only *flat* if its triangles agree on a normal within [angleToleranceDeg]. The model's
 *    own curvature is not mistaken for a plane.
 *  - The chosen face is reported ([FlatFace]) so the UI can show *why* it rotated, and so a wrong
 *    guess is diagnosable instead of mysterious.
 */
object MeshOrient {

    /** The flat face the model was laid on: its outward [normal] and total [areaMm2]. */
    data class FlatFace(val normal: Vec3, val areaMm2: Double)

    /** Down, in the printer's frame: a face resting on the plate has its outward normal pointing here. */
    private val DOWN = Vec3(0f, 0f, -1f)

    /**
     * The largest flat face of [mesh], or null when the mesh is empty.
     *
     * Triangles are bucketed by quantised normal; the bucket holding the most **area** wins, and its
     * representative normal is the area-weighted mean of its members. Quantisation is 2% per axis,
     * which is far tighter than any real flat face's numerical noise and far looser than the angle
     * between genuinely different faces.
     */
    fun dominantFlatFace(mesh: Mesh, angleToleranceDeg: Double = 12.0): FlatFace? {
        if (mesh.isEmpty) return null
        val cosTol = cos(Math.toRadians(angleToleranceDeg)).toFloat()
        val buckets = LinkedHashMap<String, MutableList<Pair<Vec3, Double>>>()
        val p = mesh.positions
        var i = 0
        while (i < p.size) {
            val a = Vec3(p[i], p[i + 1], p[i + 2])
            val b = Vec3(p[i + 3], p[i + 4], p[i + 5])
            val c = Vec3(p[i + 6], p[i + 7], p[i + 8])
            val raw = (b - a).cross(c - a)
            val len = raw.length()
            if (len > 1e-9f) {
                val n = raw * (1f / len)
                val area = 0.5 * len
                val key = "${Math.round(n.x * 50)},${Math.round(n.y * 50)},${Math.round(n.z * 50)}"
                buckets.getOrPut(key) { ArrayList() }.add(n to area.toDouble())
            }
            i += Mesh.FLOATS_PER_TRIANGLE
        }
        if (buckets.isEmpty()) return null

        // Merge buckets whose normals are within tolerance (quantisation can split one face in two).
        val merged = ArrayList<Pair<Vec3, Double>>()
        for ((_, members) in buckets) {
            val areaSum = members.sumOf { it.second }
            var nx = 0f; var ny = 0f; var nz = 0f
            for ((n, a) in members) {
                nx += n.x * a.toFloat(); ny += n.y * a.toFloat(); nz += n.z * a.toFloat()
            }
            val avg = Vec3(nx, ny, nz).normalized()
            var placed = false
            for (k in merged.indices) {
                if (merged[k].first.dot(avg) >= cosTol) {
                    val (mn, ma) = merged[k]
                    val w = ma + areaSum
                    merged[k] = Vec3(
                        mn.x * ma.toFloat() + avg.x * areaSum.toFloat(),
                        mn.y * ma.toFloat() + avg.y * areaSum.toFloat(),
                        mn.z * ma.toFloat() + avg.z * areaSum.toFloat(),
                    ).normalized() to w
                    placed = true
                    break
                }
            }
            if (!placed) merged.add(avg to areaSum)
        }

        val best = merged.maxByOrNull { it.second } ?: return null
        // A face that is neither up nor down cannot be laid on: only faces pointing *up or down*
        // relative to the plate can rest on it. Everything else gets rotated onto the plate by the
        // caller, which is the point -- so no filtering here, the largest face always wins.
        return FlatFace(best.first, best.second)
    }

    /**
     * Rotate [mesh] so its largest flat face rests on the plate, then drop it onto Z=0 and centre it
     * on X/Y. Returns the mesh unchanged when it has no usable face (an empty or degenerate mesh).
     */
    fun layFlat(mesh: Mesh, angleToleranceDeg: Double = 12.0): Mesh {
        val face = dominantFlatFace(mesh, angleToleranceDeg) ?: return mesh
        val n = face.normal
        // Rotate the face normal onto DOWN (axis-angle). Antiparallel needs a special axis: the cross
        // product vanishes there and acos is 180 degrees.
        val dot = n.dot(DOWN).coerceIn(-1f, 1f)
        val rotated = when {
            dot > 1f - 1e-6f -> mesh                       // already face-down
            dot < -1f + 1e-6f -> rotate(mesh, Vec3(1f, 0f, 0f), 180.0) // face-up: flip over
            else -> {
                val axis = n.cross(DOWN)
                val angleDeg = Math.toDegrees(acos(dot.toDouble()))
                rotate(mesh, axis, angleDeg)
            }
        }
        return dropAndCentre(rotated)
    }

    /** Rotate [mesh] about [axis] by [angleDeg] degrees (Rodrigues), axis need not be unit. */
    fun rotate(mesh: Mesh, axis: Vec3, angleDeg: Double): Mesh {
        val a = axis.normalized()
        val r = Math.toRadians(angleDeg)
        val c = cos(r).toFloat(); val s = sin(r).toFloat(); val t = 1f - c
        val (x, y, z) = Triple(a.x, a.y, a.z)
        return mesh.map { px, py, pz, out ->
            out[0] = (t * x * x + c) * px + (t * x * y - s * z) * py + (t * x * z + s * y) * pz
            out[1] = (t * x * y + s * z) * px + (t * y * y + c) * py + (t * y * z - s * x) * pz
            out[2] = (t * x * z - s * y) * px + (t * y * z + s * x) * py + (t * z * z + c) * pz
        }
    }

    /** Translate [mesh] so its lowest point sits on Z=0 and its X/Y footprint is centred on the origin. */
    fun dropAndCentre(mesh: Mesh): Mesh {
        if (mesh.isEmpty) return mesh
        val b = mesh.bounds()
        val dx = -(b.min.x + b.max.x) / 2f
        val dy = -(b.min.y + b.max.y) / 2f
        val dz = -b.min.z
        if (abs(dx) < 1e-6f && abs(dy) < 1e-6f && abs(dz) < 1e-6f) return mesh
        return mesh.map { x, y, z, out -> out[0] = x + dx; out[1] = y + dy; out[2] = z + dz }
    }

    /** Is [mesh] already resting on a face? True when its lowest point is on Z=0 within [epsMm]. */
    fun isOnPlate(mesh: Mesh, epsMm: Float = 1e-3f): Boolean =
        !mesh.isEmpty && abs(mesh.bounds().min.z) <= epsMm

    /** Rough check that [mesh] rests on a *flat* face rather than a point: how much of its footprint
     *  is within [bandMm] of Z=0. Returns the fraction 0..1 of vertices in that band. */
    fun plateContactFraction(mesh: Mesh, bandMm: Float = 0.2f): Double {
        if (mesh.isEmpty) return 0.0
        val p = mesh.positions
        var inBand = 0; var total = 0
        var i = 0
        while (i < p.size) {
            if (abs(p[i + 2]) <= bandMm) inBand++
            total++
            i += 3
        }
        return if (total == 0) 0.0 else inBand.toDouble() / total
    }
}
