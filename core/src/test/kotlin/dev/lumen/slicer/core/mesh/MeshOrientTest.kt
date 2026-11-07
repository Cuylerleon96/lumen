package dev.lumen.slicer.core.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lay-flat / "align to face".
 *
 * The rule under test: rest the model on its **largest flat face**. Every assertion is about the
 * geometry that results, not about the rotation that produced it — a wrong rotation and a wrong face
 * choice both show up as the same thing, a model that is not sitting on a face.
 *
 * The invariant that catches a broken transform: rotation and translation preserve volume. If the
 * matrix is transposed, skewed or the angle is wrong, the volume moves.
 */
class MeshOrientTest {

    /** A box sitting on Z=0 (its 20x20 bottom face on the plate). */
    private fun box(w: Float, d: Float, h: Float) = MeshPrimitives.box(w, d, h, 0f, 0f, h / 2f)

    @Test
    fun `a box already on its face is left alone`() {
        val b = box(20f, 20f, 10f)

        val flat = MeshOrient.layFlat(b)

        assertEquals(0f, flat.bounds().min.z, 1e-3f)
        assertEquals(10f, flat.bounds().size.z, 1e-3f, "still resting on a 20x20 face")
        assertEquals(b.volumeMm3(), flat.volumeMm3(), 0.05, "a no-op must not deform the mesh")
    }

    @Test
    fun `a tilted box is laid back onto its largest face`() {
        val tilted = MeshOrient.rotate(box(20f, 20f, 10f), Vec3(1f, 0f, 0f), 30.0)
        assertTrue(tilted.bounds().size.z > 11f, "fixture must actually be tilted: ${tilted.bounds().size.z}")

        val flat = MeshOrient.layFlat(tilted)

        assertEquals(0f, flat.bounds().min.z, 1e-3f, "must end up on the plate")
        assertEquals(10f, flat.bounds().size.z, 1e-2f, "resting on the 20x20 face, so 10 mm tall")
        assertEquals(20f, flat.bounds().size.x, 1e-2f)
        assertEquals(20f, flat.bounds().size.y, 1e-2f)
        assertEquals(tilted.volumeMm3(), flat.volumeMm3(), 0.05, "rotation must not change the volume")
    }

    @Test
    fun `the largest face wins`() {
        // 40x40 faces (1600) beat the 40x5 sides (200), so it lies on the big one: 5 mm tall.
        val flat = MeshOrient.layFlat(MeshOrient.rotate(box(40f, 40f, 5f), Vec3(0f, 1f, 0f), 45.0))

        assertEquals(5f, flat.bounds().size.z, 1e-2f)
        assertEquals(40f, flat.bounds().size.x, 1e-2f)
    }

    @Test
    fun `lay flat is idempotent`() {
        val once = MeshOrient.layFlat(MeshOrient.rotate(box(20f, 20f, 10f), Vec3(1f, 1f, 0f), 37.0))

        val twice = MeshOrient.layFlat(once)

        assertEquals(once.bounds().min.z, twice.bounds().min.z, 1e-3f)
        assertEquals(once.bounds().size.z, twice.bounds().size.z, 1e-3f)
        assertEquals(once.volumeMm3(), twice.volumeMm3(), 0.05)
    }

    @Test
    fun `a face pointing up is flipped over rather than left upside down`() {
        // The 20x20 face is on top; lay-flat has to turn the model over (antiparallel case).
        val flipped = MeshOrientationHelpers.upsideDown(box(20f, 20f, 10f))

        val flat = MeshOrient.layFlat(flipped)

        assertEquals(0f, flat.bounds().min.z, 1e-3f)
        assertEquals(10f, flat.bounds().size.z, 1e-2f)
        assertEquals(flipped.volumeMm3(), flat.volumeMm3(), 0.05)
    }

    @Test
    fun `an empty mesh survives untouched`() {
        val empty = Mesh(FloatArray(0))

        assertEquals(0, MeshOrient.layFlat(empty).triangleCount)
        assertEquals(null, MeshOrient.dominantFlatFace(empty))
        assertEquals(0.0, MeshOrient.plateContactFraction(empty))
    }

    @Test
    fun `drop and centre puts the lowest point on the plate and the footprint on the origin`() {
        val off = MeshPrimitives.box(10f, 10f, 10f, 100f, -50f, 37f)

        val placed = MeshOrient.dropAndCentre(off)
        val b = placed.bounds()

        assertEquals(0f, b.min.z, 1e-3f)
        assertEquals(0f, (b.min.x + b.max.x) / 2f, 1e-3f)
        assertEquals(0f, (b.min.y + b.max.y) / 2f, 1e-3f)
        assertTrue(MeshOrient.isOnPlate(placed))
    }

    @Test
    fun `the dominant face is the one reported`() {
        val face = MeshOrient.dominantFlatFace(box(20f, 20f, 10f))

        assertTrue(face != null)
        assertEquals(400.0, face!!.areaMm2, 0.5, "the 20x20 face is 400 mm2")
        assertEquals(1.0, kotlin.math.abs(face.normal.z).toDouble(), 1e-3, "and it is vertical, up or down")
    }

    @Test
    fun `a resting box has a real contact patch, not a point`() {
        val flat = MeshOrient.layFlat(MeshOrient.rotate(box(20f, 20f, 10f), Vec3(1f, 0f, 0f), 25.0))

        // 6 of the 36 vertex entries belong to the face on the plate.
        assertTrue(
            MeshOrient.plateContactFraction(flat) > 0.1,
            "a model resting on a face has many vertices at Z=0, got ${MeshOrient.plateContactFraction(flat)}",
        )
    }
}

/** Small helpers the tests need but the library does not. */
private object MeshOrientationHelpers {
    /** The same box turned over, so its largest face points up. */
    fun upsideDown(m: Mesh): Mesh = MeshOrient.rotate(m, Vec3(1f, 0f, 0f), 180.0)
}
