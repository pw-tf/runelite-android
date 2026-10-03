package net.runelite.mp.flavor

import androidx.compose.runtime.Composable

/**
 * A Compose panel contributed by the active build flavor (see `FlavorHooks`). It gets an
 * icon in the nav strip under the modifier chips and renders in the content column like
 * any [net.runelite.mp.ui.panels.PanelRegistry] panel, keyed by [key].
 */
class FlavorPanel(
    val key: String,
    val label: String,
    val content: @Composable () -> Unit,
)
