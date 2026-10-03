package net.runelite.mp.flavor

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import net.runelite.mp.konkr.ControllerStore
import net.runelite.mp.konkr.GamepadInput
import net.runelite.mp.konkr.KonkrGameOverlay
import net.runelite.mp.konkr.KonkrSettings
import net.runelite.mp.konkr.PerformanceManager

/**
 * Flavor extension points for the Konkr Pocket Fit Elite build: gamepad input, the
 * analog-stick cursor, the controller settings panel and device performance tuning.
 * Same shape as the standard flavor's no-op copy.
 */
object FlavorHooks
{
    val panels: List<FlavorPanel> = listOf(
        FlavorPanel(KonkrSettings.KEY, "🎮") { KonkrSettings.Panel() },
    )

    fun onCreate(activity: ComponentActivity)
    {
        ControllerStore.init(activity)
        GamepadInput.init(activity)
        PerformanceManager.onCreate(activity)
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean = GamepadInput.onKeyEvent(event)

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = GamepadInput.onMotionEvent(event)

    @Composable
    fun GameOverlay()
    {
        KonkrGameOverlay()
    }

    fun onGlSurfaceCreated(surface: Surface) = PerformanceManager.onGlSurfaceCreated(surface)

    @JvmStatic
    fun onRenderThreadStart() = PerformanceManager.onRenderThreadStart()

    @JvmStatic
    fun onFrameRendered(workNanos: Long) = PerformanceManager.onFrameRendered(workNanos)
}
