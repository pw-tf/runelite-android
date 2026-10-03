package net.runelite.mp.konkr

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import java.awt.event.KeyEvent
import kotlin.math.abs
import net.runelite.mp.AwtPointer

/**
 * The analog-stick mouse pointer, plus the per-frame work for the other stick roles
 * (camera, scroll). [frame] is driven from the overlay's `withFrameNanos` loop on the main
 * thread, so it runs once per display refresh.
 *
 * Positions are AWT window pixels, the same space [AwtPointer] dispatches in.
 */
object StickCursor
{
    private val integrator = CursorIntegrator()
    private var initialized = false
    private var lastFrameNs = 0L
    private var lastActivityNs = 0L
    private var lastTouchSerial = -1
    private var lastWinW = 0
    private var lastWinH = 0

    /** Window position, observable by the overlay. */
    val posX = mutableFloatStateOf(0f)
    val posY = mutableFloatStateOf(0f)

    /** Whether the overlay should draw the pointer right now. */
    val visible = mutableStateOf(false)

    /** Cursor stick on/off ([Action.ToggleCursor]). */
    val enabled = mutableStateOf(true)

    val x: Int get() = integrator.x.toInt()
    val y: Int get() = integrator.y.toInt()

    private val scrollAcc = RateAccumulator()

    /** Camera arrow keys currently held by a camera-role stick. */
    private val cameraKeys = linkedMapOf(
        KeyEvent.VK_LEFT to AxisLatch(CAMERA_ON),
        KeyEvent.VK_RIGHT to AxisLatch(CAMERA_ON),
        KeyEvent.VK_UP to AxisLatch(CAMERA_ON),
        KeyEvent.VK_DOWN to AxisLatch(CAMERA_ON),
    )

    private const val CAMERA_ON = 0.45f
    private const val AUTO_HIDE_NS = 3_000_000_000L
    private const val MAX_SCROLL_PER_SECOND = 14f

    fun toggleEnabled()
    {
        enabled.value = !enabled.value
        visible.value = enabled.value
        lastActivityNs = System.nanoTime()
    }

    /** Put the pointer in the middle of the game view, roughly over the player. */
    fun recenter()
    {
        val win = AwtPointer.windowSize() ?: return
        val canvas = AwtPointer.canvasBounds()
        if (canvas != null && canvas.width > 0 && canvas.height > 0)
        {
            integrator.set(canvas.x + canvas.width / 2f, canvas.y + canvas.height / 2f, win.width, win.height)
        }
        else
        {
            integrator.set(win.width / 2f, win.height / 2f, win.width, win.height)
        }
        initialized = true
        publish()
        AwtPointer.move(x, y)
        onButtonActivity()
    }

    /** A mouse action fired from a button: show the pointer so the click has a visible spot. */
    fun onButtonActivity()
    {
        if (!initialized) recenter()
        lastActivityNs = System.nanoTime()
        if (enabled.value) visible.value = true
    }

    fun frame(nowNs: Long)
    {
        val dt = if (lastFrameNs == 0L) 0f else ((nowNs - lastFrameNs) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNs = nowNs

        val cfg = ControllerStore.current
        val win = AwtPointer.windowSize()
        if (win == null || !cfg.enabled)
        {
            updateCamera(0f, 0f)
            return
        }
        if (!initialized) recenter()
        if (win.width != lastWinW || win.height != lastWinH)
        {
            // The frame resizes after boot (splash → game) and with the sidebar; keep the
            // pointer at the same relative spot.
            if (lastWinW > 0 && lastWinH > 0)
            {
                integrator.set(
                    integrator.x * win.width / lastWinW,
                    integrator.y * win.height / lastWinH,
                    win.width, win.height,
                )
                publish()
            }
            lastWinW = win.width
            lastWinH = win.height
        }

        // Follow touches: a finger tap moves the real pointer, so the stick cursor resumes
        // from there instead of jumping back. Touch users don't need the arrow drawn.
        val serial = AwtPointer.touchSerial
        if (serial != lastTouchSerial)
        {
            if (lastTouchSerial != -1)
            {
                integrator.set(AwtPointer.touchX.toFloat(), AwtPointer.touchY.toFloat(), win.width, win.height)
                publish()
                visible.value = false
            }
            lastTouchSerial = serial
        }

        val dz = cfg.deadzone / 100f
        val exponent = cfg.cursorCurve / 10f

        var cursorVx = 0f
        var cursorVy = 0f
        var camX = 0f
        var camY = 0f
        var scroll = 0f
        for (stick in 0..1)
        {
            val role = if (stick == 0) cfg.leftStick else cfg.rightStick
            val rawX = if (stick == 0) GamepadInput.leftX else GamepadInput.rightX
            val rawY = if (stick == 0) GamepadInput.leftY else GamepadInput.rightY
            when (role)
            {
                StickRole.CURSOR ->
                {
                    val (sx, sy) = StickMath.shape(rawX, rawY, dz, exponent)
                    cursorVx += sx
                    cursorVy += sy
                }
                StickRole.CAMERA ->
                {
                    val (sx, sy) = StickMath.shape(rawX, rawY, dz, 1f)
                    if (abs(sx) > abs(camX)) camX = sx
                    if (abs(sy) > abs(camY)) camY = sy
                }
                StickRole.SCROLL ->
                {
                    val (_, sy) = StickMath.shape(rawX, rawY, dz, exponent)
                    if (abs(sy) > abs(scroll)) scroll = sy
                }
                StickRole.OFF -> {}
            }
        }

        if (enabled.value && (cursorVx != 0f || cursorVy != 0f))
        {
            // cursorSpeed is tenths of the window height per second at full deflection,
            // so the feel is the same whatever resolution the AWT frame runs at.
            var speed = cfg.cursorSpeed / 10f * win.height
            if (ActionExecutor.precisionHeld) speed *= cfg.precisionPercent / 100f
            if (integrator.step(cursorVx * speed, cursorVy * speed, dt, win.width, win.height))
            {
                publish()
                AwtPointer.move(x, y)
            }
            lastActivityNs = nowNs
            visible.value = true
        }

        updateCamera(
            if (cfg.cameraInvertX) -camX else camX,
            if (cfg.cameraInvertY) -camY else camY,
        )

        val notches = scrollAcc.step((if (cfg.scrollInvert) -scroll else scroll) * MAX_SCROLL_PER_SECOND, dt)
        if (notches != 0) AwtPointer.wheel(x, y, notches)

        ActionExecutor.tick(nowNs)

        if (visible.value && (!enabled.value || (cfg.cursorAutoHide && nowNs - lastActivityNs > AUTO_HIDE_NS)))
        {
            visible.value = false
        }
    }

    /** Hold or release the arrow keys from a camera-role stick (OSRS rotates on arrows). */
    private fun updateCamera(cx: Float, cy: Float)
    {
        for ((vk, latch) in cameraKeys)
        {
            val v = when (vk)
            {
                KeyEvent.VK_LEFT -> -cx
                KeyEvent.VK_RIGHT -> cx
                KeyEvent.VK_UP -> -cy
                else -> cy
            }
            when (latch.update(v))
            {
                1 -> ActionExecutor.keyDown(vk, KeyEvent.CHAR_UNDEFINED)
                -1 -> ActionExecutor.keyUp(vk, KeyEvent.CHAR_UNDEFINED)
            }
        }
    }

    private fun publish()
    {
        posX.floatValue = integrator.x
        posY.floatValue = integrator.y
    }
}
