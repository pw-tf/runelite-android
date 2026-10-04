package net.runelite.mp.flavor

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

/**
 * Flavor extension points called from the shared source set. Every flavor defines an
 * object with this exact name and shape; this is the `standard` flavor's, and it does
 * nothing, so the regular APK behaves as if the hooks weren't there.
 *
 * The `konkr` flavor's copy lives in src/konkr/kotlin.
 */
object FlavorHooks
{
    /** Panels to add to the nav strip and the panel registry. */
    val panels: List<FlavorPanel> = emptyList()

    /** Whether the user can hide the whole sidebar (icon strip + panel). */
    val sidebarCollapsible: Boolean = false

    /** The sidebar was hidden or shown; lets the flavor persist the choice. */
    fun onSidebarCollapsedChanged(collapsed: Boolean) {}

    fun onCreate(activity: ComponentActivity) {}

    /** Offered every key event before the game's own key handling. True consumes it. */
    fun dispatchKeyEvent(event: KeyEvent): Boolean = false

    /** Offered every generic motion event (joysticks, mice, trackpads). True consumes it. */
    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = false

    /** Drawn over the game viewport, under the boot splash. Fills the viewport. */
    @Composable
    fun GameOverlay() {}

    /** The GLES SurfaceView's surface was created or recreated. */
    fun onGlSurfaceCreated(surface: Surface) {}

    /** Called on the GPU plugin's render thread once its EGL context is current. */
    @JvmStatic
    fun onRenderThreadStart() {}

    /** Called on the render thread after each GPU frame with its CPU-side work time. */
    @JvmStatic
    fun onFrameRendered(workNanos: Long) {}
}
