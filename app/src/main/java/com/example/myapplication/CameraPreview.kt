package com.example.myapplication
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import java.util.concurrent.ExecutorService

// ── CameraPreview.kt ──────────────────────────────────────────────────────────

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    useFrontCamera:    Boolean,
    detectorProvider:    () -> YoloDetector?,
    isScanningRef:     AtomicBoolean,
    sideInsetDp:       androidx.compose.ui.unit.Dp,
    topInsetDp:        androidx.compose.ui.unit.Dp,
    bottomInsetDp:     androidx.compose.ui.unit.Dp,
    onCameraReady:     (Boolean) -> Unit,
    onFrameProjection: (FrameProjection?) -> Unit,
    onBoxesDetected:   (List<DetectionBox>) -> Unit,
    onNewItems:        (List<DetectionBox>) -> Unit,
    modifier:          Modifier = Modifier
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewViewRef     = remember { mutableStateOf<PreviewView?>(null) }
    var lastInferenceMs    by remember { mutableStateOf(0L) }

    var cameraProviderRef  by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysisRef   by remember { mutableStateOf<ImageAnalysis?>(null) }
    var analysisExecutorRef by remember { mutableStateOf<ExecutorService?>(null) }

    DisposableEffect(useFrontCamera) {
        onDispose {
            try { imageAnalysisRef?.clearAnalyzer() }  catch (_: Exception) {}
            try { cameraProviderRef?.unbindAll() }      catch (_: Exception) {}
            try { analysisExecutorRef?.shutdownNow() }  catch (_: Exception) {}
        }
    }

    key(useFrontCamera) {
        AndroidView(
            modifier = modifier,
            factory  = { ctx ->
                val pv = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef.value = pv

                val mainExecutor     = ContextCompat.getMainExecutor(ctx)
                val analysisExecutor = Executors.newSingleThreadExecutor()
                analysisExecutorRef  = analysisExecutor

                ProcessCameraProvider.getInstance(ctx).apply {
                    addListener({
                        try {
                            val provider = get()
                            cameraProviderRef = provider

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(pv.surfaceProvider)
                            }

                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { ia ->
                                    imageAnalysisRef = ia
                                    ia.setAnalyzer(analysisExecutor) {

                                        imageProxy ->
                                        Log.e("YOLO_DEBUG", "Analyzer fired — scanning=${isScanningRef.get()} detector=${detectorProvider() != null}")
                                        val currentDetector = detectorProvider ()
                                        if (currentDetector == null) {
                                            imageProxy.close(); return@setAnalyzer
                                        }
                                        val now = System.currentTimeMillis()
                                        if (now - lastInferenceMs < 800L) {
                                            Log.e("YOLO_DEBUG", "❌ Skipped — throttled")
                                            imageProxy.close(); return@setAnalyzer
                                        }

                                        if (detectorProvider ()== null) {
                                            Log.e("YOLO_DEBUG", "❌ Skipped — detector is null")
                                            imageProxy.close(); return@setAnalyzer
                                        }



                                        lastInferenceMs = now

                                        val mediaImage = imageProxy.image
                                        if (mediaImage == null || detectorProvider() == null) {
                                            imageProxy.close(); return@setAnalyzer
                                        }

                                        val rotation  = imageProxy.imageInfo.rotationDegrees
                                        val isRotated = rotation == 90 || rotation == 270
                                        val imgW = if (isRotated) mediaImage.height else mediaImage.width
                                        val imgH = if (isRotated) mediaImage.width  else mediaImage.height

                                        val pv2   = previewViewRef.value
                                        val viewW = pv2?.width?.takeIf  { it > 0 } ?: imgW
                                        val viewH = pv2?.height?.takeIf { it > 0 } ?: imgH

                                        val density      = ctx.resources.displayMetrics.density
                                        val hPadPx       = sideInsetDp.value   * density
                                        val tPadPx       = topInsetDp.value    * density
                                        val bPadPx       = bottomInsetDp.value * density
                                        val previewScale = maxOf(viewW / imgW.toFloat(), viewH / imgH.toFloat())
                                        val scaledImageW = imgW * previewScale
                                        val scaledImageH = imgH * previewScale
                                        val transX       = ((scaledImageW - viewW) / 2f).coerceAtLeast(0f)
                                        val transY       = ((scaledImageH - viewH) / 2f).coerceAtLeast(0f)

                                        val cropLeft   = ((hPadPx + transX) / previewScale).toInt().coerceIn(0, imgW - 1)
                                        val cropTop    = ((tPadPx + transY) / previewScale).toInt().coerceIn(0, imgH - 1)
                                        val cropRight  = ((viewW - hPadPx + transX) / previewScale).toInt().coerceIn(cropLeft + 1, imgW)
                                        val cropBottom = ((viewH - bPadPx + transY) / previewScale).toInt().coerceIn(cropTop  + 1, imgH)
                                        val cropW      = (cropRight  - cropLeft).coerceAtLeast(1)
                                        val cropH      = (cropBottom - cropTop ).coerceAtLeast(1)

                                        try {
                                            val full    = imageProxy.toBitmap()
                                            val matrix  = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                            val rotated = android.graphics.Bitmap.createBitmap(full, 0, 0, full.width, full.height, matrix, true)
                                            val safeCropW = cropW.coerceAtMost(rotated.width  - cropLeft).coerceAtLeast(1)
                                            val safeCropH = cropH.coerceAtMost(rotated.height - cropTop ).coerceAtLeast(1)
                                            val cropped   = android.graphics.Bitmap.createBitmap(rotated, cropLeft, cropTop, safeCropW, safeCropH)

                                            try {
                                             val boxes = currentDetector.detectBoxes(cropped)
                                                mainExecutor.execute {
                                                    if (!isScanningRef.get()) {
                                                        onBoxesDetected(emptyList())
                                                        onFrameProjection(null)
                                                        return@execute
                                                    }
                                                    onBoxesDetected(boxes)
                                                    onFrameProjection(FrameProjection(
                                                        imageWidth  = imgW,
                                                        imageHeight = imgH,
                                                        cropX       = cropLeft,
                                                        cropY       = cropTop,
                                                        cropWidth   = safeCropW,
                                                        cropHeight  = safeCropH,
                                                        viewWidth   = viewW,
                                                        viewHeight  = viewH,
                                                        mirrored    = useFrontCamera
                                                    ))
                                                    if (boxes.isNotEmpty() && currentDetector.justRegistered) {
                                                        onNewItems(boxes)
                                                    }
                                                    currentDetector.justRegistered = false
                                                }
                                            } finally {
                                                cropped.recycle(); rotated.recycle(); full.recycle()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CameraPreview", "Frame error: ${e.message}", e)
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                                else               CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, analysis
                            )
                            mainExecutor.execute { onCameraReady(true) }
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "Setup failed: ${e.message}", e)
                            ContextCompat.getMainExecutor(ctx).execute { onCameraReady(false) }
                        }
                    }, mainExecutor)
                }
                pv
            }
        )
    }
}