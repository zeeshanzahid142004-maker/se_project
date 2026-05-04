package com.example.myapplication

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val ShimmerBase = Color(0xFF2A303C)
private val ShimmerHighlight = Color(0xFF3D4A5C)
private const val SHIMMER_TRANSLATE_RANGE = 1000f
private const val SHIMMER_WIDTH = 400f

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = 0f,
        targetValue = SHIMMER_TRANSLATE_RANGE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return this.background(
        Brush.linearGradient(
            colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
            start = Offset(translateX - SHIMMER_WIDTH, 0f),
            end = Offset(translateX + SHIMMER_WIDTH, SHIMMER_WIDTH)
        )
    )
}
