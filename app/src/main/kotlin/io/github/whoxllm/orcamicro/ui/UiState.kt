package io.github.whoxllm.orcamicro.ui

import io.github.whoxllm.orcamicro.core.Technology
import io.github.whoxllm.orcamicro.core.gcode.Toolpaths
import io.github.whoxllm.orcamicro.core.mesh.Mesh
import io.github.whoxllm.orcamicro.core.mesh.Placement
import io.github.whoxllm.orcamicro.core.mesh.Vec3
import io.github.whoxllm.orcamicro.core.profiles.Catalog
import io.github.whoxllm.orcamicro.core.resin.ResinPrinter
import io.github.whoxllm.orcamicro.core.resin.ResinSettings
import io.github.whoxllm.orcamicro.core.settings.BedShape
import io.github.whoxllm.orcamicro.core.settings.PresetEdits
import io.github.whoxllm.orcamicro.core.slicing.SliceInputs
import io.github.whoxllm.orcamicro.data.PresetSelection
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
    val resinSettings: ResinSettings = ResinSettings(),
    val resinSlice: SliceState = SliceState.Idle,
    /** The last resin slice written to disk, ready to save/share. */
    val resinOutput: File? = null,
)
