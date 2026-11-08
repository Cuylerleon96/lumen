package dev.lumen.slicer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lumen.slicer.core.mesh.Placement
import dev.lumen.slicer.core.mesh.PlateArranger
import dev.lumen.slicer.core.settings.BedShape
import dev.lumen.slicer.render.PlateRenderer
import dev.lumen.slicer.render.SceneView
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PlateScreen(state: UiState, viewModel: MainViewModel) {
    val openModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openModel(it) }
    }
    val exportProject = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.exportProject(it) }
    }
    val importProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importProject(it) }
    }
    var showPlacement by rememberSaveable { mutableStateOf(false) }
    val renderer = remember { PlateRenderer() }
    val gesture = remember { PlateGesture(renderer, viewModel::setPlacement) }
    // Read in the composable body: an AndroidView update lambda does not subscribe to snapshot state
    // by itself, so a selection change would not otherwise reach the renderer.
    val selected = gesture.selected
    // A newly opened model starts unselected.
    LaunchedEffect(state.model?.name) {
        if (gesture.modelName != state.model?.name) {
            gesture.selected = false
            gesture.modelName = state.model?.name
        }
    }

    PlateShell(
        title = "Lumen",
        modelName = state.model?.name,
        onSlice = viewModel::slice,
        message = state.message,
        onMessageConsumed = viewModel::consumeMessage,
        topActions = {
            TextButton(onClick = { viewModel.switchTechnology() }) { Text("Switch") }
            TextButton(onClick = { exportProject.launch("project.osproj") }) { Text("Save") }
            TextButton(onClick = { importProject.launch(arrayOf("*/*")) }) { Text("Open") }
            if (state.model != null) {
                IconButton(onClick = viewModel::removeModel) { Icon(Icons.Default.Delete, "Remove model") }
            }
            IconButton(onClick = { viewModel.navigate(Screen.ABOUT) }) { Icon(Icons.Default.Info, "About") }
        },
        bottomActions = {
            IconButton(onClick = { openModel.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Add, "Open STL") }
            IconButton(onClick = { showPlacement = true }, enabled = state.model != null) {
                Icon(Icons.Default.Edit, "Move, rotate and scale")
            }
            TextButton(onClick = { viewModel.arrangePlate() }, enabled = state.model != null) { Text("Arrange") }
            IconButton(onClick = { viewModel.navigate(Screen.PRESETS) }) { Icon(Icons.Default.Build, "Printer and presets") }
            IconButton(onClick = { viewModel.navigate(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, "Print settings") }
            if (state.lastResult != null) {
                TextButton(onClick = { viewModel.navigate(Screen.PREVIEW) }) { Text("Preview") }
            }
        },
    ) {
        // Plain holder, not state: framing happens in the view update and must not recompose.
        val framedBed = remember { arrayOfNulls<BedShape>(1) }
        SceneView(renderer, Modifier.fillMaxSize(), gesture) { r ->
            val bed = state.bed
            if (bed != null && framedBed[0] != bed) {
                r.camera.frame(bed.centerX, bed.centerY, 0f, max(bed.width, bed.depth) * 0.6f)
                framedBed[0] = bed
            }
            r.setBed(bed, state.printableHeight)
            // Keep the gesture's picture of the model current: this is the box the picking ray tests
            // and the placement a committed drag is applied to.
            val m = state.model
            gesture.bounds = m?.placed?.worldAabb()
            gesture.placement = m?.placement ?: Placement()
            r.setModel(m?.placed, m?.fits ?: true, selected)
        }
        PresetSummary(state, onClick = { viewModel.navigate(Screen.PRESETS) }, modifier = Modifier.align(Alignment.TopCenter))
        val model = state.model
        if (model == null) {
            Card(Modifier.align(Alignment.Center).padding(32.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Open an STL file to start", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { openModel.launch(arrayOf("*/*")) }) { Text("Open STL") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.loadCalibrationModel() }) { Text("Load calibration model") }
                    if (state.library.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Recent", style = MaterialTheme.typography.labelLarge)
                        state.library.take(4).forEach { f ->
                            TextButton(onClick = { viewModel.openFromLibrary(f) }) {
                                Text(f.name.substringAfter('_', f.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else {
            ModelBar(model.size, model.fits, gesture, Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
    }

    val model = state.model
    if (showPlacement && model != null) {
        PlacementSheet(
            model = model,
            state = state,
            onChange = viewModel::setPlacement,
            onCenter = viewModel::centerModel,
            onFit = viewModel::fitToBuildVolume,
            onLayFlat = viewModel::layFlat,
            onDismiss = { showPlacement = false },
            onArrange = viewModel::arrangePlate,
            onArrangeSettings = viewModel::setArrange,
        )
    }
    UnitPromptDialog(state, viewModel)
}

@Composable
private fun PresetSummary(state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val selection = state.selection ?: return
    Card(
        modifier = modifier.padding(12.dp).fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(selection.printer, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${selection.process}  ·  ${selection.filament.name}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PlacementSheet(
    model: ModelState,
    state: UiState,
    onChange: (Placement) -> Unit,
    onCenter: () -> Unit,
    onFit: () -> Unit,
    onLayFlat: () -> Unit,
    onDismiss: () -> Unit,
    onArrange: () -> Unit,
    onArrangeSettings: (PlateArranger.Settings) -> Unit,
    /**
     * The plate this model sits on. Pass it when the state's own bed is not the plate being shown: the
     * resin side's `state.bed` comes from the FDM machine profile, so the position limits, the reset
     * target and the centre button would all point at the wrong rectangle.
     */
    plateBed: BedShape? = null,
) {
    // NOT skipPartiallyExpanded: the sheet must open at HALF height so the plate and the model stay
    // visible above it while you drag the rotate slider — a full-height sheet hides the very thing you
    // are judging, whatever its alpha. Drag it up for the rest of the controls.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val p = model.placement
    val bed = plateBed ?: state.bed
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The sheet must not hide the thing it edits: you judge a rotation by WATCHING the model turn,
        // so the modal scrim goes and the container is translucent — the plate stays visible above and
        // behind it. Cuyler's request: "the orientation tab should be partially transparent so you can
        // see how it's rotating".
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Place the model", style = MaterialTheme.typography.titleMedium)
            Text(
                "%.1f × %.1f × %.1f mm".format(model.size.x, model.size.y, model.size.z) +
                    if (model.fits) "" else "  ·  does not fit",
                color = if (model.fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            HorizontalDivider()
            Text("Rotate", style = MaterialTheme.typography.labelLarge)
            RotateRow("X", p.rotationX) { onChange(p.copy(rotationX = it)) }
            RotateRow("Y", p.rotationY) { onChange(p.copy(rotationY = it)) }
            RotateRow("Z", p.rotationZ) { onChange(p.copy(rotationZ = it)) }
            LabeledSlider("Turn on the bed", "${p.rotationZ.roundToInt()}°", p.rotationZ, -180f..180f) {
                onChange(p.copy(rotationZ = it.roundToInt().toFloat()))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLayFlat) { Text("Lay flat") }
                Text(
                    "rests it on its largest flat face",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Mirror", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = p.mirrorX, onClick = { onChange(p.copy(mirrorX = !p.mirrorX)) }, label = { Text("X") })
                FilterChip(selected = p.mirrorY, onClick = { onChange(p.copy(mirrorY = !p.mirrorY)) }, label = { Text("Y") })
                FilterChip(selected = p.mirrorZ, onClick = { onChange(p.copy(mirrorZ = !p.mirrorZ)) }, label = { Text("Z") })
            }
            HorizontalDivider()
            LabeledSlider("Scale", "${p.scalePercent.roundToInt()}%", p.scalePercent, 10f..400f) {
                onChange(p.copy(scalePercent = it.roundToInt().toFloat()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in listOf(50f, 100f, 200f)) {
                    OutlinedButton(onClick = { onChange(p.copy(scalePercent = preset)) }) { Text("${preset.roundToInt()}%") }
                }
            }
            if (bed != null) {
                HorizontalDivider()
                LabeledSlider("Position X", "%.1f mm".format(p.centerX), p.centerX, bed.minX..bed.maxX) {
                    onChange(p.copy(centerX = it))
                }
                LabeledSlider("Position Y", "%.1f mm".format(p.centerY), p.centerY, bed.minY..bed.maxY) {
                    onChange(p.copy(centerY = it))
                }
            }
            HorizontalDivider()
            val arrange = state.arrange
            Text("Arrange the plate", style = MaterialTheme.typography.labelLarge)
            LabeledSlider("Spacing between models", "%.1f mm".format(arrange.spacingMm), arrange.spacingMm, 0f..20f) {
                onArrangeSettings(arrange.copy(spacingMm = it))
            }
            LabeledSlider("Margin from the edge", "%.1f mm".format(arrange.marginMm), arrange.marginMm, 0f..20f) {
                onArrangeSettings(arrange.copy(marginMm = it))
            }
            FilterChip(
                selected = arrange.allowRotation,
                onClick = { onArrangeSettings(arrange.copy(allowRotation = !arrange.allowRotation)) },
                label = { Text("Turn models to fit") },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onArrange) { Text("Arrange now") }
                Text(
                    "packs the plate without overlap",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = onCenter) { Text("Center") }
                OutlinedButton(onClick = onFit) { Text("Fit") }
                OutlinedButton(onClick = {
                    onChange(Placement(centerX = bed?.centerX ?: p.centerX, centerY = bed?.centerY ?: p.centerY))
                }) { Text("Reset") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun RotateRow(axis: String, degrees: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$axis  ${normalize(degrees).roundToInt()}°", modifier = Modifier.width(72.dp))
        OutlinedButton(onClick = { onChange(normalize(degrees - 90f)) }) { Text("−90°") }
        OutlinedButton(onClick = { onChange(normalize(degrees + 90f)) }) { Text("+90°") }
        TextButton(onClick = { onChange(0f) }) { Text("0°") }
    }
}

private fun normalize(degrees: Float): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = current.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

/** Shows [message] in [host] once, then clears it. */
@Composable
fun MessageEffect(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            onShown()
            host.showSnackbar(message)
        }
    }
}
