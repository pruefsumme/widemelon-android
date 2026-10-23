package me.magnum.melonds.impl.layout

import me.magnum.melonds.domain.model.BottomScreenMode
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.WidescreenConfiguration
import me.magnum.melonds.domain.model.WidescreenFallback
import me.magnum.melonds.domain.model.WidescreenMode
import me.magnum.melonds.domain.model.layout.Insets
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenLayout
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidescreenLayoutCalculatorTest {

    private val defaultConfig = WidescreenConfiguration(
        mode = WidescreenMode.AUTO,
        customWidth = 342,
        bottomScreenMode = BottomScreenMode.SWAP,
        overlaySizePercent = 40,
        overlayOpacityPercent = 85,
        fallback = WidescreenFallback.STRETCH,
    )

    @Test
    fun autoWidthMatchesDisplayAspect() {
        assertEquals(342, WidescreenLayoutCalculator.resolveViewWidth(defaultConfig, 1920, 1080))
        assertEquals(426, WidescreenLayoutCalculator.resolveViewWidth(defaultConfig, 2400, 1080))
    }

    @Test
    fun autoWidthIsNativeInPortrait() {
        assertEquals(256, WidescreenLayoutCalculator.resolveViewWidth(defaultConfig, 1080, 2400))
    }

    @Test
    fun customWidthIsEvenAndClamped() {
        assertEquals(300, WidescreenLayoutCalculator.resolveViewWidth(defaultConfig.copy(mode = WidescreenMode.CUSTOM, customWidth = 301), 1, 1))
        assertEquals(768, WidescreenLayoutCalculator.resolveViewWidth(defaultConfig.copy(mode = WidescreenMode.CUSTOM, customWidth = 2000), 1, 1))
    }

    @Test
    fun fitRectPillarboxesNarrowContent() {
        val rect = WidescreenLayoutCalculator.fitRect(Rect(0, 0, 2400, 1080), 4f / 3f)
        assertEquals(Rect(480, 0, 1440, 1080), rect)
    }

    @Test
    fun swapModeShowsOnlyTopScreenFillingTheDisplay() {
        val result = WidescreenLayoutCalculator.calculate(input())

        val screens = result.layout.mainScreenLayout.components!!.filter { it.isScreen() }
        assertEquals(1, screens.size)
        assertEquals(LayoutComponent.TOP_SCREEN, screens.single().component)
        assertEquals(Rect(0, 0, 2400, 1080), screens.single().rect)
        assertEquals(426, result.viewWidth)
        assertTrue(result.canToggleBottomScreen)
    }

    @Test
    fun swapModeShowsOnlyBottomScreenWhenToggled() {
        val result = WidescreenLayoutCalculator.calculate(input(bottomVisible = true))

        val screens = result.layout.mainScreenLayout.components!!.filter { it.isScreen() }
        assertEquals(LayoutComponent.BOTTOM_SCREEN, screens.single().component)
        assertEquals(Rect(480, 0, 1440, 1080), screens.single().rect)
    }

    @Test
    fun buttonsAreKeptAboveScreens() {
        val result = WidescreenLayoutCalculator.calculate(input())

        val components = result.layout.mainScreenLayout.components!!
        assertTrue(components.first().isScreen())
        assertTrue(components.drop(1).none { it.isScreen() })
    }

    @Test
    fun toggleButtonIsAddedWhenLayoutLacksIt() {
        val result = WidescreenLayoutCalculator.calculate(input())

        val components = result.layout.mainScreenLayout.components!!
        assertEquals(1, components.count { it.component == LayoutComponent.BUTTON_TOGGLE_BOTTOM_SCREEN })
    }

    @Test
    fun overlayModeFloatsBottomScreenInCorner() {
        val config = defaultConfig.copy(bottomScreenMode = BottomScreenMode.OVERLAY)
        val result = WidescreenLayoutCalculator.calculate(input(config = config, bottomVisible = true))

        val bottom = result.layout.mainScreenLayout.components!!.first { it.component == LayoutComponent.BOTTOM_SCREEN }
        assertEquals(432, bottom.rect.height)
        assertEquals(576, bottom.rect.width)
        assertTrue(bottom.onTop)
        assertEquals(0.85f, bottom.alpha, 0.001f)
        assertTrue(bottom.rect.right <= 2400 && bottom.rect.bottom <= 1080)
    }

    @Test
    fun sideBySideKeepsBothScreensInsideTheDisplay() {
        val config = defaultConfig.copy(bottomScreenMode = BottomScreenMode.SIDE_BY_SIDE)
        val result = WidescreenLayoutCalculator.calculate(input(config = config, bottomVisible = true))

        val components = result.layout.mainScreenLayout.components!!
        val top = components.first { it.component == LayoutComponent.TOP_SCREEN }
        val bottom = components.first { it.component == LayoutComponent.BOTTOM_SCREEN }
        assertTrue(top.rect.right <= bottom.rect.x)
        assertEquals(2400, bottom.rect.right)
    }

    @Test
    fun otherRenderersStretchAtNativeWidth() {
        val result = WidescreenLayoutCalculator.calculate(input(supportsWidescreen = false))

        assertEquals(256, result.viewWidth)
        val top = result.layout.mainScreenLayout.components!!.first { it.component == LayoutComponent.TOP_SCREEN }
        assertEquals(Rect(0, 0, 2400, 1080), top.rect)
    }

    @Test
    fun portraitLayoutIsLeftAlone() {
        val portrait = input().copy(variant = variant(1080, 2400))
        val result = WidescreenLayoutCalculator.calculate(portrait)

        assertEquals(portrait.layout, result.layout)
        assertEquals(256, result.viewWidth)
        assertFalse(result.canToggleBottomScreen)
    }

    @Test
    fun dualDisplayWidensTopScreenOnItsOwnDisplay() {
        val layout = UILayout(
            mainScreenLayout = ScreenLayout(listOf(screen(LayoutComponent.TOP_SCREEN, Rect(240, 0, 1440, 1080)))),
            secondaryScreenLayout = ScreenLayout(listOf(screen(LayoutComponent.BOTTOM_SCREEN, Rect(0, 0, 1240, 930)))),
        )
        val dual = input().copy(
            layout = layout,
            variant = variant(1920, 1080, secondary = LayoutDisplay(1, LayoutDisplay.Type.BUILT_IN, 1240, 1080)),
        )

        val result = WidescreenLayoutCalculator.calculate(dual)

        assertEquals(Rect(0, 0, 1920, 1080), result.layout.mainScreenLayout.components!!.single().rect)
        assertEquals(layout.secondaryScreenLayout, result.layout.secondaryScreenLayout)
        assertEquals(342, result.viewWidth)
        assertFalse(result.canToggleBottomScreen)
    }

    @Test
    fun offModeChangesNothing() {
        val off = input(config = defaultConfig.copy(mode = WidescreenMode.OFF))
        val result = WidescreenLayoutCalculator.calculate(off)

        assertEquals(off.layout, result.layout)
        assertEquals(256, result.viewWidth)
    }

    @Test
    fun hybridLayoutsAreSkipped() {
        val hybrid = input().copy(
            layout = UILayout(ScreenLayout(listOf(screen(LayoutComponent.HYBRID_SCREEN, Rect(0, 0, 2400, 1080)))), ScreenLayout())
        )
        val result = WidescreenLayoutCalculator.calculate(hybrid)

        assertEquals(hybrid.layout, result.layout)
        assertNull(result.layout.secondaryScreenLayout.components)
    }

    private fun input(
        config: WidescreenConfiguration = defaultConfig,
        bottomVisible: Boolean = false,
        supportsWidescreen: Boolean = true,
    ) = WidescreenLayoutCalculator.Input(
        layout = landscapeLayout(),
        variant = variant(2400, 1080),
        configuration = config,
        rendererSupportsWidescreen = supportsWidescreen,
        forceNative = false,
        bottomScreenVisible = bottomVisible,
        screensSwapped = false,
    )

    private fun landscapeLayout() = UILayout(
        mainScreenLayout = ScreenLayout(
            listOf(
                screen(LayoutComponent.TOP_SCREEN, Rect(0, 0, 1584, 1188)),
                screen(LayoutComponent.BOTTOM_SCREEN, Rect(1584, 500, 816, 612)),
                PositionedLayoutComponent(Rect(2200, 900, 100, 100), LayoutComponent.BUTTON_A),
            )
        ),
        secondaryScreenLayout = ScreenLayout(),
    )

    private fun screen(component: LayoutComponent, rect: Rect) = PositionedLayoutComponent(rect, component)

    private fun variant(width: Int, height: Int, secondary: LayoutDisplay? = null) = UILayoutVariant(
        uiSize = Point(width, height),
        uiInsets = Insets.Zero,
        orientation = if (width > height) Orientation.LANDSCAPE else Orientation.PORTRAIT,
        folds = emptyList(),
        displays = LayoutDisplayPair(LayoutDisplay(0, LayoutDisplay.Type.BUILT_IN, width, height), secondary),
    )
}
