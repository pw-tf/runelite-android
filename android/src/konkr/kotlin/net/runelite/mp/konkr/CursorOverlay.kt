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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.runelite.mp.AwtPointer

/** Pointer dot: radius, fill opacity and outline opacity. The click lands at its center. */
private const val DOT_RADIUS_DP = 6f
private const val DOT_FILL_ALPHA = 0.55f
private const val DOT_OUTLINE_ALPHA = 0.5f

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
                val radius = DOT_RADIUS_DP.dp.toPx()
                drawCircle(Color.White.copy(alpha = DOT_FILL_ALPHA * alpha), radius, center = at)
                drawCircle(
                    Color.Black.copy(alpha = DOT_OUTLINE_ALPHA * alpha),
                    radius,
                    center = at,
                    style = Stroke(width = 1.dp.toPx()),
                )
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

