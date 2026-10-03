package net.runelite.mp.konkr

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickMathTest
{
    private val eps = 1e-4f

    @Test
    fun insideDeadzoneIsZero()
    {
        assertEquals(0f to 0f, StickMath.shape(0.1f, 0.05f, 0.15f, 2f))
        assertEquals(0f to 0f, StickMath.shape(0f, 0f, 0f, 1f))
    }

    @Test
    fun fullDeflectionIsUnitLengthAndKeepsDirection()
    {
        val (x, y) = StickMath.shape(0.6f, -0.8f, 0.15f, 2f)
        assertEquals(1f, sqrt(x * x + y * y), eps)
        assertEquals(0.6f, x, eps)
        assertEquals(-0.8f, y, eps)
    }

    @Test
    fun overRangeInputIsClampedToUnit()
    {
        val (x, _) = StickMath.shape(1.3f, 0f, 0.1f, 1f)
        assertEquals(1f, x, eps)
    }

    @Test
    fun curveIsRescaledFromDeadzoneEdge()
    {
        // Halfway between deadzone (0.2) and full: t = 0.5, squared = 0.25.
        val (x, _) = StickMath.shape(0.6f, 0f, 0.2f, 2f)
        assertEquals(0.25f, x, eps)
        // Linear curve.
        val (lx, _) = StickMath.shape(0.6f, 0f, 0.2f, 1f)
        assertEquals(0.5f, lx, eps)
    }

    @Test
    fun integratorClampsAndReportsPixelChanges()
    {
        val c = CursorIntegrator()
        c.set(10f, 10f, 100, 50)
        assertFalse(c.step(5f, 0f, 0.1f, 100, 50)) // +0.5 px, same pixel
        assertTrue(c.step(5f, 0f, 0.1f, 100, 50))  // crosses into x = 11
        assertEquals(11, c.x.toInt())
        c.step(-10_000f, 10_000f, 1f, 100, 50)
        assertEquals(0f, c.x, eps)
        assertEquals(49f, c.y, eps)
    }

    @Test
    fun latchHasHysteresis()
    {
        val l = AxisLatch(0.5f, 0.3f)
        assertEquals(0, l.update(0.4f))
        assertEquals(1, l.update(0.55f))
        assertEquals(0, l.update(0.4f))   // still held above release point
        assertEquals(-1, l.update(0.2f))
        assertEquals(0, l.update(0.2f))
    }
}
