package io.github.whoxllm.orcamicro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.resin.ResinPrinter

/**
 * Resin (MSLA) plate screen. Loads a model, picks a resin printer, sets exposures, and slices to
 * an SL1 archive. The naive built-in rasterizer handles slicing until the PrusaSlicer SLA engine
 * (supports, hollowing, anti-aliasing) is grafted in.
 */
@Composable
fun ResinPlateScreen(state: UiState, viewModel: MainViewModel) {
    val openModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openModel(it) }
    }
    val saveOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.saveResinOutput(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Resin (MSLA)", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { viewModel.switchTechnology() }) { Text("Change slicer") }
        }
        PrinterSelector(state, viewModel)
        Text(
            state.model?.name ?: "No model loaded",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openModel.launch(arrayOf("model/stl", "application/octet-stream", "*/*")) }) {
                Text("Open STL")
            }
            OutlinedButton(onClick = { viewModel.removeModel() }, enabled = state.model != null) {
                Text("Remove")
            }
        }

        ResinSettingsFields(state, viewModel)

        Button(
            onClick = { viewModel.sliceResin() },
            enabled = state.model != null && state.resinSlice !is SliceState.Running,
        ) {
            Text("Slice to SL1")
        }

        when (val running = state.resinSlice) {
            is SliceState.Running -> {
                LinearProgressIndicator(progress = { running.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(running.text, style = MaterialTheme.typography.labelMedium)
            }
            is SliceState.Failed -> Text(running.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }

        state.resinOutput?.let { file ->
            Button(onClick = { saveOutput.launch(file.name) }) { Text("Save ${file.name}") }
        }
    }
}

@Composable
private fun PrinterSelector(state: UiState, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.resinPrinter
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current?.name ?: "Choose a printer")
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
private fun ResinSettingsFields(state: UiState, viewModel: MainViewModel) {
    val s = state.resinSettings
    Text("Settings", style = MaterialTheme.typography.titleMedium)
    NumField("Layer height (mm)", s.layerHeightMm) { viewModel.setResinSettings(s.copy(layerHeightMm = it)) }
    NumField("Exposure (s)", s.exposureSec) { viewModel.setResinSettings(s.copy(exposureSec = it)) }
    NumField("Bottom exposure (s)", s.bottomExposureSec) { viewModel.setResinSettings(s.copy(bottomExposureSec = it)) }
    IntField("Bottom layers", s.bottomLayers) { viewModel.setResinSettings(s.copy(bottomLayers = it)) }
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
