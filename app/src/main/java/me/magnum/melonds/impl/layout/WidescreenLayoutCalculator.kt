package me.magnum.melonds.impl.layout

import me.magnum.melonds.domain.model.BottomScreenMode
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.SCREEN_WIDTH
import me.magnum.melonds.domain.model.WidescreenConfiguration
import me.magnum.melonds.domain.model.WidescreenFallback
import me.magnum.melonds.domain.model.WidescreenMode
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenLayout
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Works out where the screens go when WideMelon is on. Kept free of Android classes so it can be unit tested.
 */
object WidescreenLayoutCalculator {

    data class Input(
        val layout: UILayout,
        val variant: UILayoutVariant,
        val configuration: WidescreenConfiguration,
        /** Only the OpenGL renderer can actually draw the extra 3D at the sides */
        val rendererSupportsWidescreen: Boolean,
        /** Hardcore achievements and similar cases where the game must see stock behaviour */
        val forceNative: Boolean,
        val bottomScreenVisible: Boolean,
        val screensSwapped: Boolean,
    )

    data class Result(
        val layout: UILayout,
        /** Width to hand to the core, in native pixels */
        val viewWidth: Int,
        /** True when the bottom screen toggle does something with the current layout */
        val canToggleBottomScreen: Boolean,
    )

    private const val OVERLAY_MARGIN_FRACTION = 0.03f
    private const val SIDE_BY_SIDE_MAX_WIDTH_FRACTION = 0.4f

    fun calculate(input: Input): Result {
        val unchanged = Result(input.layout, SCREEN_WIDTH, canToggleBottomScreen = false)
        val config = input.configuration
        if (!config.isEnabled) {
            return unchanged
        }

        val variant = input.variant
        val secondaryDisplay = variant.displays.secondaryScreenDisplay
        return if (secondaryDisplay == null) {
            calculateSingleDisplay(input) ?: unchanged
        } else {
            calculateDualDisplay(input, secondaryDisplay.width, secondaryDisplay.height) ?: unchanged
        }
    }

    /**
     * Picks the core view width for an area of the given size. Portrait or square areas end up at 256, which turns
     * widescreen off.
     */
    fun resolveViewWidth(config: WidescreenConfiguration, areaWidth: Int, areaHeight: Int): Int {
        val width = when (config.mode) {
            WidescreenMode.OFF -> SCREEN_WIDTH
            WidescreenMode.AUTO -> {
                if (areaWidth <= 0 || areaHeight <= 0) {
                    SCREEN_WIDTH
                } else {
                    // round to the nearest even width, the core only takes even ones
                    ((SCREEN_HEIGHT * areaWidth.toFloat() / areaHeight) / 2f).roundToInt() * 2
                }
            }
            WidescreenMode.WIDE_16_9 -> WidescreenConfiguration.WIDTH_16_9
            WidescreenMode.WIDE_21_9 -> WidescreenConfiguration.WIDTH_21_9
            WidescreenMode.CUSTOM -> config.customWidth and 1.inv()
        }
        return width.coerceIn(SCREEN_WIDTH, WidescreenConfiguration.MAX_WIDTH)
    }

    /**
     * Largest rect with the given aspect ratio that fits in [area], centered.
     */
    fun fitRect(area: Rect, aspectRatio: Float): Rect {
        if (area.width <= 0 || area.height <= 0) {
            return area
        }

        val areaAspect = area.width.toFloat() / area.height
        val (width, height) = if (areaAspect > aspectRatio) {
            (area.height * aspectRatio).roundToInt() to area.height
        } else {
            area.width to (area.width / aspectRatio).roundToInt()
        }
        return Rect(
            x = area.x + (area.width - width) / 2,
            y = area.y + (area.height - height) / 2,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
        )
    }

    private fun calculateSingleDisplay(input: Input): Result? {
        val mainLayout = input.layout.mainScreenLayout
        val components = mainLayout.components ?: return null
        if (components.any { it.component == LayoutComponent.HYBRID_SCREEN }) {
            return null
        }

        val area = Rect(0, 0, input.variant.uiSize.x, input.variant.uiSize.y)
        // Nothing to gain in portrait, the user's own layout already stacks the screens there
        if (area.width <= area.height * SCREEN_WIDTH / SCREEN_HEIGHT) {
            return null
        }

        val topComponent = components.firstOrNull { it.component == LayoutComponent.TOP_SCREEN }
        val bottomComponent = components.firstOrNull { it.component == LayoutComponent.BOTTOM_SCREEN }
        if (topComponent == null || bottomComponent == null) {
            return null
        }

        val viewWidth = effectiveViewWidth(input, area.width, area.height)
        val topAspect = topScreenAspect(input, viewWidth, area)
        val bottomAspect = SCREEN_WIDTH.toFloat() / SCREEN_HEIGHT
        val config = input.configuration
        val buttons = components.filterNot { it.isScreen() }

        val screens = if (!input.bottomScreenVisible) {
            listOf(topComponent.copy(rect = fitRect(area, topAspect), alpha = 1f, onTop = false))
        } else {
            when (config.bottomScreenMode) {
                BottomScreenMode.SWAP -> {
                    listOf(bottomComponent.copy(rect = fitRect(area, bottomAspect), alpha = 1f, onTop = false))
                }
                BottomScreenMode.OVERLAY -> {
                    val height = (area.height * config.overlaySizePercent / 100f).roundToInt()
                    val width = (height * bottomAspect).roundToInt()
                    val margin = (area.height * OVERLAY_MARGIN_FRACTION).roundToInt()
                    val overlayRect = Rect(area.right - width - margin, area.bottom - height - margin, width, height)
                    listOf(
                        topComponent.copy(rect = fitRect(area, topAspect), alpha = 1f, onTop = false),
                        bottomComponent.copy(rect = overlayRect, alpha = config.overlayOpacityPercent / 100f, onTop = true),
                    )
                }
                BottomScreenMode.SIDE_BY_SIDE -> {
                    val width = min(area.height * bottomAspect, area.width * SIDE_BY_SIDE_MAX_WIDTH_FRACTION).roundToInt()
                    val height = (width / bottomAspect).roundToInt()
                    val bottomRect = Rect(area.right - width, area.y + (area.height - height) / 2, width, height)
                    val topArea = Rect(area.x, area.y, area.width - width, area.height)
                    listOf(
                        topComponent.copy(rect = fitRect(topArea, topAspect), alpha = 1f, onTop = false),
                        bottomComponent.copy(rect = bottomRect, alpha = 1f, onTop = false),
                    )
                }
            }
        }

        // Screens first so buttons stay on top of them
        val newLayout = input.layout.copy(mainScreenLayout = mainLayout.copy(components = screens + withToggleButton(buttons, area)))
        return Result(newLayout, viewWidth, canToggleBottomScreen = true)
    }

    /**
     * Older or custom layouts don't have the bottom screen toggle. Without it there would be no way back to the bottom
     * screen on a touch only device, so add one next to fast forward (or at the top centre).
     */
    private fun withToggleButton(buttons: List<PositionedLayoutComponent>, area: Rect): List<PositionedLayoutComponent> {
        if (buttons.any { it.component == LayoutComponent.BUTTON_TOGGLE_BOTTOM_SCREEN }) {
            return buttons
        }

        val anchor = buttons.firstOrNull { it.component == LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE }
        val rect = if (anchor != null) {
            val spacing = anchor.rect.width / 10
            Rect(anchor.rect.right + spacing, anchor.rect.y, anchor.rect.width, anchor.rect.height)
        } else {
            val size = area.height / 10
            Rect(area.x + (area.width - size) / 2, area.y, size, size)
        }
        return buttons + PositionedLayoutComponent(rect, LayoutComponent.BUTTON_TOGGLE_BOTTOM_SCREEN)
    }

    private fun calculateDualDisplay(input: Input, secondaryWidth: Int, secondaryHeight: Int): Result? {
        // Whichever view shows the DS top screen gets widened. Swapping flips which component that is.
        val wideComponent = if (input.screensSwapped) LayoutComponent.BOTTOM_SCREEN else LayoutComponent.TOP_SCREEN

        val mainLayout = input.layout.mainScreenLayout
        val secondaryLayout = input.layout.secondaryScreenLayout
        val (hostLayout, area) = when {
            mainLayout.hasOnlyScreen(wideComponent) -> mainLayout to Rect(0, 0, input.variant.uiSize.x, input.variant.uiSize.y)
            secondaryLayout.hasOnlyScreen(wideComponent) -> secondaryLayout to Rect(0, 0, secondaryWidth, secondaryHeight)
            // Both screens share a display, widening one of them would squash the other
            else -> return null
        }
        if (area.width <= area.height * SCREEN_WIDTH / SCREEN_HEIGHT) {
            return null
        }

        val viewWidth = effectiveViewWidth(input, area.width, area.height)
        val topAspect = topScreenAspect(input, viewWidth, area)
        val updatedHost = hostLayout.copy(
            components = hostLayout.components?.map {
                if (it.component == wideComponent) it.copy(rect = fitRect(area, topAspect)) else it
            }
        )

        val newLayout = if (hostLayout === mainLayout) {
            input.layout.copy(mainScreenLayout = updatedHost)
        } else {
            input.layout.copy(secondaryScreenLayout = updatedHost)
        }
        return Result(newLayout, viewWidth, canToggleBottomScreen = false)
    }

    private fun effectiveViewWidth(input: Input, areaWidth: Int, areaHeight: Int): Int {
        if (!input.rendererSupportsWidescreen || input.forceNative) {
            return SCREEN_WIDTH
        }
        return resolveViewWidth(input.configuration, areaWidth, areaHeight)
    }

    private fun topScreenAspect(input: Input, viewWidth: Int, area: Rect): Float {
        return when {
            // Auto already picked the width from this area, only rounding separates the two. Fill it exactly
            // instead of leaving a pixel wide gap at the edges.
            viewWidth > SCREEN_WIDTH && input.configuration.mode == WidescreenMode.AUTO -> area.width.toFloat() / area.height
            viewWidth > SCREEN_WIDTH -> viewWidth.toFloat() / SCREEN_HEIGHT
            input.configuration.fallback == WidescreenFallback.STRETCH -> area.width.toFloat() / area.height
            else -> SCREEN_WIDTH.toFloat() / SCREEN_HEIGHT
        }
    }

    private fun ScreenLayout.hasOnlyScreen(component: LayoutComponent): Boolean {
        val screens = components?.filter(PositionedLayoutComponent::isScreen) ?: return false
        return screens.size == 1 && screens.single().component == component
    }
}
