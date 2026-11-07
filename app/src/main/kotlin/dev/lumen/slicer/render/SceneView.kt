package dev.lumen.slicer.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.lumen.slicer.core.mesh.Picking
import kotlin.math.abs

/** How a one-finger drag on the plate is read. [NAVIGATE] keeps the camera on one finger. */
enum class PlateTool { NAVIGATE, MOVE, ROTATE }

/**
 * Direct manipulation of the model on the plate, implemented by the screen that owns the model.
 *
 * The gesture state machine lives here (it is the only place that sees raw touches); the *meaning* of
 * a gesture — which model, what placement — stays with the caller.
 */
interface Manipulation {
    val tool: PlateTool

    /** The placed model's world bounding box as [minX,minY,minZ,maxX,maxY,maxZ], or null if none. */
    fun modelBounds(): FloatArray?

    /** A tap landed on the model (true) or on empty space (false). */
    fun onTap(hitModel: Boolean)

    /** Live, uncommitted transform relative to where the gesture started. */
    fun onLive(offsetXMm: Float, offsetYMm: Float, rotationDeg: Float)

    /** The finger lifted: commit [offsetXMm]/[offsetYMm]/[rotationDeg] so the change sticks. */
    fun onCommit(offsetXMm: Float, offsetYMm: Float, rotationDeg: Float)
}

/**
 * Hosts a [SceneRenderer] in Compose.
 *
 * Gestures: **two fingers** always pan and pinch-zoom; **one finger** orbits in [PlateTool.NAVIGATE].
 * With a [manipulation] and a Move/Rotate tool, a one-finger drag that **starts on the model** moves
 * or turns it and a drag that starts on empty space still orbits the camera — so the view is never
 * stuck, and the model is manipulated by touching it, the way a desktop slicer works.
 */
@Composable
fun <R : SceneRenderer> SceneView(
    renderer: R,
    modifier: Modifier = Modifier,
    manipulation: Manipulation? = null,
    update: (R) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { arrayOfNulls<GLSurfaceView>(1) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = modifier,
        factory = { context -> SceneSurface(context, renderer).also { holder[0] = it } },
        update = { view ->
            view.manipulation = manipulation
            update(renderer)
            view.requestRender()
        },
    )
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
private class SceneSurface(context: Context, private val renderer: SceneRenderer) : GLSurfaceView(context) {
    /** Set from the composable's update pass; read on the UI thread. */
    var manipulation: Manipulation? = null

    private var lastX = 0f
    private var lastY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var pointers = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // One-finger gesture bookkeeping: where it started, how far it has travelled, and whether it
    // grabbed the model (decided once, when the drag passes the slop threshold).
    private var downX = 0f
    private var downY = 0f
    private var travelled = 0f
    private var draggingModel = false
    private var dragPlaneZ = 0f
    private var dragFromX = 0f
    private var dragFromY = 0f
    private var liveOffsetX = 0f
    private var liveOffsetY = 0f
    private var liveAngle = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.camera.zoom(detector.scaleFactor)
            requestRender()
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pointers = 1
                lastX = event.x; lastY = event.y
                downX = event.x; downY = event.y
                travelled = 0f
                draggingModel = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger means the camera, so any model drag in progress is committed here
                // rather than left dangling.
                if (draggingModel) commitModelDrag()
                pointers = event.pointerCount
                focus(event, excluding = -1)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pointers = event.pointerCount - 1
                focus(event, excluding = event.actionIndex)
                if (pointers == 1) {
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    lastX = event.getX(remaining)
                    lastY = event.getY(remaining)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointers == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    travelled += abs(dx) + abs(dy)
                    val man = manipulation
                    if (man != null && man.tool != PlateTool.NAVIGATE) {
                        if (!draggingModel && travelled > touchSlop) {
                            draggingModel = beginModelDrag(man, downX, downY)
                        }
                        if (draggingModel) {
                            updateModelDrag(man, event.x, event.y)
                            lastX = event.x; lastY = event.y
                            requestRender()
                            return true
                        }
                    }
                    // No model grabbed: orbit, so the camera stays available in every tool.
                    renderer.camera.rotate(dx * ROTATE_DEGREES_PER_PX, dy * ROTATE_DEGREES_PER_PX)
                    lastX = event.x
                    lastY = event.y
                } else if (pointers >= 2) {
                    val previousX = lastFocusX
                    val previousY = lastFocusY
                    focus(event, excluding = -1)
                    renderer.camera.pan((lastFocusX - previousX) / height, (lastFocusY - previousY) / height)
                }
                requestRender()
            }
            MotionEvent.ACTION_UP -> {
                val man = manipulation
                when {
                    draggingModel -> commitModelDrag()
                    man != null && travelled <= touchSlop -> man.onTap(hitModel(man, downX, downY))
                }
                pointers = 0
            }
            MotionEvent.ACTION_CANCEL -> {
                if (draggingModel) commitModelDrag()
                pointers = 0
            }
        }
        return true
    }

    /** Start a drag if the touch landed on the model. One decision per gesture, at the start point. */
    private fun beginModelDrag(man: Manipulation, x: Float, y: Float): Boolean {
        val bounds = man.modelBounds() ?: return false
        if (!hitModel(man, x, y)) return false
        // Slide on the plane the model sits on, so the model tracks the point of the BED under the
        // finger — the way an object moves on the plate in a desktop slicer.
        dragPlaneZ = bounds[2]
        val hit = Picking.intersectPlane(renderer.rayFor(x, y), dragPlaneZ)
        if (hit == null) {
            draggingModel = false
            return false
        }
        dragFromX = hit[0]
        dragFromY = hit[1]
        liveOffsetX = 0f; liveOffsetY = 0f; liveAngle = 0f
        return true
    }

    private fun updateModelDrag(man: Manipulation, x: Float, y: Float) {
        when (man.tool) {
            PlateTool.MOVE -> {
                val hit = Picking.intersectPlane(renderer.rayFor(x, y), dragPlaneZ) ?: return
                liveOffsetX = hit[0] - dragFromX
                liveOffsetY = hit[1] - dragFromY
            }
            PlateTool.ROTATE -> {
                // Sideways drag turns the model on the bed (about world Z) — the axis that matters for
                // a print. Exact angles stay available in the placement sheet's numbered rows.
                liveAngle = (x - downX) * ROTATE_DEGREES_PER_PX_TOOL
            }
            PlateTool.NAVIGATE -> Unit
        }
        man.onLive(liveOffsetX, liveOffsetY, liveAngle)
    }

    private fun commitModelDrag() {
        val man = manipulation
        draggingModel = false
        if (man != null) man.onCommit(liveOffsetX, liveOffsetY, liveAngle)
        liveOffsetX = 0f; liveOffsetY = 0f; liveAngle = 0f
    }

    /**
     * Did the touch hit the model? Precise first (the ray must enter the model's box), then forgiving
     * (inside the model's projected box, inflated by the touch slop) — a finger is far wider than a
     * small model on a phone screen, and without the second test such a model is nearly untappable.
     */
    private fun hitModel(man: Manipulation, x: Float, y: Float): Boolean {
        val b = man.modelBounds() ?: return false
        if (Picking.intersectAabb(renderer.rayFor(x, y), b[0], b[1], b[2], b[3], b[4], b[5]) != null) return true
        return insideProjectedBounds(x, y, b)
    }

    private fun insideProjectedBounds(x: Float, y: Float, b: FloatArray): Boolean {
        val m = renderer.mvpSnapshot()
        val vp = IntArray(2)
        renderer.viewport(vp)
        val w = vp[0].toFloat()
        val h = vp[1].toFloat()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until 8) {
            val px = if (i and 1 == 0) b[0] else b[3]
            val py = if (i and 2 == 0) b[1] else b[4]
            val pz = if (i and 4 == 0) b[2] else b[5]
            val clipW = m[3] * px + m[7] * py + m[11] * pz + m[15]
            if (clipW <= 0.0001f) return false           // behind the camera
            val ndcX = (m[0] * px + m[4] * py + m[8] * pz + m[12]) / clipW
            val ndcY = (m[1] * px + m[5] * py + m[9] * pz + m[13]) / clipW
            val sx = (ndcX + 1f) * 0.5f * w
            val sy = (1f - ndcY) * 0.5f * h
            if (sx < minX) minX = sx
            if (sx > maxX) maxX = sx
            if (sy < minY) minY = sy
            if (sy > maxY) maxY = sy
        }
        return x >= minX - touchSlop && x <= maxX + touchSlop && y >= minY - touchSlop && y <= maxY + touchSlop
    }

    private fun focus(event: MotionEvent, excluding: Int) {
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == excluding) continue
            sumX += event.getX(i)
            sumY += event.getY(i)
            count++
        }
        if (count > 0) {
            lastFocusX = sumX / count
            lastFocusY = sumY / count
        }
    }

    private companion object {
        const val ROTATE_DEGREES_PER_PX = 0.35f

        /** Turning the model: a flick of ~180 px is a quarter turn. */
        const val ROTATE_DEGREES_PER_PX_TOOL = 0.5f
    }
}
