package net.runelite.mp.konkr

import java.awt.event.KeyEvent

/**
 * Android gamepad keycodes, spelled out so this file (and its unit tests) don't need the
 * Android framework on the classpath. Values are android.view.KeyEvent.KEYCODE_*.
 */
object GamepadKeys
{
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_C = 98
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_Z = 101
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
    const val BUTTON_MODE = 110

    private val names = mapOf(
        DPAD_UP to "D-pad up",
        DPAD_DOWN to "D-pad down",
        DPAD_LEFT to "D-pad left",
        DPAD_RIGHT to "D-pad right",
        BUTTON_A to "A",
        BUTTON_B to "B",
        BUTTON_C to "C",
        BUTTON_X to "X",
        BUTTON_Y to "Y",
        BUTTON_Z to "Z",
        BUTTON_L1 to "L1",
        BUTTON_R1 to "R1",
        BUTTON_L2 to "L2",
        BUTTON_R2 to "R2",
        BUTTON_THUMBL to "L3 (left stick click)",
        BUTTON_THUMBR to "R3 (right stick click)",
        BUTTON_START to "Start",
        BUTTON_SELECT to "Select",
        BUTTON_MODE to "Mode / Home",
    )

    /** Display order for the bindings list: the standard controls first. */
    val standard: List<Int> = names.keys.toList()

    fun name(keyCode: Int): String = names[keyCode] ?: "Key $keyCode"
}

/**
 * Something a controller button can do. [id] is the persisted form and must stay stable.
 */
sealed class Action(val id: String, val label: String)
{
    object None : Action("none", "Nothing")
    object MouseLeft : Action("mouse_left", "Left click")
    object MouseRight : Action("mouse_right", "Right click")
    object MouseMiddle : Action("mouse_middle", "Middle click (camera drag)")
    object ScrollUp : Action("scroll_up", "Scroll up / zoom in")
    object ScrollDown : Action("scroll_down", "Scroll down / zoom out")
    object HoldShift : Action("hold_shift", "Hold Shift")
    object HoldCtrl : Action("hold_ctrl", "Hold Ctrl")
    object HoldAlt : Action("hold_alt", "Hold Alt")
    object PrecisionCursor : Action("precision_cursor", "Slow cursor (hold)")
    object RecenterCursor : Action("recenter_cursor", "Center cursor")
    object ToggleCursor : Action("toggle_cursor", "Stick cursor on/off")
    object ToggleKeyboard : Action("toggle_keyboard", "Show/hide keyboard")
    object OpenSettings : Action("open_settings", "Open controller settings")
    object ToggleSidebar : Action("toggle_sidebar", "Show/hide sidebar")

    /** A keyboard key. [char] is what KEY_TYPED carries, or CHAR_UNDEFINED for none. */
    class Key(val vk: Int, val char: Char, name: String) : Action("key:$vk", "Key: $name")

    override fun toString(): String = id

    // Lazy: the subclass objects extend Action, so touching one runs Action's static
    // initializer first. Eager lists here would capture them while still null.
    companion object
    {
        val keys: List<Key> by lazy {
            buildList {
                add(Key(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED, "Esc"))
                add(Key(KeyEvent.VK_SPACE, ' ', "Space"))
                add(Key(KeyEvent.VK_ENTER, '\n', "Enter"))
                add(Key(KeyEvent.VK_TAB, '\t', "Tab"))
                add(Key(KeyEvent.VK_BACK_SPACE, '\b', "Backspace"))
                for (d in 1..9) add(Key(KeyEvent.VK_0 + d, '0' + d, d.toString()))
                add(Key(KeyEvent.VK_0, '0', "0"))
                for (f in 1..12) add(Key(KeyEvent.VK_F1 + f - 1, KeyEvent.CHAR_UNDEFINED, "F$f"))
                add(Key(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED, "Up arrow"))
                add(Key(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED, "Down arrow"))
                add(Key(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED, "Left arrow"))
                add(Key(KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED, "Right arrow"))
                add(Key(KeyEvent.VK_PAGE_UP, KeyEvent.CHAR_UNDEFINED, "Page up"))
                add(Key(KeyEvent.VK_PAGE_DOWN, KeyEvent.CHAR_UNDEFINED, "Page down"))
            }
        }

        /** Every action in the order the settings picker lists them. */
        val all: List<Action> by lazy {
            listOf(
                None, MouseLeft, MouseRight, MouseMiddle, ScrollUp, ScrollDown,
                HoldShift, HoldCtrl, HoldAlt,
                PrecisionCursor, RecenterCursor, ToggleCursor, ToggleKeyboard, OpenSettings, ToggleSidebar,
            ) + keys
        }

        private val byId: Map<String, Action> by lazy { all.associateBy { it.id } }

        fun fromId(id: String): Action? = byId[id]

        fun key(vk: Int): Key = keys.first { it.vk == vk }
    }
}

enum class StickRole(val label: String)
{
    CURSOR("Cursor"),
    CAMERA("Camera"),
    SCROLL("Scroll"),
    OFF("Off"),
}

/** Which axis pair the right stick reports on. Controllers disagree; AUTO guesses. */
enum class RightStickAxes(val label: String)
{
    AUTO("Auto"),
    Z_RZ("Z / RZ"),
    RX_RY("RX / RY"),
}

enum class PerfProfile(val label: String)
{
    BATTERY("Battery"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance"),
}

/**
 * Controller + performance settings for the konkr build. Immutable; [ControllerStore]
 * holds the current instance and persists it.
 *
 * Percent-style fields are ints so they map straight onto the settings sliders.
 */
data class ControllerConfig(
    val enabled: Boolean = true,
    val leftStick: StickRole = StickRole.CAMERA,
    val rightStick: StickRole = StickRole.CURSOR,
    val rightStickAxes: RightStickAxes = RightStickAxes.AUTO,
    /** Radial deadzone, percent of full deflection. */
    val deadzone: Int = 15,
    /** Cursor speed at full deflection, in tenths of a window height per second. */
    val cursorSpeed: Int = 10,
    /** Response curve exponent ×10: 10 is linear, higher gives finer control near center. */
    val cursorCurve: Int = 20,
    /** Speed while the slow-cursor action is held, percent of normal. */
    val precisionPercent: Int = 35,
    val cameraInvertX: Boolean = false,
    val cameraInvertY: Boolean = false,
    val scrollInvert: Boolean = false,
    /** Analog trigger travel, percent, that counts as a press. */
    val triggerThreshold: Int = 50,
    /** Fade the cursor out after a few idle seconds. */
    val cursorAutoHide: Boolean = true,
    val bindings: Map<Int, Action> = DEFAULT_BINDINGS,
    val perfProfile: PerfProfile = PerfProfile.BALANCED,
    /** Window.setSustainedPerformanceMode: steadier clocks, lower peaks. */
    val sustainedPerformance: Boolean = false,
    /** Ask the display for its highest refresh rate. */
    val highRefreshRate: Boolean = true,
    /** Small FPS / frame-time readout in the corner of the game. */
    val showFps: Boolean = false,
    /** Sidebar (icon strip + panel) hidden for a full-width game. */
    val sidebarHidden: Boolean = false,
)
{
    fun actionFor(keyCode: Int): Action? = bindings[keyCode]?.takeIf { it != Action.None }

    /** Flatten to SharedPreferences-friendly primitives. */
    fun toMap(): Map<String, Any> = buildMap {
        put("version", VERSION)
        put("enabled", enabled)
        put("leftStick", leftStick.name)
        put("rightStick", rightStick.name)
        put("rightStickAxes", rightStickAxes.name)
        put("deadzone", deadzone)
        put("cursorSpeed", cursorSpeed)
        put("cursorCurve", cursorCurve)
        put("precisionPercent", precisionPercent)
        put("cameraInvertX", cameraInvertX)
        put("cameraInvertY", cameraInvertY)
        put("scrollInvert", scrollInvert)
        put("triggerThreshold", triggerThreshold)
        put("cursorAutoHide", cursorAutoHide)
        put("perfProfile", perfProfile.name)
        put("sustainedPerformance", sustainedPerformance)
        put("highRefreshRate", highRefreshRate)
        put("showFps", showFps)
        put("sidebarHidden", sidebarHidden)
        for ((code, action) in bindings) put("$BIND_PREFIX$code", action.id)
    }

    companion object
    {
        const val VERSION = 1
        private const val BIND_PREFIX = "bind."

        val DEFAULT_BINDINGS: Map<Int, Action> = mapOf(
            GamepadKeys.BUTTON_A to Action.MouseLeft,
            GamepadKeys.BUTTON_B to Action.MouseRight,
            GamepadKeys.BUTTON_X to Action.HoldShift,
            GamepadKeys.BUTTON_Y to Action.key(KeyEvent.VK_ESCAPE),
            GamepadKeys.BUTTON_L1 to Action.ScrollUp,
            GamepadKeys.BUTTON_R1 to Action.ScrollDown,
            GamepadKeys.BUTTON_L2 to Action.PrecisionCursor,
            GamepadKeys.BUTTON_R2 to Action.key(KeyEvent.VK_SPACE),
            GamepadKeys.DPAD_UP to Action.key(KeyEvent.VK_1),
            GamepadKeys.DPAD_RIGHT to Action.key(KeyEvent.VK_2),
            GamepadKeys.DPAD_DOWN to Action.key(KeyEvent.VK_3),
            GamepadKeys.DPAD_LEFT to Action.key(KeyEvent.VK_4),
            GamepadKeys.BUTTON_THUMBL to Action.ToggleCursor,
            GamepadKeys.BUTTON_THUMBR to Action.RecenterCursor,
            GamepadKeys.BUTTON_START to Action.key(KeyEvent.VK_ENTER),
            GamepadKeys.BUTTON_SELECT to Action.ToggleKeyboard,
            GamepadKeys.BUTTON_MODE to Action.OpenSettings,
        )

        /**
         * Rebuild from [toMap] output. Missing or malformed values fall back to defaults,
         * so older or hand-edited preferences never fail to load. A map with no binding
         * entries at all (first run) gets the default layout.
         */
        fun fromMap(map: Map<String, *>): ControllerConfig
        {
            val d = ControllerConfig()
            fun int(key: String, def: Int, range: IntRange) =
                ((map[key] as? Number)?.toInt() ?: def).coerceIn(range)
            fun bool(key: String, def: Boolean) = map[key] as? Boolean ?: def
            fun role(key: String, def: StickRole) =
                (map[key] as? String)?.let { n -> StickRole.entries.firstOrNull { it.name == n } } ?: def

            val bindings = LinkedHashMap<Int, Action>()
            var sawBinding = false
            for ((k, v) in map)
            {
                if (!k.startsWith(BIND_PREFIX)) continue
                sawBinding = true
                val code = k.removePrefix(BIND_PREFIX).toIntOrNull() ?: continue
                val action = (v as? String)?.let { Action.fromId(it) } ?: continue
                bindings[code] = action
            }

            return ControllerConfig(
                enabled = bool("enabled", d.enabled),
                leftStick = role("leftStick", d.leftStick),
                rightStick = role("rightStick", d.rightStick),
                rightStickAxes = (map["rightStickAxes"] as? String)
                    ?.let { n -> RightStickAxes.entries.firstOrNull { it.name == n } } ?: d.rightStickAxes,
                deadzone = int("deadzone", d.deadzone, 0..60),
                cursorSpeed = int("cursorSpeed", d.cursorSpeed, 1..40),
                cursorCurve = int("cursorCurve", d.cursorCurve, 10..40),
                precisionPercent = int("precisionPercent", d.precisionPercent, 5..100),
                cameraInvertX = bool("cameraInvertX", d.cameraInvertX),
                cameraInvertY = bool("cameraInvertY", d.cameraInvertY),
                scrollInvert = bool("scrollInvert", d.scrollInvert),
                triggerThreshold = int("triggerThreshold", d.triggerThreshold, 5..95),
                cursorAutoHide = bool("cursorAutoHide", d.cursorAutoHide),
                bindings = if (sawBinding) bindings else DEFAULT_BINDINGS,
                perfProfile = (map["perfProfile"] as? String)
                    ?.let { n -> PerfProfile.entries.firstOrNull { it.name == n } } ?: d.perfProfile,
                sustainedPerformance = bool("sustainedPerformance", d.sustainedPerformance),
                highRefreshRate = bool("highRefreshRate", d.highRefreshRate),
                showFps = bool("showFps", d.showFps),
                sidebarHidden = bool("sidebarHidden", d.sidebarHidden),
            )
        }
    }
}
