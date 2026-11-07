package io.github.whoxllm.orcamicro.core.resin

import kotlinx.serialization.Serializable

/**
 * A masked-SLA resin printer: the LCD resolution and pixel pitch decide the build area.
 * Build area (mm) = resolution × pixel size.
 */
@Serializable
data class ResinPrinter(
    val name: String,
    /** Horizontal pixel count (X / width). */
    val resolutionX: Int,
    /** Vertical pixel count (Y / depth). */
    val resolutionY: Int,
    /** Pixel pitch in micrometres. */
    val pixelSizeMicrons: Double,
    val buildHeightMm: Double,
) {
    val buildWidthMm: Double get() = resolutionX * pixelSizeMicrons / 1000.0
    val buildDepthMm: Double get() = resolutionY * pixelSizeMicrons / 1000.0

    companion object {
        /**
         * Starter set of common MSLA printers. Approximate — verify against your machine's LCD
         * spec before trusting build dimensions; full profiles replace this later.
         */
        val builtIn: List<ResinPrinter> = listOf(
            ResinPrinter("Elegoo Mars 3 (4K)", 4098, 2560, 35.0, 175.0),
            ResinPrinter("Elegoo Saturn 3 (12K)", 11520, 5120, 19.0, 260.0),
            ResinPrinter("Anycubic Photon Mono X 6K", 5760, 3600, 34.45, 245.0),
            ResinPrinter("Anycubic Photon Mono 4K", 3840, 2400, 34.5, 165.0),
            ResinPrinter("Phrozen Sonic Mini 8K", 7500, 4623, 22.0, 180.0),
            ResinPrinter("Generic 4K (4098×2560, 35 µm)", 4098, 2560, 35.0, 200.0),
        )

        fun byName(name: String?): ResinPrinter? = builtIn.firstOrNull { it.name == name }
    }
}
