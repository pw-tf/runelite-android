package net.runelite.mp.konkr

import android.app.Activity
import android.app.GameManager
import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt

/**
 * Device performance tuning for the konkr build:
 *
 *  - Display: requests the panel's highest refresh rate (or 60 Hz on the battery profile),
 *    and tells SurfaceFlinger the GL surface's intended frame rate.
 *  - CPU: optional sustained performance mode; raises the render thread's priority and
 *    opens a PerformanceHintManager session so the scheduler sizes clocks to the frame
 *    budget instead of guessing.
 *  - Game: sets the GPU plugin's FPS target from the profile, and on first run seeds its
 *    quality settings for handheld-class hardware.
 *
 * Android 13+ Game Mode (the system game dashboard) overrides the profile when set.
 */
object PerformanceManager
{
    private const val TAG = "KonkrPerf"
    private const val PREFS = "konkr_perf"
    private const val KEY_SEEDED = "gpuSeeded"
    private const val GPU_GROUP = "gpugles"

    private var appContext: Context? = null
    private var activity: Activity? = null

    /** Highest refresh rate the display offers at its current resolution. */
    @Volatile var maxRefreshHz = 60f
        private set

    /** Refresh rate we asked the display for. */
    val requestedRefresh = mutableStateOf("—")

    /** System Game Mode as reported by GameManager, for the settings panel. */
    val systemGameMode = mutableStateOf("unsupported")

    /** "FPS 60 · 7.9 ms" readout for the overlay and settings panel. */
    val fpsText = mutableStateOf("FPS —")

    private var surface: Surface? = null
    private var hintSession: Any? = null // PerformanceHintManager.Session, API 31+
    private var fpsPushedToClient = 0

    // Render-thread frame statistics, published twice a second.
    private var statFrames = 0
    private var statWorkNs = 0L
    private var statSinceNs = 0L

    fun onCreate(activity: Activity)
    {
        this.activity = activity
        appContext = activity.applicationContext
        apply()
    }

    /** Profile the device should run at right now, after any system Game Mode override. */
    fun effectiveProfile(): PerfProfile
    {
        val cfg = ControllerStore.current
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            val gm = appContext?.getSystemService(GameManager::class.java)
            when (gm?.gameMode)
            {
                GameManager.GAME_MODE_PERFORMANCE -> return PerfProfile.PERFORMANCE
                GameManager.GAME_MODE_BATTERY -> return PerfProfile.BATTERY
            }
        }
        return cfg.perfProfile
    }

    /** Frame rate the game is asked to render at for the current profile. */
    fun targetFps(): Int = when (effectiveProfile())
    {
        PerfProfile.BATTERY -> 30
        PerfProfile.BALANCED -> 60
        PerfProfile.PERFORMANCE -> maxRefreshHz.roundToInt().coerceAtLeast(60)
    }

    /** Re-apply everything after a settings change. Main thread. */
    fun apply()
    {
        val act = activity ?: return
        val cfg = ControllerStore.current
        updateGameModeLabel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        {
            val pm = act.getSystemService(PowerManager::class.java)
            if (pm?.isSustainedPerformanceModeSupported == true)
            {
                act.window.setSustainedPerformanceMode(cfg.sustainedPerformance)
            }
        }

        applyDisplayMode(act, cfg)
        applySurfaceFrameRate()
        updateHintTarget()
        pushFpsTarget()
    }

    private fun updateGameModeLabel()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val gm = appContext?.getSystemService(GameManager::class.java) ?: return
        systemGameMode.value = when (gm.gameMode)
        {
            GameManager.GAME_MODE_PERFORMANCE -> "Performance"
            GameManager.GAME_MODE_BATTERY -> "Battery"
            GameManager.GAME_MODE_STANDARD -> "Standard"
            else -> "Not set"
        }
    }

    @Suppress("DEPRECATION")
    private fun applyDisplayMode(act: Activity, cfg: ControllerConfig)
    {
        try
        {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) act.display else act.windowManager.defaultDisplay
            display ?: return
            val current = display.mode
            val sameRes = display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }
            if (sameRes.isEmpty()) return
            maxRefreshHz = sameRes.maxOf { it.refreshRate }

            val wantHigh = cfg.highRefreshRate && effectiveProfile() != PerfProfile.BATTERY
            val pick = if (wantHigh)
            {
                sameRes.maxBy { it.refreshRate }
            }
            else
            {
                // Lowest mode that still reaches 60 Hz, or the fastest one there is.
                sameRes.filter { it.refreshRate >= 59f }.minByOrNull { it.refreshRate }
                    ?: sameRes.maxBy { it.refreshRate }
            }
            val attrs = act.window.attributes
            if (attrs.preferredDisplayModeId != pick.modeId)
            {
                attrs.preferredDisplayModeId = pick.modeId
                act.window.attributes = attrs
            }
            requestedRefresh.value = "${pick.refreshRate.roundToInt()} Hz (panel max ${maxRefreshHz.roundToInt()} Hz)"
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "display mode selection failed", t)
        }
    }

    fun onGlSurfaceCreated(s: Surface)
    {
        surface = s
        applySurfaceFrameRate()
    }

    private fun applySurfaceFrameRate()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val s = surface ?: return
        if (!s.isValid) return
        try
        {
            s.setFrameRate(targetFps().toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "setFrameRate failed", t)
        }
    }

    // ---- render thread ------------------------------------------------------------------

    fun onRenderThreadStart()
    {
        try
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "render thread priority not raised", t)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            try
            {
                val phm = appContext?.getSystemService(PerformanceHintManager::class.java)
                hintSession = phm?.createHintSession(intArrayOf(Process.myTid()), frameBudgetNs())
                Log.i(TAG, "hint session ${if (hintSession != null) "created" else "unavailable"}")
            }
            catch (t: Throwable)
            {
                Log.w(TAG, "hint session failed", t)
            }
        }
    }

    private fun frameBudgetNs(): Long = 1_000_000_000L / targetFps().coerceAtLeast(1)

    private fun updateHintTarget()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val session = hintSession as? PerformanceHintManager.Session ?: return
        try
        {
            session.updateTargetWorkDuration(frameBudgetNs())
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "updateTargetWorkDuration failed", t)
        }
    }

    /** Render thread: one GPU frame finished, [workNs] of CPU time spent on it. */
    fun onFrameRendered(workNs: Long)
    {
        if (workNs <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            (hintSession as? PerformanceHintManager.Session)?.let {
                try { it.reportActualWorkDuration(workNs) } catch (_: Throwable) {}
            }
        }
        val now = System.nanoTime()
        if (statSinceNs == 0L) statSinceNs = now
        statFrames++
        statWorkNs += workNs
        val elapsed = now - statSinceNs
        if (elapsed >= 500_000_000L)
        {
            val fps = statFrames * 1e9 / elapsed
            val ms = statWorkNs / 1e6 / statFrames
            fpsText.value = "FPS %.0f · %.1f ms".format(fps, ms)
            statFrames = 0
            statWorkNs = 0
            statSinceNs = now
        }
    }

    // ---- RuneLite config ----------------------------------------------------------------

    private fun configManager(): net.runelite.client.config.ConfigManager? =
        net.runelite.mp.ui.bridge.RuneLiteAccess.instance(net.runelite.client.config.ConfigManager::class.java)

    /**
     * Called about once a second from the overlay loop until the RuneLite injector exists,
     * then seeds GPU settings once and pushes the FPS target.
     */
    fun pollClientReady()
    {
        val target = targetFps()
        if (fpsPushedToClient == target) return
        val cm = configManager() ?: return
        seedGpuDefaultsOnce(cm)
        pushFpsTarget()
    }

    private fun pushFpsTarget()
    {
        val target = targetFps()
        if (fpsPushedToClient == target) return
        val cm = configManager() ?: return
        try
        {
            cm.setConfiguration(GPU_GROUP, "unlockFps", "true")
            cm.setConfiguration(GPU_GROUP, "fpsTarget", target.toString())
            fpsPushedToClient = target
            Log.i(TAG, "GPU fpsTarget=$target (${effectiveProfile()})")
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "could not set GPU fps target", t)
        }
    }

    /**
     * First run only: quality defaults for handheld hardware. The GPU plugin's stock
     * defaults are pitched at phones (75% resolution, no MSAA, 2 threads). After this the
     * user's own changes in the GPU plugin settings are left alone.
     */
    private fun seedGpuDefaultsOnce(cm: net.runelite.client.config.ConfigManager)
    {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        try
        {
            val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
            cm.setConfiguration(GPU_GROUP, "resolutionScale", "100")
            cm.setConfiguration(GPU_GROUP, "msaaSamples", "TWO")
            cm.setConfiguration(GPU_GROUP, "numThreads", threads.toString())
            cm.setConfiguration(GPU_GROUP, "drawDistance", "75")
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            Log.i(TAG, "seeded GPU defaults (threads=$threads)")
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "could not seed GPU defaults", t)
        }
    }
}
