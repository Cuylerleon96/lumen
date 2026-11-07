package dev.lumen.slicer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.Placement
import dev.lumen.slicer.core.mesh.Vec3
import dev.lumen.slicer.render.Manipulation
import dev.lumen.slicer.render.PlateRenderer
import dev.lumen.slicer.render.PlateTool

/**
 * Tap-to-select and drag-to-transform for the model on the plate — shared by the FDM and the resin
 * plate so the two cannot drift apart.
 *
 * The gesture state machine (which finger did what) lives in `SceneView`; this class holds the
 * **meaning**: what is selected, which tool is active, and what a finished gesture does to the
 * placement. Only the *committed* transform is handed back, so an interrupted gesture leaves no
 * trace, and the live preview is a renderer uniform rather than a new mesh.
 */
class PlateGesture(
    private val renderer: PlateRenderer,
    private val commit: (Placement) -> Unit,
) : Manipulation {

    /** Whether a tap has picked the model up. */
    var selected by mutableStateOf(false)

    /** Move or Rotate — the gizmo a desktop slicer shows for the selected object. */
    override var tool: PlateTool by mutableStateOf(PlateTool.MOVE)

    /** The placed model's placement and world AABB, refreshed on every recomposition. */
    var placement: Placement = Placement()
    var bounds: FloatArray? = null

    /** Which model the selection belongs to, so opening a new file starts unselected. */
    var modelName: String? = null

    override fun modelBounds(): FloatArray? = bounds

    override fun onTap(hitModel: Boolean) {
        selected = hitModel
    }

    override fun onLive(offsetXMm: Float, offsetYMm: Float, rotationDeg: Float) {
        renderer.setLive(offsetXMm, offsetYMm, rotationDeg)
    }

    override fun onCommit(offsetXMm: Float, offsetYMm: Float, rotationDeg: Float) {
        renderer.setLive(0f, 0f, 0f)
        val next = when (tool) {
            PlateTool.MOVE -> placement.copy(
                centerX = placement.centerX + offsetXMm,
                centerY = placement.centerY + offsetYMm,
            )
            PlateTool.ROTATE -> placement.copy(rotationZ = normalizeDegrees(placement.rotationZ + rotationDeg))
            PlateTool.NAVIGATE -> placement
        }
        if (next != placement) commit(next)
    }

    companion object {
        fun normalizeDegrees(degrees: Float): Float {
            var d = degrees % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }
    }
}

/** The placed mesh's world AABB as `[minX,minY,minZ,maxX,maxY,maxZ]` — what the picking ray tests. */
fun Mesh.worldAabb(): FloatArray {
    val b = bounds()
    return floatArrayOf(b.min.x, b.min.y, b.min.z, b.max.x, b.max.y, b.max.z)
}

/**
 * The model bar: its size, and — once the model is tapped — the Move/Rotate tool and a one-line hint
 * naming the gesture. Nothing here is decorative: every control does the thing it says, and the hint
 * is the only place the touch idiom is explained, because on a phone there is no hover to discover it.
 */
@Composable
fun ModelBar(
    size: Vec3,
    fits: Boolean,
    gesture: PlateGesture,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "%.1f × %.1f × %.1f mm".format(size.x, size.y, size.z) + if (fits) "" else "  ·  outside the build volume",
                style = MaterialTheme.typography.labelLarge,
                color = if (fits) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            if (!gesture.selected) {
                Text(
                    "Tap the model to move or rotate it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = gesture.tool == PlateTool.MOVE,
                    onClick = { gesture.tool = PlateTool.MOVE },
                    label = { Text("Move") },
                )
                FilterChip(
                    selected = gesture.tool == PlateTool.ROTATE,
                    onClick = { gesture.tool = PlateTool.ROTATE },
                    label = { Text("Rotate") },
                )
            }
            Text(
                when (gesture.tool) {
                    PlateTool.ROTATE -> "Drag sideways to turn it on the plate"
                    else -> "Drag the model to move it on the plate"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
