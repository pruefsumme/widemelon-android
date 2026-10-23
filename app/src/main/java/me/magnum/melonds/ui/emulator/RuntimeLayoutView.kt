package me.magnum.melonds.ui.emulator

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isGone
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.input.SoftInputBehaviour
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.ui.common.LayoutView
import me.magnum.melonds.ui.emulator.input.ButtonsInputHandler
import me.magnum.melonds.ui.emulator.input.DpadInputHandler
import me.magnum.melonds.ui.emulator.input.FrontendInputHandler
import me.magnum.melonds.ui.emulator.input.HybridScreenTouchscreenInputHandler
import me.magnum.melonds.ui.emulator.input.IInputListener
import me.magnum.melonds.ui.emulator.input.Slot2AnalogInput
import me.magnum.melonds.ui.emulator.input.Slot2AnalogInputHandler
import me.magnum.melonds.ui.emulator.input.SingleButtonInputHandler
import me.magnum.melonds.ui.emulator.input.TouchscreenInputHandler
import me.magnum.melonds.ui.emulator.input.view.ToggleableImageView
import me.magnum.melonds.ui.emulator.model.ConnectedControllersState
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget
import javax.inject.Inject

@AndroidEntryPoint
class RuntimeLayoutView(context: Context, attrs: AttributeSet? = null) : LayoutView(context, attrs) {

    @Inject
    lateinit var touchVibrator: TouchVibrator

    private var currentRuntimeLayout: RuntimeInputLayoutConfiguration? = null
    private var frontendInputHandler: IInputListener? = null
    private var systemInputHandler: IInputListener? = null
    private var isSoftInputVisible = true
    private var areScreensSwapped = false
    private var connectedControllersState: ConnectedControllersState = ConnectedControllersState.NoControllers
    private var slot2AnalogInput: Slot2AnalogInput? = null
    private var slot2AnalogHandler: Slot2AnalogInputHandler? = null
    private val virtualButtonInputs = mutableMapOf<LayoutComponent, SingleButtonInputHandler>()

    fun setSlot2AnalogInput(input: Slot2AnalogInput) {
        releaseVirtualButtonInputs()
        slot2AnalogInput = input
        updateInputs()
    }

    fun setFrontendInputHandler(frontendInputHandler: FrontendInputHandler) {
        this.frontendInputHandler = frontendInputHandler
        updateInputs()
    }

    fun setSystemInputHandler(systemInputHandler: IInputListener) {
        this.systemInputHandler = systemInputHandler
        updateInputs()
    }

    fun setConnectedControllersState(state: ConnectedControllersState) {
        connectedControllersState = state
        updateVisibility()
    }

    fun toggleSoftInputVisibility() {
        isSoftInputVisible = !isSoftInputVisible
        setLayoutComponentToggleState(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT, isSoftInputVisible)
        updateVisibility()
    }

    fun swapScreens() {
        areScreensSwapped = !areScreensSwapped
        updateScreenInputs()
    }

    fun areScreensSwapped(): Boolean {
        return areScreensSwapped
    }

    fun setLayoutComponentToggleState(layoutComponent: LayoutComponent, isEnabled: Boolean) {
        val toggleableImageView = getLayoutComponentView(layoutComponent)?.view as? ToggleableImageView ?: return
        toggleableImageView.setToggleState(isEnabled)
    }

    fun instantiateLayout(runtimeLayout: RuntimeInputLayoutConfiguration, layoutTarget: LayoutTarget) {
        currentRuntimeLayout = runtimeLayout
        instantiateLayout(runtimeLayout.layout, layoutTarget)
        updateInputs()
        updateVisibility()
        setLayoutComponentToggleState(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT, isSoftInputVisible)
    }

    private fun updateInputs() {
        releaseVirtualButtonInputs()
        virtualButtonInputs.clear()
        slot2AnalogHandler = null
        val currentRuntimeLayout = currentRuntimeLayout
        if (currentRuntimeLayout == null) {
            isGone = true
            return
        }

        isVisible = true
        val inputAlpha = currentRuntimeLayout.softInputOpacity / 100f

        slot2AnalogInput?.let { input ->
            getLayoutComponentView(LayoutComponent.SLOT2_ANALOG)?.view?.let { view ->
                Slot2AnalogInputHandler(input).also {
                    slot2AnalogHandler = it
                    view.setOnTouchListener(it)
                }
            }
        }
        val enableHapticFeedback = currentRuntimeLayout.isHapticFeedbackEnabled
        systemInputHandler?.let {
            getLayoutComponentView(LayoutComponent.DPAD)?.view?.setOnTouchListener(DpadInputHandler(it, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTONS)?.view?.setOnTouchListener(ButtonsInputHandler(it, enableHapticFeedback, touchVibrator))
            for (component in listOf(LayoutComponent.BUTTON_A, LayoutComponent.BUTTON_B, LayoutComponent.BUTTON_X, LayoutComponent.BUTTON_Y)) {
                val componentView = getLayoutComponentView(component) ?: continue
                val handler = SingleButtonInputHandler(it, component.matchingInputs.single(), enableHapticFeedback, touchVibrator, componentView.buttonMode)
                virtualButtonInputs[component] = handler
                componentView.view.setOnTouchListener(handler)
            }
            getLayoutComponentView(LayoutComponent.BUTTON_L)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.L, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_R)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.R, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_SELECT)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.SELECT, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_START)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.START, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_HINGE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.HINGE, enableHapticFeedback, touchVibrator))
        }
        frontendInputHandler?.let {
            getLayoutComponentView(LayoutComponent.BUTTON_RESET)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.RESET, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_PAUSE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.PAUSE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.FAST_FORWARD, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_MICROPHONE_TOGGLE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.MICROPHONE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.TOGGLE_SOFT_INPUT, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_SWAP_SCREENS)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.SWAP_SCREENS, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_TOGGLE_BOTTOM_SCREEN)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.TOGGLE_BOTTOM_SCREEN, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_QUICK_SAVE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.QUICK_SAVE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_QUICK_LOAD)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.QUICK_LOAD, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_REWIND)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.REWIND, enableHapticFeedback, touchVibrator))
        }

        getLayoutComponentViews().forEach {
            if (!it.component.isScreen()) {
                it.view.apply {
                    alpha = inputAlpha
                }
            }
        }

        updateScreenInputs()
    }

    private fun updateScreenInputs() {
        val (touchScreenComponent, nonTouchScreenComponent) = if (areScreensSwapped) {
            LayoutComponent.TOP_SCREEN to LayoutComponent.BOTTOM_SCREEN
        } else {
            LayoutComponent.BOTTOM_SCREEN to LayoutComponent.TOP_SCREEN
        }
        systemInputHandler?.let {
            getLayoutComponentView(touchScreenComponent)?.view?.setOnTouchListener(TouchscreenInputHandler(it))
            getLayoutComponentView(LayoutComponent.HYBRID_SCREEN)?.view?.setOnTouchListener(HybridScreenTouchscreenInputHandler(it))
        }
        getLayoutComponentView(nonTouchScreenComponent)?.view?.setOnTouchListener(null)
    }

    fun releaseVirtualButtonInputs() {
        slot2AnalogHandler?.release()
        virtualButtonInputs.values.forEach { it.release() }
    }

    override fun destroyLayout() {
        releaseVirtualButtonInputs()
        virtualButtonInputs.clear()
        slot2AnalogHandler = null
        super.destroyLayout()
    }

    override fun onDetachedFromWindow() {
        releaseVirtualButtonInputs()
        super.onDetachedFromWindow()
    }

    private fun updateVisibility() {
        val currentConnectedControllersState = connectedControllersState
        var hiddenComponents = when(currentRuntimeLayout?.softInputBehaviour) {
            SoftInputBehaviour.ALWAYS_VISIBLE -> emptyList()
            SoftInputBehaviour.HIDE_SYSTEM_BUTTONS_WHEN_CONTROLLERS_CONNECTED, null -> {
                when(currentConnectedControllersState) {
                    ConnectedControllersState.NoControllers -> emptyList()
                    is ConnectedControllersState.ControllersConnected -> listOf(
                        LayoutComponent.BUTTONS,
                        LayoutComponent.BUTTON_A,
                        LayoutComponent.BUTTON_B,
                        LayoutComponent.BUTTON_X,
                        LayoutComponent.BUTTON_Y,
                        LayoutComponent.SLOT2_ANALOG,
                        LayoutComponent.DPAD,
                        LayoutComponent.BUTTON_L,
                        LayoutComponent.BUTTON_R,
                        LayoutComponent.BUTTON_START,
                        LayoutComponent.BUTTON_SELECT
                    )
                }
            }
            SoftInputBehaviour.HIDE_ALL_BUTTONS_ASSIGNED_TO_CONNECTED_CONTROLLERS -> when(currentConnectedControllersState) {
                ConnectedControllersState.NoControllers -> emptyList()
                is ConnectedControllersState.ControllersConnected -> {
                    LayoutComponent.entries.filter {
                        // The component can be hidden if all matching inputs are assigned to connected controllers
                        it != LayoutComponent.SLOT2_ANALOG && it.matchingInputs.all {
                            currentConnectedControllersState.assignedInputs.contains(it)
                        }
                    }
                }
            }
            SoftInputBehaviour.ALWAYS_INVISIBLE -> LayoutComponent.entries.toList()
        }

        if (!isSoftInputVisible) {
            // Hide everything except the soft input toggle button if it was not already hidden by the soft input behaviour logic
            hiddenComponents = if (hiddenComponents.contains(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT)) {
                LayoutComponent.entries.toList()
            } else {
                LayoutComponent.entries.toList().filter { it != LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT}
            }
        }

        val visibleComponents = LayoutComponent.entries.filter { !hiddenComponents.contains(it) }

        hiddenComponents.forEach { component ->
            if (!component.isScreen()) {
                if (component == LayoutComponent.SLOT2_ANALOG) slot2AnalogHandler?.release()
                virtualButtonInputs[component]?.release()
                getLayoutComponentView(component)?.view?.isVisible = false
            }
        }
        visibleComponents.forEach { component ->
            if (!component.isScreen()) {
                getLayoutComponentView(component)?.view?.isVisible = true
            }
        }
    }
}
