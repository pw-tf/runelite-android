package net.runelite.mp.konkr

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import net.runelite.mp.AwtPointer
import net.runelite.mp.ui.bridge.KeyDispatch
import net.runelite.mp.ui.bridge.ModifierState

/**
 * Performs [Action]s against the game. Mouse actions land at the [StickCursor] position;
 * keys go through [KeyDispatch.dispatchAwtEvent] as separate press and release, so holding
 * a button holds the key.
 *
 * Main thread only.
 */
object ActionExecutor
{
    /** Held AWT keys with how many sources hold them (a button and the camera stick can
     *  both hold the left arrow). Released when the count reaches zero. */
    private val heldKeys = HashMap<Int, Int>()

    /** Keys a real keyboard would auto-repeat while held. The client tracks them as held
     *  between press and release, but re-sending the press (like desktop key repeat, and
     *  like ModifierState's heartbeat) survives any reset of that state, e.g. focus loss. */
    private val REPEATING = setOf(
        KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
        KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT,
    )
    private const val REPEAT_NS = 100_000_000L
    private var lastRepeatNs = 0L

    /** Scroll actions currently held: +1 per held scroll-down, -1 per scroll-up. */
    private var scrollHeld = 0
    private var scrollHeldSinceNs = 0L
    private var lastScrollRepeatNs = 0L
    private const val SCROLL_REPEAT_DELAY_NS = 350_000_000L
    private const val SCROLL_REPEAT_NS = 90_000_000L

    var precisionHeld = false
        private set

    fun down(action: Action)
    {
        when (action)
        {
            Action.None -> {}
            Action.MouseLeft -> mouseDown(MouseEvent.BUTTON1)
            Action.MouseRight -> mouseDown(MouseEvent.BUTTON3)
            Action.MouseMiddle -> mouseDown(MouseEvent.BUTTON2)
            Action.ScrollUp -> scrollDown(-1)
            Action.ScrollDown -> scrollDown(1)
            Action.HoldShift -> keyDown(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED)
            Action.HoldCtrl -> keyDown(KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
            Action.HoldAlt -> keyDown(KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED)
            Action.PrecisionCursor -> precisionHeld = true
            Action.RecenterCursor -> StickCursor.recenter()
            Action.ToggleCursor -> StickCursor.toggleEnabled()
            Action.ToggleKeyboard -> net.runelite.mp.ui.bridge.SoftKeyboardController.toggle()
            Action.OpenSettings -> net.runelite.mp.ui.WindowImpl.showPanel(KonkrSettings.KEY)
            is Action.Key -> keyDown(action.vk, action.char)
        }
    }

    fun up(action: Action)
    {
        when (action)
        {
            Action.MouseLeft -> mouseUp(MouseEvent.BUTTON1)
            Action.MouseRight -> mouseUp(MouseEvent.BUTTON3)
            Action.MouseMiddle -> mouseUp(MouseEvent.BUTTON2)
            Action.ScrollUp -> scrollUp(-1)
            Action.ScrollDown -> scrollUp(1)
            Action.HoldShift -> keyUp(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED)
            Action.HoldCtrl -> keyUp(KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
            Action.HoldAlt -> keyUp(KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED)
            Action.PrecisionCursor -> precisionHeld = false
            is Action.Key -> keyUp(action.vk, action.char)
            else -> {}
        }
    }

    // ---- mouse ----------------------------------------------------------------------

    private fun mouseDown(button: Int)
    {
        StickCursor.onButtonActivity()
        AwtPointer.press(StickCursor.x, StickCursor.y, button)
    }

    private fun mouseUp(button: Int)
    {
        AwtPointer.release(StickCursor.x, StickCursor.y, button)
    }

    private fun scrollDown(dir: Int)
    {
        StickCursor.onButtonActivity()
        AwtPointer.wheel(StickCursor.x, StickCursor.y, dir)
        scrollHeld += dir
        scrollHeldSinceNs = System.nanoTime()
        lastScrollRepeatNs = scrollHeldSinceNs
    }

    private fun scrollUp(dir: Int)
    {
        scrollHeld -= dir
    }

    // ---- keys -----------------------------------------------------------------------

    fun keyDown(vk: Int, char: Char)
    {
        val count = heldKeys[vk] ?: 0
        heldKeys[vk] = count + 1
        if (count > 0) return
        fireKey(KeyEvent.KEY_PRESSED, vk, char)
        if (char != KeyEvent.CHAR_UNDEFINED)
        {
            fireKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, char)
        }
    }

    fun keyUp(vk: Int, char: Char)
    {
        val count = heldKeys[vk] ?: return
        if (count > 1)
        {
            heldKeys[vk] = count - 1
            return
        }
        heldKeys.remove(vk)
        // The sticky Shift/Alt chips own their key while engaged; don't release it from
        // under them.
        if (vk == KeyEvent.VK_SHIFT && ModifierState.shiftActive.value) return
        if (vk == KeyEvent.VK_ALT && ModifierState.altActive.value) return
        fireKey(KeyEvent.KEY_RELEASED, vk, char)
    }

    private fun modifierMask(): Int
    {
        var m = ModifierState.modifierMask()
        if (heldKeys.containsKey(KeyEvent.VK_SHIFT)) m = m or InputEvent.SHIFT_DOWN_MASK
        if (heldKeys.containsKey(KeyEvent.VK_CONTROL)) m = m or InputEvent.CTRL_DOWN_MASK
        if (heldKeys.containsKey(KeyEvent.VK_ALT)) m = m or InputEvent.ALT_DOWN_MASK
        return m
    }

    private fun fireKey(id: Int, vk: Int, char: Char)
    {
        val source = java.awt.Canvas.latest() ?: java.awt.Window.primaryFrame() ?: return
        KeyDispatch.dispatchAwtEvent(KeyEvent(source, id, System.currentTimeMillis(), modifierMask(), vk, char))
    }

    // ---- per frame ------------------------------------------------------------------

    /** Key repeat and held-scroll repeat. Called once per display frame. */
    fun tick(nowNs: Long)
    {
        if (heldKeys.isNotEmpty() && nowNs - lastRepeatNs >= REPEAT_NS)
        {
            lastRepeatNs = nowNs
            for (vk in heldKeys.keys)
            {
                if (vk in REPEATING) fireKey(KeyEvent.KEY_PRESSED, vk, KeyEvent.CHAR_UNDEFINED)
            }
        }
        if (scrollHeld != 0 && nowNs - scrollHeldSinceNs >= SCROLL_REPEAT_DELAY_NS &&
            nowNs - lastScrollRepeatNs >= SCROLL_REPEAT_NS)
        {
            lastScrollRepeatNs = nowNs
            AwtPointer.wheel(StickCursor.x, StickCursor.y, scrollHeld.coerceIn(-1, 1))
        }
    }

    /** Release every key and mouse button, e.g. after a controller disconnects. */
    fun releaseAll()
    {
        for (vk in heldKeys.keys.toList())
        {
            heldKeys[vk] = 1
            keyUp(vk, KeyEvent.CHAR_UNDEFINED)
        }
        scrollHeld = 0
        precisionHeld = false
        AwtPointer.releaseAll(StickCursor.x, StickCursor.y)
    }
}
