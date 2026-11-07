package dev.lumen.slicer.core.gcode

/**
 * A calibration ramp applied to a sliced tower: one value per Z segment, bottom segment first.
 *
 * The G-code side of an FDM calibration print. The tower model comes from
 * [dev.lumen.slicer.core.mesh.CalibrationModels.fdmTower]; slicing it gives G-code that is uniform
 * from top to bottom, and this pass makes it *not* uniform — one value per segment, switched at the
 * segment boundary. That split is deliberate: the slicer keeps doing the slicing, and the ramp is a
 * textual pass over its output, so nothing here depends on which engine produced the G-code.
 */
data class CalibrationRamp(
    /** Height of one segment, mm. Must match the tower that was sliced. */
    val segmentHeightMm: Float,
    /** One value per segment, bottom first. */
    val values: List<Double>,
    /**
     * Optional command emitted *after* [command] that waits for the change to take effect
     * (`M109` for temperature). Null when the change is immediate.
     */
    val waitCommand: ((Double) -> String)? = null,
    /**
     * The command that applies a value, e.g. `{ v -> "M104 S${v.toInt()}" }`.
     *
     * Deliberately the **last** parameter: a trailing lambda binds to the last parameter, so putting
     * [waitCommand] (defaulted, easy to forget) after it would silently capture the lambda and leave
     * `command` unset — `CalibrationRamp(10f, values) { ... }` has to mean "this is the command".
     */
    val command: (Double) -> String,
) {
    /** Value for the segment covering [z] mm, or null above the top segment. */
    fun valueAt(z: Float): Double? {
        if (z < 0f || segmentHeightMm <= 0f) return null
        val i = (z / segmentHeightMm).toInt()
        return values.getOrNull(i)
    }
}

/**
 * Rewrites sliced G-code so each Z segment of a calibration tower runs with its own value.
 *
 * Design rules, and why:
 *  - **Everything not inserted is preserved byte for byte**, including line endings. A calibration
 *    print is thrown away afterwards, but a mangled file means an hour of the user's time is gone and
 *    the diagnosis is worthless, so the pass must not "tidy" anything.
 *  - A boundary is crossed at the first move whose Z **reaches** it, not the first whose Z equals it:
 *    a layer height that does not divide the segment height (0.2 mm layers into a 10 mm segment) never
 *    lands exactly on the boundary, and an equality test would silently apply nothing.
 *  - A single move can jump several boundaries (a Z hop, or a coarse first layer); the pass therefore
 *    loops until the segment index catches up rather than emitting one change per move.
 *  - The first segment's value is emitted before the first move that carries a Z word — the start of
 *    the print — so the bottom segment is not left running at whatever the profile's start G-code set.
 */
object GcodeCalibration {

    private val zWord = Regex("""(?:^|\s)[Zz](-?\d*\.?\d+)""")
    private val moveWord = Regex("""^\s*G[01]\b""")

    /** Apply [ramp] to [gcode]. Returns the rewritten text. */
    fun apply(gcode: String, ramp: CalibrationRamp): String {
        if (ramp.values.isEmpty() || ramp.segmentHeightMm <= 0f) return gcode
        val out = StringBuilder(gcode.length + 256)
        var nextSegment = 1
        var firstEmitted = false
        var i = 0
        while (i < gcode.length) {
            val nl = gcode.indexOf('\n', i)
            val end = if (nl < 0) gcode.length else nl + 1
            val line = gcode.substring(i, end)
            val eol = if (line.endsWith("\r\n")) "\r\n" else "\n"

            val isMove = moveWord.containsMatchIn(line)
            val z = if (isMove) zWord.find(line)?.groupValues?.get(1)?.toFloatOrNull() else null

            if (z != null && !firstEmitted) {
                appendCommands(out, ramp, 0, eol)
                firstEmitted = true
            }
            if (z != null) {
                // Emit every boundary this move has reached or passed (a Z hop can clear several).
                while (nextSegment < ramp.values.size && z >= nextSegment * ramp.segmentHeightMm) {
                    appendCommands(out, ramp, nextSegment, eol)
                    nextSegment++
                }
            }
            out.append(line)
            i = end
        }
        return out.toString()
    }

    private fun appendCommands(out: StringBuilder, ramp: CalibrationRamp, segment: Int, eol: String) {
        val v = ramp.values[segment]
        out.append("; Lumen calibration: segment ").append(segment).append(" (Z ")
            .append(segment * ramp.segmentHeightMm).append(")").append(eol)
        out.append(ramp.command(v)).append(eol)
        ramp.waitCommand?.let { out.append(it(v)).append(eol) }
    }

    // ---- standard ramps -------------------------------------------------------

    /**
     * Temperature tower: [startC] for the bottom segment, changing by [stepC] each segment.
     * Emits `M104` to set and `M109` to wait — the wait matters, because the segment above is printed
     * immediately after the change and would otherwise start at the previous temperature.
     */
    fun temperature(
        startC: Int,
        stepC: Int,
        segments: Int,
        segmentHeightMm: Float = 10f,
    ) = CalibrationRamp(
        segmentHeightMm = segmentHeightMm,
        values = (0 until segments).map { (startC + it * stepC).toDouble() },
        command = { v -> "M104 S${v.toInt()}" },
        waitCommand = { v -> "M109 S${v.toInt()}" },
    )

    /** Flow tower: `M220 S<percent>` — a firmware-level flow multiplier, no reslice needed. */
    fun flow(
        startPercent: Int,
        stepPercent: Int,
        segments: Int,
        segmentHeightMm: Float = 10f,
    ) = CalibrationRamp(
        segmentHeightMm = segmentHeightMm,
        values = (0 until segments).map { (startPercent + it * stepPercent).toDouble() },
        command = { v -> "M220 S${v.toInt()}" },
    )

    /**
     * Pressure-advance tower. Klipper takes `M572 D0 S<value>`; Marlin takes `M900 K<value>`. Both are
     * emitted so one file works on either firmware (each ignores the other's command).
     */
    fun pressureAdvance(
        start: Double,
        step: Double,
        segments: Int,
        segmentHeightMm: Float = 10f,
    ) = CalibrationRamp(
        segmentHeightMm = segmentHeightMm,
        values = (0 until segments).map { start + it * step },
        command = { v -> "M572 D0 S${"%.3f".format(v)}" },
        waitCommand = { v -> "M900 K${"%.3f".format(v)}" },
    )
}
