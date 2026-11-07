package dev.lumen.slicer.core.resin

/**
 * JNI bridge to the PrusaSlicer SLA engine (`libsla_engine.so`, built from `native/sla/sla_jni.cpp`).
 *
 * The engine owns the whole resin pipeline: load mesh -> supports -> hollowing -> rasterize -> SL1.
 */
object SlaEngine {

    @Volatile private var loaded = false

    /** Load the native library once; returns false if the .so is absent (e.g. non-arm64 host). */
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("sla_engine")
            loaded = true
            println("SlaEngine: libsla_engine.so loaded OK")
            true
        } catch (t: Throwable) {
            println("SlaEngine: loadLibrary(sla_engine) failed: $t")
            false
        }
    }

    /**
     * Slice [inPath] (STL/3MF/OBJ) into an SL1 archive at [outPath].
     *
     * @param settingsJson JSON object of SLA setting overrides, e.g. `{"layer_height":"0.05",
     *   "initial_exposure_time":"2.5"}`. Keys are PrusaSlicer SLA config keys; values are strings.
     * @return 0 success · 1 engine error · 2 validation error · 3 empty / no objects.
     */
    external fun nativeSlice(inPath: String, outPath: String, settingsJson: String): Int

    /** Convenience wrapper that ensures the library is loaded. */
    fun slice(inPath: String, outPath: String, settingsJson: String = "{}"): Int {
        ensureLoaded()
        return nativeSlice(inPath, outPath, settingsJson)
    }
}
