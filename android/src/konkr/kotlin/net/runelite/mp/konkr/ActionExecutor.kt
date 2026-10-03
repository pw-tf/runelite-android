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
 * One input, one action: a button press produces exactly one game input (one click, one
 * key press, one wheel notch), and holding it only keeps that input held. Nothing here
 * repeats, queues, times or combines inputs on the player's behalf. Keep it that way;
 * Jagex's third-party client rules require it.
 *
 * Main thread only.
 */
object ActionExecutor
{
    /** Held AWT keys with how many sources hold them (a button and the camera stick can
     *  both hold the left arrow). Released when the count reaches zero. */
    private val heldKeys = HashMap<Int, Int>()

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
            Action.ScrollUp -> scroll(-1)
            Action.ScrollDown -> scroll(1)
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

    /** One press, one wheel notch. Holding the button does not repeat. */
    private fun scroll(dir: Int)
    {
        StickCursor.onButtonActivity()
        AwtPointer.wheel(StickCursor.x, StickCursor.y, dir)
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

    /** Release every key and mouse button, e.g. after a controller disconnects. */
    fun releaseAll()
    {
        for (vk in heldKeys.keys.toList())
        {
            heldKeys[vk] = 1
            keyUp(vk, KeyEvent.CHAR_UNDEFINED)
        }
        precisionHeld = false
        AwtPointer.releaseAll(StickCursor.x, StickCursor.y)
    }
}
