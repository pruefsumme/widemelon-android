package me.magnum.melonds.domain.model

enum class WidescreenMode {
    OFF,
    /** Pick the width that matches the display the top screen is shown on */
    AUTO,
    WIDE_16_9,
    WIDE_21_9,
    CUSTOM,
}

/**
 * How the bottom screen is shown when the top screen fills the whole display on a single screen device.
 */
enum class BottomScreenMode {
    /** Toggling replaces the top screen with the bottom screen */
    SWAP,
    /** Bottom screen floats in a corner above the top screen */
    OVERLAY,
    /** Bottom screen is docked next to a slightly smaller top screen */
    SIDE_BY_SIDE,
}

/**
 * What to do when the active renderer can't render real widescreen (everything except OpenGL).
 */
enum class WidescreenFallback {
    STRETCH,
    NATIVE,
}

data class WidescreenConfiguration(
    val mode: WidescreenMode,
    val customWidth: Int,
    val bottomScreenMode: BottomScreenMode,
    val overlaySizePercent: Int,
    val overlayOpacityPercent: Int,
    val fallback: WidescreenFallback,
) {
    val isEnabled get() = mode != WidescreenMode.OFF

    companion object {
        const val WIDTH_16_9 = 342
        const val WIDTH_21_9 = 448
        const val MAX_WIDTH = 768
    }
}
