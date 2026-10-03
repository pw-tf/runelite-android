package net.runelite.mp.konkr

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * Current [ControllerConfig], persisted in device-level SharedPreferences. That storage is
 * ready before RuneLite boots, and controller layout belongs to the device rather than to
 * a RuneLite profile.
 *
 * [config] is Compose state so the settings panel and overlay recompose on change.
 */
object ControllerStore
{
    private const val PREFS = "konkr_controller"

    private var prefs: SharedPreferences? = null

    val config = mutableStateOf(ControllerConfig())

    val current: ControllerConfig get() = config.value

    fun init(context: Context)
    {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        config.value = ControllerConfig.fromMap(p.all)
    }

    fun update(transform: (ControllerConfig) -> ControllerConfig)
    {
        val next = transform(config.value)
        if (next == config.value) return
        config.value = next
        save(next)
    }

    fun resetToDefaults()
    {
        val defaults = ControllerConfig()
        config.value = defaults
        save(defaults)
    }

    private fun save(cfg: ControllerConfig)
    {
        val p = prefs ?: return
        val e = p.edit().clear()
        for ((k, v) in cfg.toMap())
        {
            when (v)
            {
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                is String -> e.putString(k, v)
            }
        }
        e.apply()
    }
}
