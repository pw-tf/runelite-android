package net.runelite.mp.konkr

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs
import kotlin.math.max

/**
 * Entry point for controller input on the konkr build. Each physical button press maps to
 * at most one [Action] (see [ActionExecutor]); OS key auto-repeat is swallowed, and a press
 * reported both as a key and as an axis is only acted on once.
 *
 * [MainActivity] hands every key and generic motion event here first (through
 * FlavorHooks); anything that didn't come from a gamepad is declined so touch, keyboards
 * and the IME keep their normal paths.
 *
 * Buttons go straight to [ActionExecutor]. Stick axes are only cached here; [StickCursor]
 * reads them once per display frame, which keeps cursor speed independent of how often the
 * controller reports.
 *
 * Everything runs on the main thread.
 */
object GamepadInput
{
    /** Latest stick / trigger values, -1..1 (triggers 0..1), deadzone not yet applied. */
    var leftX = 0f; private set
    var leftY = 0f; private set
    var rightX = 0f; private set
    var rightY = 0f; private set
    var leftTrigger = 0f; private set
    var rightTrigger = 0f; private set

    /** Diagnostics for the settings panel. */
    val lastKey = mutableStateOf("—")
    val lastAxes = mutableStateOf("—")
    val connected = mutableStateOf<List<String>>(emptyList())

    /**
     * While non-null, the next controller button press is delivered here instead of being
     * acted on. The settings panel uses it to rebind a button by pressing it.
     */
    val capture = mutableStateOf<((Int) -> Unit)?>(null)

    /** Keys a controller may send that always stay with the system. */
    private val SYSTEM_KEYS = setOf(
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_APP_SWITCH,
    )

    /** What each held button triggered on press, so release undoes the same thing even if
     *  the binding changed in between. */
    private val held = HashMap<Int, Action>()

    /** Codes that have arrived as real key events. Triggers and the D-pad hat are only
     *  synthesized from axes on controllers that don't also send keys for them. */
    private val realKeys = HashSet<Int>()

    private var triggerThreshold = -1
    private var l2Latch = AxisLatch(0.5f)
    private var r2Latch = AxisLatch(0.5f)
    private val hatLatches = mapOf(
        GamepadKeys.DPAD_LEFT to AxisLatch(0.5f),
        GamepadKeys.DPAD_RIGHT to AxisLatch(0.5f),
        GamepadKeys.DPAD_UP to AxisLatch(0.5f),
        GamepadKeys.DPAD_DOWN to AxisLatch(0.5f),
    )

    private var initialized = false

    fun init(context: Context)
    {
        if (initialized) return
        val im = context.applicationContext.getSystemService(InputManager::class.java) ?: return
        initialized = true
        im.registerInputDeviceListener(object : InputManager.InputDeviceListener
        {
            override fun onInputDeviceAdded(deviceId: Int) = refreshDevices()
            override fun onInputDeviceChanged(deviceId: Int) = refreshDevices()
            override fun onInputDeviceRemoved(deviceId: Int)
            {
                refreshDevices()
                // A controller vanishing mid-press must not leave a button, modifier or
                // camera key stuck down.
                resetAll()
            }
        }, Handler(Looper.getMainLooper()))
        refreshDevices()
    }

    private fun refreshDevices()
    {
        connected.value = InputDevice.getDeviceIds().toList().mapNotNull { id ->
            val d = InputDevice.getDevice(id) ?: return@mapNotNull null
            if (!isController(d)) return@mapNotNull null
            "${d.name} (vendor ${d.vendorId}, product ${d.productId})"
        }
    }

    private fun isController(d: InputDevice?): Boolean
    {
        if (d == null || d.isVirtual) return false
        return d.supportsSource(InputDevice.SOURCE_GAMEPAD) || d.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }

    private fun fromController(e: InputEvent): Boolean
    {
        if (e.isFromSource(InputDevice.SOURCE_GAMEPAD) || e.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true
        // Built-in pads often send the D-pad as SOURCE_DPAD | SOURCE_KEYBOARD. Only treat it
        // as ours when the device is a controller, so a real keyboard's arrows stay keys.
        return e.isFromSource(InputDevice.SOURCE_DPAD) && isController(e.device)
    }

    fun onKeyEvent(e: KeyEvent): Boolean
    {
        val cfg = ControllerStore.current
        if (!fromController(e)) return false
        val code = e.keyCode
        if (code in SYSTEM_KEYS) return false

        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0)
        {
            lastKey.value = "${GamepadKeys.name(code)} — keycode $code (${KeyEvent.keyCodeToString(code)})"
        }

        val listener = capture.value
        if (listener != null)
        {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0)
            {
                capture.value = null
                listener(code)
            }
            return true
        }

        if (!cfg.enabled) return false
        realKeys += code

        val gamepadKey = KeyEvent.isGamepadButton(code) || isDpad(code)
        when (e.action)
        {
            KeyEvent.ACTION_DOWN ->
            {
                if (e.repeatCount > 0) return held.containsKey(code) || gamepadKey
                // Already held from this button's analog axis (pads that report a D-pad or
                // trigger both ways): one physical press, so no second action.
                if (held.containsKey(code)) return true
                val action = cfg.actionFor(code)
                    // Unbound gamepad buttons are still swallowed: otherwise Android's
                    // fallback turns e.g. B into BACK, which closes panels.
                    ?: return gamepadKey
                held[code] = action
                ActionExecutor.down(action)
                return true
            }
            KeyEvent.ACTION_UP ->
            {
                val action = held.remove(code) ?: return gamepadKey || cfg.actionFor(code) != null
                ActionExecutor.up(action)
                return true
            }
        }
        return gamepadKey
    }

    fun onMotionEvent(e: MotionEvent): Boolean
    {
        if (!e.isFromSource(InputDevice.SOURCE_JOYSTICK) || e.action != MotionEvent.ACTION_MOVE) return false
        val cfg = ControllerStore.current
        if (!cfg.enabled)
        {
            resetAxes()
            return false
        }
        val dev = e.device

        leftX = axis(e, dev, MotionEvent.AXIS_X)
        leftY = axis(e, dev, MotionEvent.AXIS_Y)

        val useRxRy = when (cfg.rightStickAxes)
        {
            RightStickAxes.Z_RZ -> false
            RightStickAxes.RX_RY -> true
            // Most Android pads put the right stick on Z/RZ. Some (older DualShock
            // mappings, a few handhelds) put it on RX/RY and use Z/RZ for the triggers;
            // those report no dedicated trigger axes.
            RightStickAxes.AUTO -> has(dev, e, MotionEvent.AXIS_RX) &&
                (!has(dev, e, MotionEvent.AXIS_Z) ||
                    (!has(dev, e, MotionEvent.AXIS_LTRIGGER) && !has(dev, e, MotionEvent.AXIS_BRAKE)))
        }
        if (useRxRy)
        {
            rightX = axis(e, dev, MotionEvent.AXIS_RX)
            rightY = axis(e, dev, MotionEvent.AXIS_RY)
        }
        else
        {
            rightX = axis(e, dev, MotionEvent.AXIS_Z)
            rightY = axis(e, dev, MotionEvent.AXIS_RZ)
        }

        leftTrigger = max(axis(e, dev, MotionEvent.AXIS_LTRIGGER), axis(e, dev, MotionEvent.AXIS_BRAKE))
        rightTrigger = max(axis(e, dev, MotionEvent.AXIS_RTRIGGER), axis(e, dev, MotionEvent.AXIS_GAS))

        if (triggerThreshold != cfg.triggerThreshold)
        {
            triggerThreshold = cfg.triggerThreshold
            val t = cfg.triggerThreshold / 100f
            if (!l2Latch.pressed) l2Latch = AxisLatch(t)
            if (!r2Latch.pressed) r2Latch = AxisLatch(t)
        }
        synthesize(GamepadKeys.BUTTON_L2, l2Latch.update(leftTrigger))
        synthesize(GamepadKeys.BUTTON_R2, r2Latch.update(rightTrigger))

        val hatX = axis(e, dev, MotionEvent.AXIS_HAT_X)
        val hatY = axis(e, dev, MotionEvent.AXIS_HAT_Y)
        synthesize(GamepadKeys.DPAD_LEFT, hatLatches.getValue(GamepadKeys.DPAD_LEFT).update(-hatX))
        synthesize(GamepadKeys.DPAD_RIGHT, hatLatches.getValue(GamepadKeys.DPAD_RIGHT).update(hatX))
        synthesize(GamepadKeys.DPAD_UP, hatLatches.getValue(GamepadKeys.DPAD_UP).update(-hatY))
        synthesize(GamepadKeys.DPAD_DOWN, hatLatches.getValue(GamepadKeys.DPAD_DOWN).update(hatY))

        lastAxes.value = "L %+.2f %+.2f  R %+.2f %+.2f  LT %.2f RT %.2f  hat %+.0f %+.0f%s".format(
            leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, hatX, hatY,
            if (useRxRy) "  (right=RX/RY)" else "  (right=Z/RZ)")
        return true
    }

    /** Press/release edge from an analog source, for controllers that don't send the key. */
    private fun synthesize(code: Int, edge: Int)
    {
        if (edge == 0 || code in realKeys) return
        if (edge > 0)
        {
            // Already held from the key event for the same press.
            if (held.containsKey(code)) return
            val listener = capture.value
            if (listener != null)
            {
                capture.value = null
                listener(code)
                return
            }
            lastKey.value = "${GamepadKeys.name(code)} — from analog axis"
            val action = ControllerStore.current.actionFor(code) ?: return
            held[code] = action
            ActionExecutor.down(action)
        }
        else
        {
            held.remove(code)?.let { ActionExecutor.up(it) }
        }
    }

    private fun has(dev: InputDevice?, e: MotionEvent, axis: Int): Boolean =
        dev?.getMotionRange(axis, e.source) != null

    /** Axis value with the device's own noise floor ("flat") zeroed out. */
    private fun axis(e: MotionEvent, dev: InputDevice?, axis: Int): Float
    {
        val range = dev?.getMotionRange(axis, e.source) ?: return 0f
        val v = e.getAxisValue(axis)
        return if (abs(v) > range.flat) v else 0f
    }

    private fun isDpad(code: Int) = code == GamepadKeys.DPAD_UP || code == GamepadKeys.DPAD_DOWN ||
        code == GamepadKeys.DPAD_LEFT || code == GamepadKeys.DPAD_RIGHT ||
        code == KeyEvent.KEYCODE_DPAD_CENTER

    private fun resetAxes()
    {
        leftX = 0f; leftY = 0f; rightX = 0f; rightY = 0f
        leftTrigger = 0f; rightTrigger = 0f
    }

    /** Release everything this class pressed. */
    fun resetAll()
    {
        resetAxes()
        for (action in held.values.toList()) ActionExecutor.up(action)
        held.clear()
        l2Latch = AxisLatch(triggerThreshold.coerceAtLeast(5) / 100f)
        r2Latch = AxisLatch(triggerThreshold.coerceAtLeast(5) / 100f)
        ActionExecutor.releaseAll()
    }
}
