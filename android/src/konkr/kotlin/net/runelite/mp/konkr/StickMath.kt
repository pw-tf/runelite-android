package net.runelite.mp.konkr

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Framework-free stick math so it can be unit tested on the JVM.
 */
object StickMath
{
    /**
     * Radial deadzone followed by a power response curve. Input axes are -1..1; the result
     * keeps the stick's direction and maps magnitude [deadzone, 1] onto [0, 1]^exponent.
     */
    fun shape(x: Float, y: Float, deadzone: Float, exponent: Float): Pair<Float, Float>
    {
        val mag = sqrt(x * x + y * y)
        if (mag <= deadzone || mag == 0f) return 0f to 0f
        val clamped = mag.coerceAtMost(1f)
        val t = ((clamped - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        val out = t.pow(exponent)
        val k = out / mag
        return x * k to y * k
    }
}

/**
 * Integrates a stick velocity into a sub-pixel cursor position clamped to the window.
 */
class CursorIntegrator
{
    var x = 0f
        private set
    var y = 0f
        private set

    fun set(nx: Float, ny: Float, width: Int, height: Int)
    {
        x = nx.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        y = ny.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    }

    /**
     * Advance by velocity (vx, vy) in px/s over [dtSeconds]. Returns true when the integer
     * pixel position changed, i.e. when a mouse-move is worth sending.
     */
    fun step(vx: Float, vy: Float, dtSeconds: Float, width: Int, height: Int): Boolean
    {
        val oldX = x.toInt()
        val oldY = y.toInt()
        set(x + vx * dtSeconds, y + vy * dtSeconds, width, height)
        return x.toInt() != oldX || y.toInt() != oldY
    }
}

/**
 * Turns an analog value into press/release edges with hysteresis, so a value hovering
 * around the threshold doesn't chatter.
 */
class AxisLatch(private val onAt: Float, private val offAt: Float = onAt * 0.7f)
{
    var pressed = false
        private set

    /** Returns +1 on a press edge, -1 on a release edge, 0 otherwise. */
    fun update(value: Float): Int
    {
        if (!pressed && value >= onAt)
        {
            pressed = true
            return 1
        }
        if (pressed && value < offAt)
        {
            pressed = false
            return -1
        }
        return 0
    }
}
