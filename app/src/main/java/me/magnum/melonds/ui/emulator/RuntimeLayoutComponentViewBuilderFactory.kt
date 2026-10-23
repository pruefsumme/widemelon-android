package me.magnum.melonds.ui.emulator

import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.common.LayoutComponentViewBuilderFactory
import me.magnum.melonds.ui.common.componentbuilders.*
import me.magnum.melonds.ui.emulator.input.componentbuilder.RuntimeScreenLayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.componentbuilder.ToggleableSingleButtonLayoutComponentViewBuilder

class RuntimeLayoutComponentViewBuilderFactory : LayoutComponentViewBuilderFactory {
    private val layoutComponentViewBuilderCache = mutableMapOf<LayoutComponent, LayoutComponentViewBuilder>()

    override fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder {
        return layoutComponentViewBuilderCache.getOrElse(layoutComponent) {
            val builder = when (layoutComponent) {
                LayoutComponent.TOP_SCREEN -> RuntimeScreenLayoutComponentViewBuilder()
                LayoutComponent.BOTTOM_SCREEN -> RuntimeScreenLayoutComponentViewBuilder()
                LayoutComponent.HYBRID_SCREEN -> RuntimeScreenLayoutComponentViewBuilder(256f / (192f * 2f))
                LayoutComponent.SLOT2_ANALOG -> Slot2AnalogLayoutComponentViewBuilder()
                LayoutComponent.DPAD -> DpadLayoutComponentViewBuilder()
                LayoutComponent.BUTTONS -> ButtonsLayoutComponentViewBuilder()
                LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE,
                LayoutComponent.BUTTON_MICROPHONE_TOGGLE,
                LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT,
                LayoutComponent.BUTTON_TOGGLE_BOTTOM_SCREEN -> ToggleableSingleButtonLayoutComponentViewBuilder(layoutComponent)
                else -> SingleButtonLayoutComponentViewBuilder(layoutComponent)
            }

            layoutComponentViewBuilderCache[layoutComponent] = builder
            builder
        }
    }
}
