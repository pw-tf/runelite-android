package net.runelite.mp.ui.panels

import androidx.compose.runtime.Composable

/**
 * Maps an RL nav-button tooltip to a Compose-native replacement for its Swing panel.
 * The icon strip looks here first when a button is tapped — a hit suppresses the
 * `ClientUI.openPanel` call (the AWT panel never paints) and the host's content
 * column renders the registered composable instead.
 *
 * Tooltips are matched exactly because that's the only stable identifier we have
 * from `NavigationButton` — the underlying plugin instance varies by build and the
 * priority field is ordering metadata, not identity.
 */
internal object PanelRegistry
{
    private val builtIn: Map<String, @Composable () -> Unit> = mapOf(
        "Notes" to { NotesPanel() },
        "Screen Markers" to { ScreenMarkersPanel() },
        "XP Tracker" to { XpTrackerPanel() },
        // Upstream RL's nav tooltips don't match the plugin display names:
        //   WorldHopperPlugin → "World Switcher"
        //   HiscorePlugin     → "Hiscore"  (no second 's')
        //   TimeTrackingPlugin → "Time Tracking"  (already correct)
        // Get these wrong and the icon strip falls back to opening the AWT Swing panel
        // instead of routing to the Compose replacement — which is exactly the bug that
        // hid the HiScore and World list panels on first launch.
        "World Switcher" to { WorldHopperPanel() },
        "Hiscore" to { HiscoresPanel() },
        "Grand Exchange" to { GrandExchangePanel() },
        "Time Tracking" to { TimersPanel() },
        "Quest Helper" to { QuestHelperPanel() },
        // External resource-packs plugin (melky.resourcepacks) — its nav button tooltip is
        // "Resource packs hub"; route it to our Compose hub browser.
        "Resource packs hub" to { ResourcePacksPanel() },
    )

    /** Built-in panels plus any the build flavor contributes (see FlavorHooks). */
    private val byTooltip: Map<String, @Composable () -> Unit> =
        builtIn + net.runelite.mp.flavor.FlavorHooks.panels.associate { it.key to it.content }

    fun hasPanel(key: String): Boolean = byTooltip.containsKey(key)

    @Composable
    fun render(key: String)
    {
        val composable = byTooltip[key] ?: return
        composable()
    }
}
