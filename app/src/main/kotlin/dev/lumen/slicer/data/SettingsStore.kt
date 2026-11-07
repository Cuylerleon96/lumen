package dev.lumen.slicer.data

import android.content.Context
import dev.lumen.slicer.core.Technology
import dev.lumen.slicer.core.mesh.Placement
import dev.lumen.slicer.core.profiles.PresetRef
import dev.lumen.slicer.core.resin.ResinSettings
import dev.lumen.slicer.core.settings.PresetEdits
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Which presets are selected. */
@Serializable
data class PresetSelection(
    val vendor: String,
    val printer: String,
    val process: String,
    val filament: PresetRef,
)

/** Everything this app remembers between launches. */
@Serializable
data class SavedState(
    val selection: PresetSelection? = null,
    val edits: PresetEdits = PresetEdits(),
    val modelName: String? = null,
    val placement: Placement? = null,
    /** FDM or resin, chosen on first launch; null = not chosen yet. */
    val technology: Technology? = null,
    val resinPrinterName: String? = null,
    val resinSettings: ResinSettings? = null,
)

/** Persists [SavedState] as JSON in app storage, and keeps a copy of the current model. */
class SettingsStore(context: Context) {
    private val dir = context.filesDir
    private val stateFile = File(dir, "state.json")
    val modelFile = File(dir, "model.stl")

    fun load(): SavedState = try {
        if (stateFile.isFile) json.decodeFromString(SavedState.serializer(), stateFile.readText()) else SavedState()
    } catch (e: Exception) {
        SavedState()
    }

    fun save(state: SavedState) {
        val tmp = File(stateFile.path + ".tmp")
        tmp.writeText(json.encodeToString(SavedState.serializer(), state))
        tmp.renameTo(stateFile)
    }

    /** Export the project (state + model) into a single `.osproj` ZIP. Returns the file count. */
    fun exportProject(out: File): Int {
        var n = 0
        java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for (f in listOf(stateFile, modelFile)) {
                if (!f.isFile) continue
                zip.putNextEntry(java.util.zip.ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry(); n++
            }
        }
        return n
    }

    /** Import a project ZIP, replacing the current state + model. Returns true if state was found. */
    fun importProject(zipFile: File): Boolean {
        var gotState = false
        java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val name = File(e.name).name   // strip any path — we only accept our two files
                if (name == stateFile.name || name == modelFile.name) {
                    File(dir, name).outputStream().use { zis.copyTo(it) }
                    if (name == stateFile.name) gotState = true
                }
                zis.closeEntry(); e = zis.nextEntry
            }
        }
        return gotState
    }

    // ---- model library (F): recently opened models, kept for one-tap re-open ----

    private val libraryDir = File(dir, "library")

    /** Copies [bytes] into the library under a timestamped name, prunes to the newest [keep]. */
    fun addToLibrary(name: String, bytes: ByteArray, keep: Int = 12): File {
        libraryDir.mkdirs()
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "model" }
        val f = File(libraryDir, "${System.currentTimeMillis()}_$safe")
        f.writeBytes(bytes)
        libraryDir.listFiles()?.sortedByDescending { it.name }?.drop(keep)?.forEach { it.delete() }
        return f
    }

    /** Library files, newest first. The display name is the original name after the timestamp. */
    fun libraryFiles(): List<File> = libraryDir.listFiles()?.sortedByDescending { it.name }?.toList() ?: emptyList()

    fun displayName(file: File): String = file.name.substringAfter('_', file.name)

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
