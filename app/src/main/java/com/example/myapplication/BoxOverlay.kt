package com.example.myapplication

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MODEL_INPUT_SIZE_F = 640f

@Composable
fun BoxOverlay(
    boxes:           List<DetectionBox>,
    frameProjection: FrameProjection?,
    scaleValue:      Float,
    modifier:        Modifier = Modifier
) {
    if (boxes.isEmpty() || frameProjection == null) return

    val density    = LocalDensity.current
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isFakeBoldText = true
            isAntiAlias    = true
            textAlign      = android.graphics.Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .scale(scaleValue)
            .drawBehind {
                val imageW = frameProjection.imageWidth.toFloat()
                val imageH = frameProjection.imageHeight.toFloat()
                val cropX  = frameProjection.cropX.toFloat()
                val cropY  = frameProjection.cropY.toFloat()
                val cropW  = frameProjection.cropWidth.toFloat()
                val cropH  = frameProjection.cropHeight.toFloat()

                val cropScaleX  = cropW / MODEL_INPUT_SIZE_F
                val cropScaleY  = cropH / MODEL_INPUT_SIZE_F
                val scaleFactor = maxOf(size.width / imageW, size.height / imageH)
                val translateX  = -(((imageW * scaleFactor) - size.width)  / 2f)
                val translateY  = -(((imageH * scaleFactor) - size.height) / 2f)

                val strokePx = with(density) { 2.dp.toPx() }

                boxes.forEach { box ->
                    val detLeft   = (box.cx - box.w / 2f).coerceIn(0f, 1f) * MODEL_INPUT_SIZE_F
                    val detTop    = (box.cy - box.h / 2f).coerceIn(0f, 1f) * MODEL_INPUT_SIZE_F
                    val detRight  = (box.cx + box.w / 2f).coerceIn(0f, 1f) * MODEL_INPUT_SIZE_F
                    val detBottom = (box.cy + box.h / 2f).coerceIn(0f, 1f) * MODEL_INPUT_SIZE_F

                    val imgLeft   = detLeft   * cropScaleX + cropX
                    val imgTop    = detTop    * cropScaleY + cropY
                    val imgRight  = detRight  * cropScaleX + cropX
                    val imgBottom = detBottom * cropScaleY + cropY

                    val scrLeftRaw  = imgLeft  * scaleFactor + translateX
                    val scrRightRaw = imgRight * scaleFactor + translateX
                    val scrTop      = imgTop   * scaleFactor + translateY
                    val scrBottom   = imgBottom * scaleFactor + translateY

                    val scrLeft  = if (frameProjection.mirrored) size.width - scrRightRaw else scrLeftRaw
                    val scrRight = if (frameProjection.mirrored) size.width - scrLeftRaw  else scrRightRaw
                    val boxW     = (scrRight - scrLeft).coerceAtLeast(4f)
                    val boxH     = (scrBottom - scrTop).coerceAtLeast(4f)

                    // ── Bounding box ──────────────────────────────────────
                    drawRect(
                        color   = Color(0xFF00FF88),
                        topLeft = Offset(scrLeft, scrTop),
                        size    = Size(boxW, boxH),
                        style   = Stroke(width = strokePx)
                    )

                    // ── Green pill label ──────────────────────────────────
                    val label   = box.label.uppercase()
                    val bgPadW  = with(density) { 16.dp.toPx() }
                    val bgPadH  = with(density) { 8.dp.toPx() }
                    val centerX = (scrLeft + scrRight) / 2f

                    labelPaint.textSize = with(density) { 12.sp.toPx() }
                    val textW   = labelPaint.measureText(label)
                    val fm      = labelPaint.fontMetrics
                    val textH   = -fm.ascent + fm.descent
                    val pillW   = textW + bgPadW * 2f
                    val pillH   = textH + bgPadH * 2f
                    val pillLeft = centerX - pillW / 2f
                    val pillTop  = scrTop - pillH - with(density) { 4.dp.toPx() }

                    // Shadow
                    drawRoundRect(
                        color        = Color.Black.copy(alpha = 0.3f),
                        topLeft      = Offset(pillLeft + 2f, pillTop + 2f),
                        size         = Size(pillW, pillH),
                        cornerRadius = CornerRadius(pillH / 2f)
                    )

                    // Green pill background
                    drawRoundRect(
                        color        = Color(0xFF00FF88),
                        topLeft      = Offset(pillLeft, pillTop),
                        size         = Size(pillW, pillH),
                        cornerRadius = CornerRadius(pillH / 2f)
                    )

                    // Black text
                    labelPaint.color = android.graphics.Color.BLACK
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        centerX,
                        pillTop + pillH / 2f + textH / 2f - fm.descent,
                        labelPaint
                    )
                }
            }
    )
}