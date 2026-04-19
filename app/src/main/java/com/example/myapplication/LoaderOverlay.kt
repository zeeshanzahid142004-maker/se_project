package com.example.myapplication

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val loaderTeal  = Color(0xFF2DD4BF)
private val loaderDark  = Color(0xFF080C10)
private val loaderMuted = Color(0xFF8B949E)
private val loaderWhite = Color(0xFFF0F6FC)

/**
 * Full-screen loading overlay with a modernized dual-ring spinner, corner brackets,
 * a subtle full-screen scan line, and animated activity dots.
 *
 * @param title    Primary message shown beneath the spinner (e.g. "Initializing Scanner").
 * @param subtitle Secondary message shown below the title (e.g. "Preparing camera…").
 */
@Composable
fun FullScreenLoader(title: String, subtitle: String) {
    val inf = rememberInfiniteTransition(label = "fsl")

    // Outer ring rotates clockwise in ~1.4 s
    val outerAngle by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "fslOuter"
    )
    // Inner ring rotates counter-clockwise in ~0.9 s
    val innerAngle by inf.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "fslInner"
    )
    // Scan line sweeps top→bottom→top
    val scanLineY by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "fslScan"
    )
    // Breathing glow / center-dot pulse
    val glow by inf.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fslGlow"
    )
    // Drives the 3-dot activity indicator (0 → 1 in 900 ms, looped)
    val dotPhase by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "fslDots"
    )

    // Fade in on first composition
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val entryAlpha by animateFloatAsState(
        if (visible) 1f else 0f, tween(280), label = "fslFade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(entryAlpha)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0D1520), loaderDark),
                    radius = 900f
                )
            )
            .clickable(remember { MutableInteractionSource() }, null) {},
        contentAlignment = Alignment.Center
    ) {
        // Subtle full-screen sweep line in the background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val ly = size.height * scanLineY
                    drawLine(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, loaderTeal.copy(0.05f), Color.Transparent)
                        ),
                        Offset(0f, ly), Offset(size.width, ly), 30f
                    )
                    drawLine(
                        loaderTeal.copy(alpha = 0.07f),
                        Offset(0f, ly), Offset(size.width, ly), 1.2f
                    )
                }
        )

        // Centered spinner + text content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Spinner container with corner-bracket decorations
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawBehind {
                        val bLen = 20.dp.toPx()
                        val sw   = 2.2f
                        val col  = loaderTeal.copy(alpha = 0.65f)
                        // top-left
                        drawLine(col, Offset(0f, bLen), Offset(0f, 0f), sw)
                        drawLine(col, Offset(0f, 0f), Offset(bLen, 0f), sw)
                        // top-right
                        drawLine(col, Offset(size.width, bLen), Offset(size.width, 0f), sw)
                        drawLine(col, Offset(size.width, 0f), Offset(size.width - bLen, 0f), sw)
                        // bottom-left
                        drawLine(col, Offset(0f, size.height - bLen), Offset(0f, size.height), sw)
                        drawLine(col, Offset(0f, size.height), Offset(bLen, size.height), sw)
                        // bottom-right
                        drawLine(col, Offset(size.width, size.height - bLen), Offset(size.width, size.height), sw)
                        drawLine(col, Offset(size.width, size.height), Offset(size.width - bLen, size.height), sw)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Glow aura behind the rings
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .drawBehind {
                            drawCircle(loaderTeal.copy(alpha = 0.09f * glow), radius = size.minDimension / 2f)
                        }
                )

                // Outer ring
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .drawBehind {
                            val r  = size.minDimension / 2f
                            val sw = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
                            // dim track
                            drawCircle(loaderTeal.copy(alpha = 0.12f), r - sw.width / 2f, style = sw)
                            // bright arc
                            drawArc(
                                Brush.sweepGradient(listOf(Color.Transparent, loaderTeal)),
                                outerAngle, 230f, false, style = sw
                            )
                        }
                )

                // Inner ring (counter-rotating)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .drawBehind {
                            val r  = size.minDimension / 2f
                            val sw = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(loaderTeal.copy(alpha = 0.10f), r - sw.width / 2f, style = sw)
                            drawArc(
                                Brush.sweepGradient(listOf(Color.Transparent, loaderTeal.copy(0.85f))),
                                innerAngle, 140f, false, style = sw
                            )
                        }
                )

                // Pulsing center dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(glow)
                        .background(loaderTeal, CircleShape)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                title,
                color = loaderWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                subtitle,
                color = loaderMuted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(20.dp))

            // 3-dot activity indicator: dots light up sequentially
            val activeDot = (dotPhase * 3).toInt().coerceIn(0, 2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val dotAlpha = when {
                        i < activeDot  -> 1f
                        i == activeDot -> glow
                        else           -> 0.18f
                    }
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .alpha(dotAlpha)
                            .background(loaderTeal, CircleShape)
                    )
                }
            }
        }
    }
}
