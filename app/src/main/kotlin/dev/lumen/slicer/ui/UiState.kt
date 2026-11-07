package dev.lumen.slicer.ui

import dev.lumen.slicer.core.Technology
import dev.lumen.slicer.core.gcode.Toolpaths
import dev.lumen.slicer.core.mesh.Mesh
import dev.lumen.slicer.core.mesh.Placement
import dev.lumen.slicer.core.mesh.Vec3
import dev.lumen.slicer.core.profiles.Catalog
import dev.lumen.slicer.core.resin.ResinFormat
import dev.lumen.slicer.core.resin.ResinPrinter
import dev.lumen.slicer.core.resin.ResinSettings
import dev.lumen.slicer.core.settings.BedShape
import dev.lumen.slicer.core.settings.PresetEdits
import dev.lumen.slicer.core.slicing.SliceInputs
import dev.lumen.slicer.data.PresetSelection
import java.io.File

enum class Screen { TECHNOLOGY, PLATE, PRESETS, SETTINGS, PREVIEW, ABOUT, RESIN }

/** The loaded model: the mesh as read, how it is placed, and the placed result for display. */
data class ModelState(
    val name: String,
    val mesh: Mesh,
    val placement: Placement,
    val placed: Mesh,
    val size: Vec3,
    val fits: Boolean,
)

sealed interface SliceState {
    data object Idle : SliceState
    data class Running(val percent: Int, val text: String) : SliceState
    data class Done(val gcode: File, val toolpaths: Toolpaths, val warnings: List<String>, val modelName: String) : SliceState
    data class Failed(val message: String, val details: String?) : SliceState
}

/** Shown when an opened model is far larger than the build volume — ask which unit the file uses. */
data class UnitPrompt(val name: String, val sizeMm: Vec3)

data class UiState(
    val ready: Boolean = false,
    val loadError: String? = null,
    val catalog: Catalog? = null,
    val selection: PresetSelection? = null,
    val edits: PresetEdits = PresetEdits(),
    /** The selected presets with the user's edits applied, as they will be sliced. */
    val inputs: SliceInputs? = null,
    val bed: BedShape? = null,
    val printableHeight: Float = 250f,
    val model: ModelState? = null,
    val slice: SliceState = SliceState.Idle,
    /** The last finished slice, kept while a new model or settings are being prepared. */
    val lastResult: SliceState.Done? = null,
    val screen: Screen = Screen.TECHNOLOGY,
    val message: String? = null,
    // ---- technology choice + the resin (MSLA) side ----
    /** Null until the user picks FDM or resin on first launch. */
    val technology: Technology? = null,
    val resinPrinter: ResinPrinter? = null,
    /** Override the printer's native export format; null = Auto (use the printer's format). */
    val formatOverride: ResinFormat? = null,
    val resinSettings: ResinSettings = ResinSettings(),
    val resinSlice: SliceState = SliceState.Idle,
    /** The last resin slice written to disk, ready to save/share. */
    val resinOutput: File? = null,
    /** Recently opened models on device (newest first). */
    val library: List<File> = emptyList(),
    /** Pending "which units is this file in?" prompt for an oversized model. */
    val unitPrompt: UnitPrompt? = null,
)
