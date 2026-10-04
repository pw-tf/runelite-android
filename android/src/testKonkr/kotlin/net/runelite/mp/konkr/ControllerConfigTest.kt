package net.runelite.mp.konkr

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerConfigTest
{
    @Test
    fun roundTripsEveryField()
    {
        val cfg = ControllerConfig(
            enabled = false,
            leftStick = StickRole.SCROLL,
            rightStick = StickRole.CAMERA,
            rightStickAxes = RightStickAxes.RX_RY,
            deadzone = 22,
            cursorSpeed = 17,
            cursorCurve = 31,
            precisionPercent = 50,
            cameraInvertX = true,
            cameraInvertY = true,
            scrollInvert = true,
            triggerThreshold = 70,
            cursorAutoHide = false,
            bindings = mapOf(GamepadKeys.BUTTON_A to Action.MouseRight, 300 to Action.key(KeyEvent.VK_F5)),
            perfProfile = PerfProfile.PERFORMANCE,
            sustainedPerformance = true,
            highRefreshRate = false,
            showFps = true,
            sidebarHidden = true,
        )
        assertEquals(cfg, ControllerConfig.fromMap(cfg.toMap()))
    }

    @Test
    fun emptyPreferencesGiveDefaults()
    {
        assertEquals(ControllerConfig(), ControllerConfig.fromMap(emptyMap<String, Any>()))
    }

    @Test
    fun badValuesFallBackOrClamp()
    {
        val cfg = ControllerConfig.fromMap(mapOf(
            "deadzone" to 500,
            "leftStick" to "NOT_A_ROLE",
            "cursorSpeed" to "fast",
            "bind.96" to "no_such_action",
            "bind.97" to "mouse_left",
        ))
        assertEquals(60, cfg.deadzone)
        assertEquals(StickRole.CAMERA, cfg.leftStick)
        assertEquals(ControllerConfig().cursorSpeed, cfg.cursorSpeed)
        assertEquals(mapOf(GamepadKeys.BUTTON_B to Action.MouseLeft), cfg.bindings)
    }

    @Test
    fun unboundButtonsStayUnbound()
    {
        val cfg = ControllerConfig(bindings = mapOf(GamepadKeys.BUTTON_A to Action.None))
        val back = ControllerConfig.fromMap(cfg.toMap())
        assertEquals(null, back.actionFor(GamepadKeys.BUTTON_A))
        assertEquals(null, back.actionFor(GamepadKeys.BUTTON_B))
    }

    @Test
    fun everyActionIdResolvesToItself()
    {
        for (a in Action.all) assertSame(a, Action.fromId(a.id))
        assertEquals(Action.all.size, Action.all.map { it.id }.toSet().size)
    }

    @Test
    fun defaultLayoutCoversTheStandardButtons()
    {
        val d = ControllerConfig.DEFAULT_BINDINGS
        for (code in listOf(
            GamepadKeys.BUTTON_A, GamepadKeys.BUTTON_B, GamepadKeys.BUTTON_X, GamepadKeys.BUTTON_Y,
            GamepadKeys.BUTTON_L1, GamepadKeys.BUTTON_R1, GamepadKeys.BUTTON_L2, GamepadKeys.BUTTON_R2,
            GamepadKeys.BUTTON_START, GamepadKeys.BUTTON_SELECT,
            GamepadKeys.DPAD_UP, GamepadKeys.DPAD_DOWN, GamepadKeys.DPAD_LEFT, GamepadKeys.DPAD_RIGHT,
        ))
        {
            assertNotNull("no default for ${GamepadKeys.name(code)}", d[code])
        }
        assertEquals(Action.MouseLeft, d[GamepadKeys.BUTTON_A])
        assertEquals(Action.MouseRight, d[GamepadKeys.BUTTON_B])
        assertTrue(d.values.any { it == Action.OpenSettings })
    }
}
