package net.runelite.mp.konkr

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.runelite.mp.AwtPointer

/**
 * Drawn over the game viewport: runs the per-frame stick loop and paints the pointer
 * (the OSRS client draws no cursor of its own) and the optional FPS readout.
 */
@Composable
internal fun KonkrGameOverlay()
{
    LaunchedEffect(Unit) {
        var nextSeedCheckNs = 0L
        while (true)
        {
            withFrameNanos { now ->
                StickCursor.frame(now)
                if (now >= nextSeedCheckNs)
                {
                    nextSeedCheckNs = now + 1_000_000_000L
                    PerformanceManager.pollClientReady()
                }
            }
        }
    }

    val visible by StickCursor.visible
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 80 else 400),
        label = "cursorAlpha",
    )

    Box(Modifier.fillMaxSize()) {
        if (alpha > 0f)
        {
            Canvas(Modifier.fillMaxSize()) {
                // Read inside draw so pointer motion only redraws, never recomposes.
                val at = AwtPointer.windowToCompose(StickCursor.posX.floatValue, StickCursor.posY.floatValue)
                    ?: return@Canvas
                val s = 20.dp.toPx()
                val arrow = arrowPath(s)
                translate(at.x, at.y) {
                    drawPath(arrow, Color.White.copy(alpha = alpha))
                    drawPath(arrow, Color.Black.copy(alpha = alpha), style = Stroke(width = 1.5.dp.toPx()))
                }
            }
        }

        if (ControllerStore.config.value.showFps)
        {
            val fps by PerformanceManager.fpsText
            Text(
                fps,
                color = Color(0xFFFFFF00),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color(0x99000000))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** Classic arrow pointer with its hotspot at (0, 0). */
private fun arrowPath(size: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(0f, size)
    lineTo(size * 0.28f, size * 0.74f)
    lineTo(size * 0.45f, size * 1.08f)
    lineTo(size * 0.6f, size * 1.0f)
    lineTo(size * 0.43f, size * 0.68f)
    lineTo(size * 0.75f, size * 0.68f)
    close()
}
