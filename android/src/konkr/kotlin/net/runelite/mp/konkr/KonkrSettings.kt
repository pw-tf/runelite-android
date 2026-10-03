package net.runelite.mp.konkr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.runelite.mp.ui.RlPalette
import net.runelite.mp.ui.panels.PanelButton
import net.runelite.mp.ui.panels.PanelDivider
import net.runelite.mp.ui.panels.PanelScaffold
import net.runelite.mp.ui.widgets.IntSliderRow
import net.runelite.mp.ui.widgets.SectionHeader
import net.runelite.mp.ui.widgets.SegmentedRow
import net.runelite.mp.ui.widgets.ToggleRow

/** Controller + performance settings panel, opened from the 🎮 icon in the nav strip. */
object KonkrSettings
{
    const val KEY = "Controller"

    @Composable
    fun Panel()
    {
        val cfg = ControllerStore.config.value
        val devices = GamepadInput.connected.value
        var editing by remember { mutableStateOf<Int?>(null) }

        PanelScaffold(
            title = "Controller",
            subtitle = devices.firstOrNull() ?: "No controller detected",
        ) {
            val code = editing
            if (code != null)
            {
                ActionPicker(code, cfg.bindings[code] ?: Action.None) { picked ->
                    if (picked != null)
                    {
                        ControllerStore.update { it.copy(bindings = it.bindings + (code to picked)) }
                    }
                    editing = null
                }
            }
            else
            {
                Settings(cfg, devices, onEdit = { editing = it })
            }
        }
    }

    @Composable
    private fun Settings(cfg: ControllerConfig, devices: List<String>, onEdit: (Int) -> Unit)
    {
        Column {
            ToggleRow("Controller input", "Use the gamepad for the game", cfg.enabled) { on ->
                if (!on) GamepadInput.resetAll()
                ControllerStore.update { it.copy(enabled = on) }
            }

            SectionHeader("Sticks")
            val roles = StickRole.entries
            SegmentedRow("Left stick", null, roles.map { it.label }, roles.indexOf(cfg.leftStick)) { i ->
                ControllerStore.update { it.copy(leftStick = roles[i]) }
            }
            SegmentedRow("Right stick", null, roles.map { it.label }, roles.indexOf(cfg.rightStick)) { i ->
                ControllerStore.update { it.copy(rightStick = roles[i]) }
            }
            val axes = RightStickAxes.entries
            SegmentedRow(
                "Right stick axes",
                "Change if the right stick moves the wrong way or acts like a trigger",
                axes.map { it.label },
                axes.indexOf(cfg.rightStickAxes),
            ) { i -> ControllerStore.update { it.copy(rightStickAxes = axes[i]) } }
            IntSliderRow("Deadzone", "Stick travel ignored around center", cfg.deadzone, 0, 60, "%") { v ->
                ControllerStore.update { it.copy(deadzone = v) }
            }
            IntSliderRow("Cursor speed", "At full tilt", cfg.cursorSpeed, 1, 40) { v ->
                ControllerStore.update { it.copy(cursorSpeed = v) }
            }
            IntSliderRow("Cursor curve", "10 is linear; higher is finer near center", cfg.cursorCurve, 10, 40) { v ->
                ControllerStore.update { it.copy(cursorCurve = v) }
            }
            IntSliderRow("Slow cursor speed", "While the slow-cursor button is held", cfg.precisionPercent, 5, 100, "%") { v ->
                ControllerStore.update { it.copy(precisionPercent = v) }
            }
            ToggleRow("Hide idle cursor", "Fade the pointer after 3 seconds", cfg.cursorAutoHide) { on ->
                ControllerStore.update { it.copy(cursorAutoHide = on) }
            }
            ToggleRow("Invert camera X", null, cfg.cameraInvertX) { on ->
                ControllerStore.update { it.copy(cameraInvertX = on) }
            }
            ToggleRow("Invert camera Y", null, cfg.cameraInvertY) { on ->
                ControllerStore.update { it.copy(cameraInvertY = on) }
            }
            ToggleRow("Invert scroll stick", null, cfg.scrollInvert) { on ->
                ControllerStore.update { it.copy(scrollInvert = on) }
            }
            IntSliderRow("Trigger threshold", "How far L2/R2 travel before they press", cfg.triggerThreshold, 5, 95, "%") { v ->
                ControllerStore.update { it.copy(triggerThreshold = v) }
            }

            SectionHeader("Buttons")
            Hint("Tap a button to change what it does.")
            val codes = (GamepadKeys.standard + cfg.bindings.keys).distinct()
            for (c in codes)
            {
                BindingRow(GamepadKeys.name(c), (cfg.bindings[c] ?: Action.None).label) { onEdit(c) }
            }
            Spacer(Modifier.height(6.dp))
            val capturing = GamepadInput.capture.value != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelButton(
                    label = if (capturing) "Press a controller button…" else "Bind another button",
                    onClick = {
                        GamepadInput.capture.value = if (capturing) null else { captured -> onEdit(captured) }
                    },
                )
            }

            SectionHeader("Performance")
            val profiles = PerfProfile.entries
            SegmentedRow(
                "Profile",
                "Battery 30 FPS · Balanced 60 · Performance at the panel's max refresh",
                profiles.map { it.label },
                profiles.indexOf(cfg.perfProfile),
            ) { i ->
                ControllerStore.update { it.copy(perfProfile = profiles[i]) }
                PerformanceManager.apply()
            }
            Hint("System Game Mode: ${PerformanceManager.systemGameMode.value} (overrides the profile when set)")
            ToggleRow("High refresh rate", "Run the display at its fastest mode", cfg.highRefreshRate) { on ->
                ControllerStore.update { it.copy(highRefreshRate = on) }
                PerformanceManager.apply()
            }
            ToggleRow(
                "Sustained performance",
                "Steadier frame rate in long sessions, lower peak clocks",
                cfg.sustainedPerformance,
            ) { on ->
                ControllerStore.update { it.copy(sustainedPerformance = on) }
                PerformanceManager.apply()
            }
            ToggleRow("Show FPS", "Frame rate and frame time in the corner", cfg.showFps) { on ->
                ControllerStore.update { it.copy(showFps = on) }
            }
            Hint("Display: ${PerformanceManager.requestedRefresh.value}")
            Hint("Renderer: ${PerformanceManager.fpsText.value}")

            SectionHeader("Diagnostics")
            if (devices.isEmpty()) Hint("No controllers connected.")
            for (d in devices) Hint(d)
            Hint("Last button: ${GamepadInput.lastKey.value}")
            Hint("Axes: ${GamepadInput.lastAxes.value}")

            Spacer(Modifier.height(10.dp))
            PanelDivider()
            Spacer(Modifier.height(10.dp))
            PanelButton(label = "Reset to defaults", onClick = {
                GamepadInput.resetAll()
                ControllerStore.resetToDefaults()
                PerformanceManager.apply()
            })
        }
    }

    @Composable
    private fun ActionPicker(code: Int, current: Action, onDone: (Action?) -> Unit)
    {
        SectionHeader("${GamepadKeys.name(code)} does…")
        PanelButton(label = "Cancel", onClick = { onDone(null) })
        Spacer(Modifier.height(6.dp))
        for (action in Action.all)
        {
            val selected = action.id == current.id
            Text(
                action.label,
                color = if (selected) Color.White else RlPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) RlPalette.AccentSurface else Color.Transparent)
                    .clickable { onDone(action) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            )
        }
    }

    @Composable
    private fun BindingRow(button: String, action: String, onClick: () -> Unit)
    {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(button, color = RlPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(action, color = RlPalette.Accent, fontSize = 12.sp)
        }
    }

    @Composable
    private fun Hint(text: String)
    {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(text, color = RlPalette.TextSecondary, fontSize = 11.sp)
        }
    }
}
