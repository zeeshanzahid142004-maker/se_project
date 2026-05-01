package com.example.myapplication



import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── BoxOverlay.kt ─────────────────────────────────────────────────────────────

private const val MODEL_INPUT_SIZE_F = 640f

@Composable
fun BoxOverlay(
    boxes:          List<DetectionBox>,
    frameProjection: FrameProjection?,
    scaleValue:     Float,           // pass boxScaleAnim.value from parent
    modifier:       Modifier = Modifier
) {
    if (boxes.isEmpty() || frameProjection == null) return

    val density    = LocalDensity.current
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color          = android.graphics.Color.WHITE
            isFakeBoldText = true
            isAntiAlias    = true
        }
    }
    labelPaint.textSize = with(density) { 11.sp.toPx() }

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

                val strokePx      = with(density) { 2.dp.toPx() }
                val labelOffsetPx = with(density) { 8.dp.toPx() }

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
                    val scrBottom   = imgBottom* scaleFactor + translateY

                    val scrLeft  = if (frameProjection.mirrored) size.width - scrRightRaw else scrLeftRaw
                    val scrRight = if (frameProjection.mirrored) size.width - scrLeftRaw  else scrRightRaw

                    val boxW = (scrRight  - scrLeft ).coerceAtLeast(4f)
                    val boxH = (scrBottom - scrTop  ).coerceAtLeast(4f)

                    drawRect(
                        color   = Color.Green,
                        topLeft = Offset(scrLeft, scrTop),
                        size    = Size(boxW, boxH),
                        style   = Stroke(width = strokePx)
                    )

                    val label      = box.label.uppercase()
                    val textWidth  = labelPaint.measureText(label)
                    val fm         = labelPaint.fontMetrics
                    val topPadPx   = with(density) { 2.dp.toPx() }
                    val minBaseline = (-fm.ascent) + topPadPx
                    val maxBaseline = size.height - fm.descent
                    val labelX     = scrLeft.coerceIn(0f, (size.width - textWidth).coerceAtLeast(0f))
                    val aboveBase  = scrTop - labelOffsetPx
                    val belowBase  = scrBottom + labelOffsetPx - fm.ascent
                    val labelY     = (if (aboveBase >= minBaseline) aboveBase else belowBase)
                        .coerceIn(minBaseline, maxBaseline)

                    drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)
                }
            }
    )
}