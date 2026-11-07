package dev.lumen.slicer.core.mesh

import kotlin.math.abs

/**
 * Boolean (CSG) operations on triangle meshes — the BSP-tree algorithm (Naylor/Thibault,
 * popularised by `csg.js`), reimplemented clean-room in Kotlin.
 *
 * This is the mesh engine behind **drain holes** ([dev.lumen.slicer.core.resin.DrainHoles] drills
 * a cylinder through the shell) and the general boolean-CSG prep tool (union / subtract /
 * intersect).
 *
 * Every operation is plane clipping + polygon splitting, so it only needs **convex** polygons —
 * triangles, plus the convex fragments splitting produces.
 *
 * Robustness notes (the two constants are the whole ballgame):
 *  - [EPS] is the plane-side tolerance in **millimetres**. A vertex within EPS of a plane counts as
 *    coplanar. Too small → slivers and jitter; too large → detail gets rounded off. 1e-4 mm is far
 *    below print resolution yet comfortably above float noise at 100 mm model scale.
 *  - [MAX_BUILD_DEPTH] bounds tree recursion, so a degenerate soup (thousands of near-coplanar
 *    triangles) degrades to a coarse leaf instead of blowing the stack.
 */
object Csg {

    /** Plane-side tolerance in mm — see the class note. */
    const val EPS: Float = 1e-4f

    private const val MAX_BUILD_DEPTH = 256

    private const val COPLANAR = 0
    private const val FRONT = 1
    private const val BACK = 2
    private const val SPANNING = 3

    /** A plane in Hessian normal form: the set of points `p` with `dot(n, p) == w`. */
    class Plane(val n: Vec3, val w: Float) {
        fun flipped() = Plane(Vec3(-n.x, -n.y, -n.z), -w)

        fun distance(p: Vec3): Float = n.dot(p) - w

        companion object {
            /** The plane through three points; a degenerate (collinear) triangle gets a +Z plane. */
            fun from(a: Vec3, b: Vec3, c: Vec3): Plane {
                val raw = (b - a).cross(c - a)
                val len = raw.length()
                if (len < 1e-12f) return Plane(Vec3(0f, 0f, 1f), a.z)
                val n = raw * (1f / len)
                return Plane(n, n.dot(a))
            }
        }
    }

    /** A convex polygon: vertices in order, wound counter-clockwise about [plane]'s normal. */
    class Polygon(val vertices: List<Vec3>, val plane: Plane) {
        fun flipped(): Polygon = Polygon(vertices.asReversed(), plane.flipped())
    }

    /** Which boolean to run — see [combine]. */
    enum class Operation { UNION, SUBTRACT, INTERSECT }

    /**
     * A BSP node: a dividing [plane], the [polygons] coplanar with it, and the [front]/[back]
     * subtrees. `null` plane = an empty leaf.
     */
    class Node(polygons: List<Polygon> = emptyList()) {
        var plane: Plane? = null
        var front: Node? = null
        var back: Node? = null
        val polygons = ArrayList<Polygon>()

        init {
            if (polygons.isNotEmpty()) build(polygons)
        }

        /** Flip every polygon and swap the two half-spaces (inside <-> outside). */
        fun invert() {
            for (i in polygons.indices) polygons[i] = polygons[i].flipped()
            plane = plane?.flipped()
            front?.invert()
            back?.invert()
            val swap = front
            front = back
            back = swap
        }

        /** Drop the parts of [input] that are inside this solid; return the remaining fragments. */
        fun clipPolygons(input: List<Polygon>): List<Polygon> {
            val pl = plane ?: return input
            var fr = ArrayList<Polygon>()
            var bk = ArrayList<Polygon>()
            for (p in input) splitPolygon(pl, p, fr, bk, fr, bk)
            if (front != null) fr = ArrayList(front!!.clipPolygons(fr))
            bk = if (back != null) ArrayList(back!!.clipPolygons(bk)) else ArrayList()
            fr.addAll(bk)
            return fr
        }

        /** Remove everything of this solid that lies inside [other]. */
        fun clipTo(other: Node) {
            val self = ArrayList(polygons)
            polygons.clear()
            polygons.addAll(other.clipPolygons(self))
            front?.clipTo(other)
            back?.clipTo(other)
        }

        /** Every polygon in the tree (the solid's whole boundary). */
        fun allPolygons(): List<Polygon> {
            val out = ArrayList<Polygon>(polygons)
            front?.let { out.addAll(it.allPolygons()) }
            back?.let { out.addAll(it.allPolygons()) }
            return out
        }

        /** Build the tree from [input]; a leaf absorbs everything left at [MAX_BUILD_DEPTH]. */
        fun build(input: List<Polygon>, depth: Int = 0) {
            if (input.isEmpty()) return
            if (plane == null) plane = input[0].plane
            if (depth >= MAX_BUILD_DEPTH) {
                polygons.addAll(input)
                return
            }
            val pl = plane!!
            val fr = ArrayList<Polygon>()
            val bk = ArrayList<Polygon>()
            for (p in input) splitPolygon(pl, p, polygons, polygons, fr, bk)
            if (fr.isNotEmpty()) {
                if (front == null) front = Node()
                front!!.build(fr, depth + 1)
            }
            if (bk.isNotEmpty()) {
                if (back == null) back = Node()
                back!!.build(bk, depth + 1)
            }
        }
    }

    /** Sort [polygon] into the four buckets around [plane] (splitting it if it spans). */
    private fun splitPolygon(
        plane: Plane,
        polygon: Polygon,
        coplanarFront: MutableList<Polygon>,
        coplanarBack: MutableList<Polygon>,
        front: MutableList<Polygon>,
        back: MutableList<Polygon>,
    ) {
        var polygonType = 0
        val n = polygon.vertices.size
        val types = IntArray(n)
        for (i in 0 until n) {
            val t = plane.distance(polygon.vertices[i])
            val type = when {
                t < -EPS -> BACK
                t > EPS -> FRONT
                else -> COPLANAR
            }
            polygonType = polygonType or type
            types[i] = type
        }
        when (polygonType) {
            COPLANAR ->
                if (plane.n.dot(polygon.plane.n) > 0f) coplanarFront.add(polygon) else coplanarBack.add(polygon)
            FRONT -> front.add(polygon)
            BACK -> back.add(polygon)
            else -> { // SPANNING — cut the polygon along the plane
                val f = ArrayList<Vec3>(n + 1)
                val b = ArrayList<Vec3>(n + 1)
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    val ti = types[i]
                    val tj = types[j]
                    val vi = polygon.vertices[i]
                    val vj = polygon.vertices[j]
                    if (ti != BACK) f.add(vi)
                    if (ti != FRONT) b.add(vi)
                    if ((ti or tj) == SPANNING) {
                        val denom = plane.n.dot(vj - vi)
                        if (abs(denom) > 1e-12f) {
                            val t = (plane.w - plane.n.dot(vi)) / denom
                            val v = vi + (vj - vi) * t
                            f.add(v)
                            b.add(v)
                        }
                    }
                }
                if (f.size >= 3) front.add(Polygon(f, polygon.plane))
                if (b.size >= 3) back.add(Polygon(b, polygon.plane))
            }
        }
    }

    // ---- public API ------------------------------------------------------------------------

    /** `a ∪ b` — everything in either solid. */
    fun union(a: Mesh, b: Mesh): Mesh = combine(a, b, Operation.UNION)

    /** `a − b` — everything in `a` that is not in `b`. The drain-hole primitive. */
    fun subtract(a: Mesh, b: Mesh): Mesh = combine(a, b, Operation.SUBTRACT)

    /** `a ∩ b` — only the shared volume. */
    fun intersect(a: Mesh, b: Mesh): Mesh = combine(a, b, Operation.INTERSECT)

    /**
     * `target − tool`, but only the neighbourhood of [tool] goes through the BSP tree: triangles of
     * [target] whose bounding box misses the tool's box (grown by [marginMm]) are copied through
     * untouched.
     *
     * A drill bit drawn through one wall of a 500 k-triangle model touches a few thousand triangles,
     * so the expensive tree is built on those few instead of the whole model — the difference between
     * milliseconds and minutes on a phone. [marginMm] must cover the tool plus a few millimetres of
     * surrounding surface, so the tool is unambiguously *inside* the retained patch.
     *
     * **Limit:** a tool buried entirely inside the solid, touching no triangle (possible only on a
     * coarse mesh — a 12-triangle box, say), selects nothing and the call is a no-op. Drills always
     * cross the surface; use [subtract] for a sealed internal void.
     */
    fun subtractLocal(target: Mesh, tool: Mesh, marginMm: Float = 2f): Mesh {
        if (target.isEmpty || tool.isEmpty) return target
        val tb = tool.bounds()
        val minX = tb.min.x - marginMm
        val minY = tb.min.y - marginMm
        val minZ = tb.min.z - marginMm
        val maxX = tb.max.x + marginMm
        val maxY = tb.max.y + marginMm
        val maxZ = tb.max.z + marginMm

        val far = ArrayList<Float>()
        val near = ArrayList<Float>()
        val p = target.positions
        var i = 0
        while (i < p.size) {
            var tMinX = Float.MAX_VALUE
            var tMinY = Float.MAX_VALUE
            var tMinZ = Float.MAX_VALUE
            var tMaxX = -Float.MAX_VALUE
            var tMaxY = -Float.MAX_VALUE
            var tMaxZ = -Float.MAX_VALUE
            for (v in 0 until 3) {
                val x = p[i + v * 3]
                val y = p[i + v * 3 + 1]
                val z = p[i + v * 3 + 2]
                if (x < tMinX) tMinX = x
                if (x > tMaxX) tMaxX = x
                if (y < tMinY) tMinY = y
                if (y > tMaxY) tMaxY = y
                if (z < tMinZ) tMinZ = z
                if (z > tMaxZ) tMaxZ = z
            }
            val hits = tMaxX >= minX && tMinX <= maxX &&
                tMaxY >= minY && tMinY <= maxY &&
                tMaxZ >= minZ && tMinZ <= maxZ
            val dst = if (hits) near else far
            var k = 0
            while (k < Mesh.FLOATS_PER_TRIANGLE) {
                dst.add(p[i + k])
                k++
            }
            i += Mesh.FLOATS_PER_TRIANGLE
        }
        if (near.isEmpty()) return target
        val cut = subtract(Mesh(near.toFloatArray()), tool)
        return Mesh(far.toFloatArray() + cut.positions)
    }

    private fun combine(a: Mesh, b: Mesh, op: Operation): Mesh {
        if (a.isEmpty) return if (op == Operation.UNION) b else Mesh(FloatArray(0))
        if (b.isEmpty) return if (op == Operation.INTERSECT) Mesh(FloatArray(0)) else a
        val na = Node(toPolygons(a))
        val nb = Node(toPolygons(b))
        when (op) {
            Operation.SUBTRACT -> {
                na.invert()
                na.clipTo(nb)
                nb.clipTo(na)
                nb.invert()
                nb.clipTo(na)
                nb.invert()
                na.build(nb.allPolygons())
                na.invert()
            }
            Operation.UNION -> {
                na.clipTo(nb)
                nb.clipTo(na)
                nb.invert()
                nb.clipTo(na)
                nb.invert()
                na.build(nb.allPolygons())
            }
            Operation.INTERSECT -> {
                na.invert()
                nb.clipTo(na)
                nb.invert()
                na.clipTo(nb)
                nb.clipTo(na)
                na.build(nb.allPolygons())
                na.invert()
            }
        }
        return toMesh(na.allPolygons())
    }

    /** Triangle soup → BSP polygons. */
    private fun toPolygons(mesh: Mesh): List<Polygon> {
        val p = mesh.positions
        val out = ArrayList<Polygon>(mesh.triangleCount)
        var i = 0
        while (i < p.size) {
            val va = Vec3(p[i], p[i + 1], p[i + 2])
            val vb = Vec3(p[i + 3], p[i + 4], p[i + 5])
            val vc = Vec3(p[i + 6], p[i + 7], p[i + 8])
            out.add(Polygon(listOf(va, vb, vc), Plane.from(va, vb, vc)))
            i += Mesh.FLOATS_PER_TRIANGLE
        }
        return out
    }

    /** BSP polygons → triangle soup (fan-triangulated, in mm). */
    private fun toMesh(polygons: List<Polygon>): Mesh {
        val out = ArrayList<Float>()
        for (poly in polygons) {
            val v = poly.vertices
            if (v.size < 3) continue
            for (k in 1 until v.size - 1) {
                out.add(v[0].x); out.add(v[0].y); out.add(v[0].z)
                out.add(v[k].x); out.add(v[k].y); out.add(v[k].z)
                out.add(v[k + 1].x); out.add(v[k + 1].y); out.add(v[k + 1].z)
            }
        }
        return Mesh(out.toFloatArray())
    }
}
