package dev.lumen.slicer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lumen.slicer.core.resin.ResinFormat
import dev.lumen.slicer.core.resin.ResinPrinter
import dev.lumen.slicer.core.resin.ResinProfile
import dev.lumen.slicer.core.settings.BedShape
import dev.lumen.slicer.render.PlateRenderer
import dev.lumen.slicer.render.SceneView
import kotlin.math.max

/**
 * Resin (MSLA) plate screen. Uses the same [PlateShell] as the FDM [PlateScreen], so the two
 * technologies share one visual shell — only the icons, the summary card and the slice engine
 * differ. The naive built-in rasterizer slices to an SL1 archive until the PrusaSlicer SLA engine
 * (supports / hollowing / anti-aliasing) is grafted in.
 */
@Composable
fun ResinPlateScreen(state: UiState, viewModel: MainViewModel) {
    val openModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openModel(it) }
    }
    val saveOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.saveResinOutput(it) }
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPlacement by rememberSaveable { mutableStateOf(false) }

    val printer = state.resinPrinter
    val bed = remember(printer?.name) {
        printer?.let { BedShape.rectangle(0f, 0f, it.buildWidthMm.toFloat(), it.buildDepthMm.toFloat()) }
    }

    PlateShell(
        title = "Lumen",
        modelName = state.model?.name,
        onSlice = viewModel::sliceResin,
        message = state.message,
        onMessageConsumed = viewModel::consumeMessage,
        topActions = {
            TextButton(onClick = { viewModel.switchTechnology() }) { Text("Switch") }
            if (state.model != null) {
                IconButton(onClick = viewModel::removeModel) { Icon(Icons.Default.Delete, "Remove model") }
            }
            IconButton(onClick = { viewModel.navigate(Screen.ABOUT) }) { Icon(Icons.Default.Info, "About") }
        },
        bottomActions = {
            IconButton(onClick = { openModel.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Add, "Open STL") }
            if (state.model != null) {
                IconButton(onClick = { showPlacement = true }) { Icon(Icons.Default.Build, "Move / rotate / scale") }
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Resin settings") }
            state.resinOutput?.let { out ->
                TextButton(onClick = { saveOutput.launch(out.name) }) { Text("Save .${out.extension.ifBlank { "sl1" }}") }
            }
        },
    ) {
        val renderer = remember { PlateRenderer() }
        val framed = remember { arrayOfNulls<BedShape>(1) }
        SceneView(renderer, Modifier.fillMaxSize()) { r ->
            if (bed != null && framed[0] != bed) {
                r.camera.frame(bed.centerX, bed.centerY, 0f, max(bed.width, bed.depth) * 0.6f)
                framed[0] = bed
            }
            r.setBed(bed, (printer?.buildHeightMm ?: 200.0).toFloat())
            r.setModel(state.model?.placed, true)
        }
        ResinSummary(state, onClick = { showSettings = true }, modifier = Modifier.align(Alignment.TopCenter))
        if (state.model == null) {
            Card(Modifier.align(Alignment.Center).padding(32.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Open an STL file to start", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { openModel.launch(arrayOf("*/*")) }) { Text("Open STL") }
                }
            }
        }
    }

    if (showSettings) {
        ResinSettingsSheet(state, viewModel, onDismiss = { showSettings = false })
    }
    val model = state.model
    if (showPlacement && model != null) {
        PlacementSheet(
            model = model,
            state = state,
            onChange = viewModel::setPlacement,
            onCenter = viewModel::centerModel,
            onFit = viewModel::fitToBuildVolume,
            onDismiss = { showPlacement = false },
            showPosition = false,
        )
    }
    ResinSliceDialog(state.resinSlice, viewModel::cancelResinSlice, viewModel::dismissResinError)
    UnitPromptDialog(state, viewModel)
}

@Composable
private fun ResinSummary(state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(12.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.resinPrinter?.name ?: "No printer",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${state.resinSettings.layerHeightMm} mm  ·  ${state.resinSettings.exposureSec}s  ·  ${state.resinSettings.bottomLayers} bottom" +
                        (state.model?.size?.z?.let { "  ·  ~${formatDuration(state.resinSettings.estimatedPrintTimeSec(it.toDouble()))}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.model?.let { m ->
                    val e = state.resinSettings.estimateResin(m.mesh.volumeAbsMm3())
                    val cost = if (state.resinSettings.pricePerKg > 0) "  ·  %.2f".format(e.cost) else ""
                    Text(
                        "%.1f ml  ·  %.1f g%s".format(e.volumeMl, e.massG, cost),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onClick) { Text("Settings") }
        }
    }
}

@Composable
private fun ResinSettingsSheet(state: UiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val s = state.resinSettings
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Resin settings", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            ResinProfilePicker(state, viewModel)
            PrinterPicker(state, viewModel)
            NumField("Layer height (mm)", s.layerHeightMm) { viewModel.setResinSettings(s.copy(layerHeightMm = it)) }
            NumField("Exposure (s)", s.exposureSec) { viewModel.setResinSettings(s.copy(exposureSec = it)) }
            NumField("Bottom exposure (s)", s.bottomExposureSec) { viewModel.setResinSettings(s.copy(bottomExposureSec = it)) }
            IntField("Bottom layers", s.bottomLayers) { viewModel.setResinSettings(s.copy(bottomLayers = it)) }
            IntField("Transition layers", s.transitionLayers) { viewModel.setResinSettings(s.copy(transitionLayers = it)) }
            NumField("Light-off delay (s)", s.lightOffSec) { viewModel.setResinSettings(s.copy(lightOffSec = it)) }
            Text("Output format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<ResinFormat?>(null, ResinFormat.SL1, ResinFormat.CTB, ResinFormat.GOO).forEach { f ->
                    FilterChip(
                        selected = state.formatOverride == f,
                        onClick = { viewModel.setFormatOverride(f) },
                        label = { Text(f?.ext?.uppercase() ?: "Auto") },
                    )
                }
            }
            IntField("Anti-aliasing levels", s.antiAliasLevels) { viewModel.setResinSettings(s.copy(antiAliasLevels = it)) }
            NumField("Resin density (g/ml)", s.materialDensityGPerMl) { viewModel.setResinSettings(s.copy(materialDensityGPerMl = it)) }
            NumField("Resin price / kg", s.pricePerKg) { viewModel.setResinSettings(s.copy(pricePerKg = it)) }
            HorizontalDivider()
            SwitchRow("Hollow the model", s.hollowing) { viewModel.setResinSettings(s.copy(hollowing = it)) }
            if (s.hollowing) {
                NumField("Wall thickness (mm)", s.wallThicknessMm) { viewModel.setResinSettings(s.copy(wallThicknessMm = it)) }
                NumField("Closing distance (mm)", s.hollowingClosingDistanceMm) { viewModel.setResinSettings(s.copy(hollowingClosingDistanceMm = it)) }
            }
            HorizontalDivider()
            SwitchRow("Generate supports", s.supportsEnabled) { viewModel.setResinSettings(s.copy(supportsEnabled = it)) }
            SwitchRow("Repair layers (remove specks, fill pinholes)", s.repairLayers) { viewModel.setResinSettings(s.copy(repairLayers = it)) }
            if (s.supportsEnabled) {
                IntField("Support density (%)", s.supportDensityPercent) { viewModel.setResinSettings(s.copy(supportDensityPercent = it)) }
                NumField("Contact diameter (mm)", s.supportContactDiameterMm) { viewModel.setResinSettings(s.copy(supportContactDiameterMm = it)) }
                NumField("Pillar diameter (mm)", s.supportPillarDiameterMm) { viewModel.setResinSettings(s.copy(supportPillarDiameterMm = it)) }
                NumField("Object gap (mm)", s.supportObjectGapMm) { viewModel.setResinSettings(s.copy(supportObjectGapMm = it)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ResinProfilePicker(state: UiState, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.resinSettings
    Box {
        ListItem(
            headlineContent = { Text("Resin profile") },
            supportingContent = { Text("${current.layerHeightMm} mm · ${current.exposureSec}s") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        ) { Text("Load") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResinProfile.builtIn.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = { viewModel.applyResinProfile(profile.name); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun PrinterPicker(state: UiState, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.resinPrinter
    Box {
        ListItem(
            headlineContent = { Text("Resin printer") },
            supportingContent = { Text(current?.name ?: "Choose a printer") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        ) {
            Text("Change")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResinPrinter.builtIn.forEach { printer ->
                DropdownMenuItem(
                    text = { Text(printer.name) },
                    onClick = {
                        viewModel.selectResinPrinter(printer.name)
                        expanded = false
                    },
                )
            }
        }
    }
    current?.let {
        Text(
            "Build ${"%.1f".format(it.buildWidthMm)} × ${"%.1f".format(it.buildDepthMm)} × ${"%.1f".format(it.buildHeightMm)} mm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumField(label: String, value: Double, onChange: (Double) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toDoubleOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Formats a duration in seconds as e.g. "1h 23m" / "45m" / "12s". */
private fun formatDuration(sec: Double): String {
    val s = sec.toLong()
    return when {
        s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
        s >= 60 -> "${s / 60}m"
        else -> "${s}s"
    }
}

@Composable
private fun ResinSliceDialog(slice: SliceState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    when (slice) {
        is SliceState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Slicing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(slice.text.ifBlank { "Working" }, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { slice.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${slice.percent}%", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
        is SliceState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Slicing failed") },
            text = { Text(slice.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        else -> Unit
    }
}
