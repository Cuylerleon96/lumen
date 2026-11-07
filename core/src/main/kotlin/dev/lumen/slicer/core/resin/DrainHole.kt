package dev.lumen.slicer.core.resin

import dev.lumen.slicer.core.mesh.Csg
import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.MeshPrimitives
import dev.lumen.slicer.core.mesh.Vec3
import kotlinx.serialization.Serializable

/**
 * A drain hole: a cylinder of [radiusMm] drilled from [point] along the **inward** direction
 * ([dx],[dy],[dz]).
 *
 * Resin flows out of, and air flows into, a hollowed model through these — without at least two
 * per cavity the print becomes a sealed pressure vessel that sags, blobs, or rips off the build
 * plate mid-print.
 *
 * Coordinates are in **model space, millimetres** (they ride through the same placement transform
 * as the mesh). Plain floats rather than a [Vec3] so the whole list round-trips through the project
 * archive ([ProjectArchive]).
 */
@Serializable
data class DrainHole(
    val x: Float,
    val y: Float,
    val z: Float,
    /** Inward unit vector from the tapped surface point. */
    val dx: Float,
    val dy: Float,
    val dz: Float,
    val radiusMm: Float = 1.0f,
    /** Drill length along the direction in mm; `0` = straight through the whole model. */
    val depthMm: Float = 0f,
) {
    val point: Vec3 get() = Vec3(x, y, z)
    val inward: Vec3 get() = Vec3(dx, dy, dz)
}

/**
 * Drilling drain holes into a mesh, using the [Csg] boolean subtractor.
 *
 * Order matters when this meets the SLA engine: drill **before** slicing and leave hollowing
 * enabled. The engine hollows the *already-drilled* solid, so the tunnel survives as a passage
 * between the cavity and the outside (hollowing erodes a shell off every surface, which widens the
 * bore rather than sealing it). Post-processing holes into finished layers cannot reconnect a sealed
 * cavity.
 */
object DrainHoles {

    /**
     * How far the drill bit starts *outside* the tapped point. Without the outset the bit's end cap
     * lands exactly on the surface triangle — a coplanar face, which is the one case a BSP boolean
     * has to resolve by tolerance luck.
     */
    const val OUTSET_MM = 0.5f

    /** Radial resolution of the drill bit. 48 segments ≈ 0.06 mm chord error at r = 0.5 mm. */
    const val SEGMENTS = 48

    /**
     * Return [mesh] with every hole in [holes] drilled through it.
     *
     * Each hole is one local boolean as a **separate** operation (not a union of all drills first):
     * a UserContent with several holes is rare, the operation is local, and sequencing keeps the
     * per-step surface count — and therefore memory on a phone — small.
     *
     * @param wallThicknessMm used only to pick a sane default depth when a hole is deeper than the
     *   wall but short of a full through-drill; see [DrainHole.depthMm].
     */
    fun drill(mesh: Mesh, holes: List<DrainHole>, wallThicknessMm: Float = 2f): Mesh {
        var out = mesh
        for (hole in holes) {
            val dir = hole.inward.normalized()
            val length = if (hole.depthMm > 0f) {
                hole.depthMm + OUTSET_MM
            } else {
                throughLength(out, dir) + 2f * OUTSET_MM
            }
            val bit = MeshPrimitives.cylinderAlong(
                radiusMm = hole.radiusMm,
                lengthMm = length,
                origin = hole.point - dir * OUTSET_MM,
                direction = dir,
                segments = SEGMENTS,
            )
            // Margin: a few mm of surrounding surface, so the bit is unambiguously inside the
            // retained patch (see Csg.subtractLocal).
            val margin = maxOf(3f, hole.radiusMm + 3f, wallThicknessMm + 2f)
            out = Csg.subtractLocal(out, bit, marginMm = margin)
        }
        return out
    }

    /** Extent of [mesh] along [dir] (mm) — the length of a complete through-drill. */
    private fun throughLength(mesh: Mesh, dir: Vec3): Float {
        val b = mesh.bounds()
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (xi in 0..1) {
            for (yi in 0..1) {
                for (zi in 0..1) {
                    val corner = Vec3(
                        if (xi == 0) b.min.x else b.max.x,
                        if (yi == 0) b.min.y else b.max.y,
                        if (zi == 0) b.min.z else b.max.z,
                    )
                    val t = corner.dot(dir)
                    if (t < lo) lo = t
                    if (t > hi) hi = t
                }
            }
        }
        return (hi - lo).coerceAtLeast(0f)
    }
}
