package me.magnum.melonds.ui.emulator.input

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import java.util.Locale
import kotlin.math.absoluteValue

class InputProcessor(private val controllerConfiguration: ControllerConfiguration, private val systemInputListener: IInputListener, private val frontendInputListener: IInputListener, private val slot2AnalogInput: Slot2AnalogInput) : INativeInputListener {
    companion object {
        private const val TAG = "InputProcessor"
        private const val SLOT2_ANALOG_LOG_INTERVAL_MS = 1500L
        private const val SLOT2_RAW_ANALOG_PRIORITY_MS = 150L
        private val slot2XAxisFallbackCodes = intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
        )
        private val slot2YAxisFallbackCodes = intArrayOf(
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS,
        )
    }

    private val axisStates: Map<Axis, AxisState>
    private data class DeviceKey(val deviceId: Int, val keyCode: Int)
    private data class KeyChord(val input: Input, val deviceId: Int?, val keys: Set<Int>)
    private data class ActiveChord(val chord: KeyChord, val fromController: Boolean)
    private data class PressedInput(val input: Input, val fromController: Boolean)
    private val keyChords = controllerConfiguration.inputMapper.filter { !it.input.isSystemInput }.flatMap { config ->
        listOf(config.assignment, config.altAssignment).mapNotNull { assignment ->
            (assignment as? InputConfig.Assignment.Key)?.takeIf {
                it.modifierKeyCode != null && it.modifierKeyCode != it.keyCode
            }?.let { KeyChord(config.input, it.deviceId, it.keyCodes.toSet()) }
        }
    }
    private val pressedChordKeys = mutableSetOf<DeviceKey>()
    private val consumedChordKeys = mutableSetOf<DeviceKey>()
    private val chordSingles = mutableMapOf<DeviceKey, PressedInput>()
    private val activeChords = mutableMapOf<Int, ActiveChord>()
    private var lastSlot2AnalogLogAtMs = 0L
    private var lastSlot2RawAnalogEventAtMs = 0L
    private var slot2DigitalLeftPressed = false
    private var slot2DigitalRightPressed = false
    private var slot2DigitalUpPressed = false
    private var slot2DigitalDownPressed = false

    init {
        val axis = controllerConfiguration.inputMapper.flatMap { inputConfig ->
            listOf(inputConfig.assignment, inputConfig.altAssignment)
        }.mapNotNull { assignment ->
            (assignment as? InputConfig.Assignment.Axis)?.let {
                Axis(it.deviceId, it.axisCode, it.direction)
            }
        }

        axisStates = axis.associateWith { AxisState(0f, false) }
    }

    override fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        val fromController = keyEvent.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)
            || keyEvent.isFromSource(InputDevice.SOURCE_JOYSTICK)
            || keyEvent.isFromSource(InputDevice.SOURCE_GAMEPAD)
            || keyEvent.isFromSource(InputDevice.SOURCE_DPAD)
            || keyEvent.device?.supportsSource(InputDevice.SOURCE_JOYSTICK) == true
            || keyEvent.device?.supportsSource(InputDevice.SOURCE_GAMEPAD) == true

        processChordKeyEvent(keyEvent, fromController)?.let { return it }
        val input = controllerConfiguration.keyToInput(keyEvent.keyCode) ?: return false
        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount > 0
            && (input == Input.CYCLE_LAYOUT || input == Input.EXIT_GAME || input == Input.TOGGLE_BOTTOM_SCREEN)
        ) {
            return true
        }

        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                dispatchInputPressed(input, fromController)
                return true
            }
            KeyEvent.ACTION_UP -> {
                dispatchInputReleased(input, fromController)
                return true
            }
        }
        return false
    }

    private fun processChordKeyEvent(event: KeyEvent, fromController: Boolean): Boolean? {
        if (keyChords.isEmpty()) return null
        val candidates = keyChords.filter { (it.deviceId == null || it.deviceId == event.deviceId) && event.keyCode in it.keys }
        if (candidates.isEmpty()) return null
        val key = DeviceKey(event.deviceId, event.keyCode)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0 || !pressedChordKeys.add(key)) return true
                if (activeChords.containsKey(event.deviceId)) {
                    consumedChordKeys.add(key)
                    return true
                }
                val chord = candidates.firstOrNull { it.keys.all { code -> DeviceKey(event.deviceId, code) in pressedChordKeys } }
                if (chord != null) {
                    for (code in chord.keys) {
                        val member = DeviceKey(event.deviceId, code)
                        consumedChordKeys.add(member)
                        chordSingles.remove(member)?.let { dispatchInputReleased(it.input, it.fromController) }
                    }
                    activeChords[event.deviceId] = ActiveChord(chord, fromController)
                    dispatchInputPressed(chord.input, fromController)
                } else if (key !in consumedChordKeys) {
                    controllerConfiguration.keyToInput(event.keyCode)?.let { input ->
                        chordSingles[key] = PressedInput(input, fromController)
                        dispatchInputPressed(input, fromController)
                    }
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                pressedChordKeys.remove(key)
                consumedChordKeys.remove(key)
                activeChords[event.deviceId]?.takeIf { event.keyCode in it.chord.keys }?.let {
                    activeChords.remove(event.deviceId)
                    dispatchInputReleased(it.chord.input, it.fromController)
                }
                chordSingles.remove(key)?.let { dispatchInputReleased(it.input, it.fromController) }
                return true
            }
            else -> return null
        }
    }

    override fun releaseChordInputs() {
        val chords = activeChords.values.toList()
        val singles = chordSingles.values.toList()
        activeChords.clear()
        chordSingles.clear()
        consumedChordKeys.clear()
        pressedChordKeys.clear()
        chords.forEach { dispatchInputReleased(it.chord.input, it.fromController) }
        singles.forEach { dispatchInputReleased(it.input, it.fromController) }
    }

    override fun onMotionEvent(motionEvent: MotionEvent): Boolean {
        if (isControllerMotionEvent(motionEvent)) {
            val slot2Handled = processSlot2AnalogFromMotionEvent(motionEvent)

            val deviceAxis = axisStates.filterKeys { it.deviceId == null || it.deviceId == motionEvent.deviceId }
            deviceAxis.forEach {
                val axis = it.key
                val axisState = it.value

                val newValue = motionEvent.getAxisValue(axis.axisCode)
                val clampedValue = when (axis.direction) {
                    InputConfig.Assignment.Axis.Direction.POSITIVE -> newValue.coerceAtLeast(0f)
                    InputConfig.Assignment.Axis.Direction.NEGATIVE -> newValue.coerceAtMost(0f)
                }

                if (axisState.shouldToggleFor(newValue = clampedValue)) {
                    controllerConfiguration.axisToInput(axis.axisCode, axis.direction)?.let { input ->
                        if (axisState.active) {
                            axisState.active = false
                            dispatchInputReleased(input, fromController = true)
                        } else {
                            axisState.active = true
                            dispatchInputPressed(input, fromController = true)
                        }
                    }
                }
                axisState.value = clampedValue
            }
            return slot2Handled || deviceAxis.isNotEmpty()
        } else {
            return false
        }
    }

    override fun onMotionEventSlot2(motionEvent: MotionEvent): Boolean {
        if (!isControllerMotionEvent(motionEvent)) {
            return false
        }
        return processSlot2AnalogFromMotionEvent(motionEvent)
    }

    private fun dispatchInputPressed(input: Input, fromController: Boolean) {
        updateSlot2DigitalFallback(input, pressed = true, fromController = fromController)
        if (input.isSystemInput) {
            systemInputListener.onKeyPress(input)
        } else {
            frontendInputListener.onKeyPress(input)
        }
    }

    private fun dispatchInputReleased(input: Input, fromController: Boolean) {
        updateSlot2DigitalFallback(input, pressed = false, fromController = fromController)
        if (input.isSystemInput) {
            systemInputListener.onKeyReleased(input)
        } else {
            frontendInputListener.onKeyReleased(input)
        }
    }

    private fun updateSlot2DigitalFallback(input: Input, pressed: Boolean, fromController: Boolean) {
        if (!fromController) {
            return
        }

        when (input) {
            Input.LEFT -> slot2DigitalLeftPressed = pressed
            Input.RIGHT -> slot2DigitalRightPressed = pressed
            Input.UP -> slot2DigitalUpPressed = pressed
            Input.DOWN -> slot2DigitalDownPressed = pressed
            else -> return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastSlot2RawAnalogEventAtMs <= SLOT2_RAW_ANALOG_PRIORITY_MS) {
            return
        }

        val digitalX = when {
            slot2DigitalLeftPressed == slot2DigitalRightPressed -> 0f
            slot2DigitalLeftPressed -> -1f
            else -> 1f
        }
        val digitalY = when {
            slot2DigitalUpPressed == slot2DigitalDownPressed -> 0f
            slot2DigitalUpPressed -> -1f
            else -> 1f
        }

        slot2AnalogInput.setPhysical(digitalX, digitalY)
        if ((digitalX.absoluteValue > 0f || digitalY.absoluteValue > 0f)
            && now - lastSlot2AnalogLogAtMs >= SLOT2_ANALOG_LOG_INTERVAL_MS
        ) {
            lastSlot2AnalogLogAtMs = now
            Log.w(
                TAG,
                "slot2AnalogInput source=digital-fallback x=${"%.3f".format(Locale.US, digitalX)} y=${"%.3f".format(Locale.US, digitalY)}",
            )
        }
    }

    private fun isControllerMotionEvent(motionEvent: MotionEvent): Boolean {
        return motionEvent.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)
            || motionEvent.isFromSource(InputDevice.SOURCE_JOYSTICK)
            || motionEvent.isFromSource(InputDevice.SOURCE_GAMEPAD)
    }

    private fun processSlot2AnalogFromMotionEvent(motionEvent: MotionEvent): Boolean {
        val slot2Mapping = controllerConfiguration.slot2AnalogMapping
        val mappedDeviceId = slot2Mapping.effectiveDeviceId()
        val mappedDeviceConnected = mappedDeviceId == null || InputDevice.getDevice(mappedDeviceId) != null
        if (mappedDeviceId != null && mappedDeviceId != motionEvent.deviceId && mappedDeviceConnected) {
            return false
        }

        val deadzone = slot2Mapping.normalizedDeadzone()
        val rawAnalogX = resolveSlot2AxisValue(
            motionEvent = motionEvent,
            preferredAxisCode = slot2Mapping.axisXCode,
            fallbackAxisCodes = slot2XAxisFallbackCodes,
        ).coerceIn(-1f, 1f)
        val rawAnalogY = resolveSlot2AxisValue(
            motionEvent = motionEvent,
            preferredAxisCode = slot2Mapping.axisYCode,
            fallbackAxisCodes = slot2YAxisFallbackCodes,
        ).coerceIn(-1f, 1f)
        val mappedX = if (slot2Mapping.invertX) -rawAnalogX else rawAnalogX
        val mappedY = if (slot2Mapping.invertY) -rawAnalogY else rawAnalogY
        val analogX = if (mappedX.absoluteValue < deadzone) 0f else mappedX
        val analogY = if (mappedY.absoluteValue < deadzone) 0f else mappedY
        slot2AnalogInput.setPhysical(analogX, analogY)
        val now = SystemClock.uptimeMillis()
        lastSlot2RawAnalogEventAtMs = now
        if ((analogX.absoluteValue > 0f || analogY.absoluteValue > 0f)
            && now - lastSlot2AnalogLogAtMs >= SLOT2_ANALOG_LOG_INTERVAL_MS
        ) {
            lastSlot2AnalogLogAtMs = now
            Log.w(
                TAG,
                "slot2AnalogInput deviceId=${motionEvent.deviceId} source=0x${motionEvent.source.toString(16)} axisX=${slot2Mapping.axisXCode} axisY=${slot2Mapping.axisYCode} x=${"%.3f".format(Locale.US, analogX)} y=${"%.3f".format(Locale.US, analogY)} deadzone=${"%.3f".format(Locale.US, deadzone)}",
            )
        }
        return true
    }

    private fun resolveSlot2AxisValue(
        motionEvent: MotionEvent,
        preferredAxisCode: Int,
        fallbackAxisCodes: IntArray,
    ): Float {
        val preferredValue = motionEvent.getAxisValue(preferredAxisCode)
        if (deviceSupportsAxis(motionEvent, preferredAxisCode)) {
            return preferredValue
        }

        if (preferredValue.absoluteValue > 0.0001f) {
            return preferredValue
        }

        var bestAxisValue = preferredValue
        var bestAxisAbs = preferredValue.absoluteValue
        fallbackAxisCodes.forEach { axisCode ->
            if (axisCode == preferredAxisCode) {
                return@forEach
            }
            val axisValue = motionEvent.getAxisValue(axisCode)
            val axisAbs = axisValue.absoluteValue
            if (axisAbs > bestAxisAbs) {
                bestAxisAbs = axisAbs
                bestAxisValue = axisValue
            }
        }
        return bestAxisValue
    }

    private fun deviceSupportsAxis(motionEvent: MotionEvent, axisCode: Int): Boolean {
        val device = motionEvent.device ?: return false
        return device.getMotionRange(axisCode, motionEvent.source) != null
            || device.getMotionRange(axisCode, InputDevice.SOURCE_CLASS_JOYSTICK) != null
            || device.getMotionRange(axisCode, InputDevice.SOURCE_JOYSTICK) != null
            || device.getMotionRange(axisCode, InputDevice.SOURCE_GAMEPAD) != null
            || device.getMotionRange(axisCode) != null
    }

    private data class Axis(
        val deviceId: Int?,
        val axisCode: Int,
        val direction: InputConfig.Assignment.Axis.Direction,
    )

    private data class AxisState(
        var value: Float,
        var active: Boolean,
    ) {
        fun shouldToggleFor(newValue: Float): Boolean {
            return if (active) {
                newValue.absoluteValue < 0.5f
            } else {
                newValue.absoluteValue >= 0.5f
            }
        }
    }
}
