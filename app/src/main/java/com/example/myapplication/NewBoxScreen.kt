package com.example.myapplication

import android.Manifest
import android.media.MediaPlayer
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.BoxRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NewBoxScreen"

private val tealN = Color(0xFF2DD4BF)
private val redN = Color(0xFFE53E3E)
private val orangeN = Color(0xFFF97316)
private val surfN = Color(0xFF161B22)
private val surfAltN = Color(0xFF1C2333)
private val borderN = Color(0xFF30363D)
private val mutedN = Color(0xFF8B949E)
private val whiteN = Color(0xFFF0F6FC)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewBoxScreen(navController: androidx.navigation.NavController) {
    Log.d(TAG, "NewBoxScreen: composing")

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            Log.d(TAG, "NewBoxScreen: requesting camera permission")
            cameraPermission.launchPermissionRequest()
        }
    }

    if (!cameraPermission.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080C10)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required", color = whiteN, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { cameraPermission.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = tealN)
                ) { Text("Grant Permission") }
            }
        }
        return
    }

    NewBoxContent(navController = navController)
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun NewBoxContent(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()
    val repository     = remember { BoxRepository(AppDatabase.getInstance(context)) }
    val supabaseRepo   = remember { com.example.myapplication.data.SupabaseRepository() }

    val detectedItems = remember { mutableStateListOf<DetectedItem>() }
    var showReviewSheet by remember { mutableStateOf(false) }
    var showComplaintSheet by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    // Triggers the flash/haptic/audio feedback on successful scan
    var scanFlashActive by remember { mutableStateOf(false) }

    val isScanningRef = remember { AtomicBoolean(true) }
    val hasNavigatedRef = remember { AtomicBoolean(false) }

    // Reset camera state when flipping
    LaunchedEffect(useFrontCamera) {
        isScanningRef.set(true)
        cameraReady = false
    }

    var detector by remember { mutableStateOf<YoloDetector?>(null) }

    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysisRef by remember { mutableStateOf<ImageAnalysis?>(null) }
    var analysisExecutorRef by remember { mutableStateOf<ExecutorService?>(null) }

    var lastInferenceMs by remember { mutableStateOf(0L) }
    val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

    fun stopScanningNow() {
        isScanningRef.set(false)
        try { imageAnalysisRef?.clearAnalyzer() } catch (_: Exception) {}
        try { cameraProviderRef?.unbindAll() } catch (_: Exception) {}
        try { analysisExecutorRef?.shutdownNow() } catch (_: Exception) {}
        imageAnalysisRef = null
        analysisExecutorRef = null
        cameraReady = false
    }

    LaunchedEffect(Unit) {
        val d = withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "YoloDetector: loading model from assets")
                YoloDetector(context).also { Log.d(TAG, "YoloDetector: loaded OK") }
            } catch (e: Exception) {
                Log.e(TAG, "YoloDetector: FAILED to load — ${e.message}", e)
                null
            }
        }
        detector = d
    }

    val haptic = LocalHapticFeedback.current

    // Sensory feedback: flash → haptic → audio when a new item is detected
    LaunchedEffect(scanFlashActive) {
        if (!scanFlashActive) return@LaunchedEffect
        // Haptic
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Audio — play the scan chime
        try {
            MediaPlayer.create(context, R.raw.crumble)?.apply {
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio feedback failed: ${e.message}")
        }
        // Hold flash for 420 ms then reset
        delay(420)
        scanFlashActive = false
    }

    DisposableEffect(Unit) {
        onDispose {
            stopScanningNow()
            try { detector?.close() } catch (_: Exception) {}
        }
    }

    val inf = rememberInfiniteTransition(label = "anim")
    val scanY by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "scanY"
    )
    val pulse by inf.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080C10))) {
        val topInsetDp = 68.dp
        val bottomInsetDp = 120.dp
        val camColor = if (cameraReady) tealN else redN

        key(useFrontCamera) {
        AndroidView(
            factory = { ctx ->
                Log.d(TAG, "AndroidView: creating PreviewView")
                val pv = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef.value = pv

                val mainExecutor = ContextCompat.getMainExecutor(ctx)
                val analysisExecutor = Executors.newSingleThreadExecutor()
                analysisExecutorRef = analysisExecutor
                val future = ProcessCameraProvider.getInstance(ctx)

                future.addListener({
                    try {
                        val provider = future.get()
                        cameraProviderRef = provider
                        Log.d(TAG, "CameraProvider: obtained")

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(pv.surfaceProvider)
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { ia ->
                                imageAnalysisRef = ia
                                ia.setAnalyzer(analysisExecutor) { imageProxy ->
                                    if (!isScanningRef.get()) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    val now = System.currentTimeMillis()
                                    if (now - lastInferenceMs < 800L) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    lastInferenceMs = now

                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        Log.w(TAG, "Analyzer: mediaImage is null")
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    val currentDetector = detector
                                    if (currentDetector == null) {
                                        Log.w(TAG, "Analyzer: detector is null, skipping inference")
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val isRotated = rotation == 90 || rotation == 270
                                    val imgW = if (isRotated) mediaImage.height else mediaImage.width
                                    val imgH = if (isRotated) mediaImage.width else mediaImage.height

                                    val pv2 = previewViewRef.value
                                    val viewW = pv2?.width?.takeIf { it > 0 } ?: imgW
                                    val viewH = pv2?.height?.takeIf { it > 0 } ?: imgH

                                    val density = ctx.resources.displayMetrics.density
                                    val hPadPx = 28f * density
                                    val tPadPx = 120f * density
                                    val bPadPx = 120f * density

                                    val cropW = ((viewW - 2 * hPadPx) * (imgW.toFloat() / viewW))
                                        .toInt().coerceIn(1, imgW)
                                    val cropH = ((viewH - tPadPx - bPadPx) * (imgH.toFloat() / viewH))
                                        .toInt().coerceIn(1, imgH)
                                    val cropX = ((imgW - cropW) / 2).coerceAtLeast(0)
                                    val cropY = (tPadPx * (imgH.toFloat() / viewH)).toInt().coerceAtLeast(0)

                                    Log.d(TAG, "Crop: imgW=$imgW imgH=$imgH cropX=$cropX cropY=$cropY cropW=$cropW cropH=$cropH")

                                    try {
                                        val full = imageProxy.toBitmap()
                                        val matrix = android.graphics.Matrix().apply {
                                            postRotate(rotation.toFloat())
                                        }
                                        val rotated = android.graphics.Bitmap.createBitmap(
                                            full, 0, 0, full.width, full.height, matrix, true
                                        )
                                        val safeCropW = cropW.coerceAtMost(rotated.width - cropX).coerceAtLeast(1)
                                        val safeCropH = cropH.coerceAtMost(rotated.height - cropY).coerceAtLeast(1)
                                        val cropped = android.graphics.Bitmap.createBitmap(
                                            rotated, cropX, cropY, safeCropW, safeCropH
                                        )

                                        Log.d(TAG, "Bitmap ready: ${cropped.width}x${cropped.height}, running inference")

                                        try {
                                            val results = currentDetector.detect(cropped)
                                            Log.d(TAG, "Inference results: $results")
                                            mainExecutor.execute {
                                                if (isScanningRef.get()) {
                                                    val hadNew = mergeDetections(detectedItems, results)
                                                    if (hadNew) {
                                                        scanFlashActive = true
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Inference failed: ${e.message}", e)
                                        } finally {
                                            cropped.recycle()
                                            rotated.recycle()
                                            full.recycle()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Bitmap crop failed: ${e.message}", e)
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        Log.d(TAG, "Camera bound to lifecycle OK")
                        mainExecutor.execute { cameraReady = true }
                    } catch (e: Exception) {
                        Log.e(TAG, "CameraProvider setup failed: ${e.message}", e)
                        mainExecutor.execute { cameraReady = false }
                    }
                }, mainExecutor)

                pv
            },
            modifier = Modifier.fillMaxSize()
        )
        } // end key(useFrontCamera)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val t = topInsetDp.toPx()
                    val b = bottomInsetDp.toPx()
                    val cr = 20.dp.toPx()
                    val dark = androidx.compose.ui.graphics.Color(0xCC000000)

                    drawRect(dark, Offset(0f, 0f), Size(size.width, t))
                    drawRect(dark, Offset(0f, size.height - b), Size(size.width, b))

                    drawRect(dark, Offset(0f, t), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr, Offset(cr, t + cr), blendMode = BlendMode.Clear)

                    drawRect(dark, Offset(size.width - cr, t), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr, Offset(size.width - cr, t + cr), blendMode = BlendMode.Clear)

                    drawRect(dark, Offset(0f, size.height - b - cr), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr, Offset(cr, size.height - b - cr), blendMode = BlendMode.Clear)

                    drawRect(dark, Offset(size.width - cr, size.height - b - cr), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr, Offset(size.width - cr, size.height - b - cr), blendMode = BlendMode.Clear)
                }
        )

        val flashAnim by rememberInfiniteTransition(label = "flash").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "flash"
        )
        // Flash color: green/white during scan success, red otherwise
        val successColor = Color(0xFF00E676)
        val boxColor by animateColorAsState(
            targetValue = if (scanFlashActive) successColor else redN,
            animationSpec = tween(durationMillis = 120),
            label = "boxColor"
        )
        val boxStrokeWidth by animateFloatAsState(
            targetValue = if (scanFlashActive) 5.0f else 2.4f,
            animationSpec = tween(durationMillis = 120),
            label = "boxStroke"
        )
        val boxScale by animateFloatAsState(
            targetValue = if (scanFlashActive) 1.05f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "boxScale"
        )

        if (detectedItems.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInsetDp + 24.dp)
                    .padding(horizontal = 56.dp)
                    .size(210.dp, 190.dp)
                    .scale(boxScale)
                    .drawBehind {
                        val cLen = 30.dp.toPx()
                        val alpha = if (scanFlashActive) 1f else flashAnim
                        val col = boxColor.copy(alpha = alpha)

                        drawLine(col, Offset(0f, cLen), Offset(0f, 0f), boxStrokeWidth)
                        drawLine(col, Offset(0f, 0f), Offset(cLen, 0f), boxStrokeWidth)
                        drawLine(col, Offset(size.width, cLen), Offset(size.width, 0f), boxStrokeWidth)
                        drawLine(col, Offset(size.width - cLen, 0f), Offset(size.width, 0f), boxStrokeWidth)
                        drawLine(col, Offset(0f, size.height - cLen), Offset(0f, size.height), boxStrokeWidth)
                        drawLine(col, Offset(0f, size.height), Offset(cLen, size.height), boxStrokeWidth)
                        drawLine(col, Offset(size.width, size.height - cLen), Offset(size.width, size.height), boxStrokeWidth)
                        drawLine(col, Offset(size.width - cLen, size.height), Offset(size.width, size.height), boxStrokeWidth)

                        drawCircle(boxColor, 3.dp.toPx(), Offset(size.width / 2, size.height / 2), alpha = alpha)
                    }
            )

            Text(
                "● ${detectedItems.last().label.uppercase()}",
                color = Color.White.copy(alpha = flashAnim),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInsetDp + 8.dp)
                    .background(boxColor.copy(0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 4.dp, top = topInsetDp, bottom = bottomInsetDp)
                .drawBehind {
                    val bLen = 42.dp.toPx()
                    val sw = 2.6f
                    val r = 12.dp.toPx()
                    val col = camColor.copy(alpha = pulse)

                    drawLine(col, Offset(0f, r + bLen), Offset(0f, r), sw)
                    drawArc(col, 180f, 90f, false, Offset(0f, 0f), Size(r * 2, r * 2), style = Stroke(sw))
                    drawLine(col, Offset(r, 0f), Offset(r + bLen, 0f), sw)

                    drawLine(col, Offset(size.width, r + bLen), Offset(size.width, r), sw)
                    drawArc(col, 270f, 90f, false, Offset(size.width - r * 2, 0f), Size(r * 2, r * 2), style = Stroke(sw))
                    drawLine(col, Offset(size.width - r, 0f), Offset(size.width - r - bLen, 0f), sw)

                    drawLine(col, Offset(0f, size.height - r - bLen), Offset(0f, size.height - r), sw)
                    drawArc(col, 90f, 90f, false, Offset(0f, size.height - r * 2), Size(r * 2, r * 2), style = Stroke(sw))
                    drawLine(col, Offset(r, size.height), Offset(r + bLen, size.height), sw)

                    drawLine(col, Offset(size.width, size.height - r - bLen), Offset(size.width, size.height - r), sw)
                    drawArc(col, 0f, 90f, false, Offset(size.width - r * 2, size.height - r * 2), Size(r * 2, r * 2), style = Stroke(sw))
                    drawLine(col, Offset(size.width - r, size.height), Offset(size.width - r - bLen, size.height), sw)

                    val ly = size.height * scanY
                    drawLine(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, camColor.copy(0.8f), camColor, camColor.copy(0.8f), Color.Transparent)
                        ),
                        Offset(0f, ly),
                        Offset(size.width, ly),
                        1.6f
                    )
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, camColor.copy(0.22f), Color.Transparent)),
                        Offset(0f, ly),
                        Offset(size.width, ly),
                        10f
                    )
                }
        )

        Text(
            "Hold steady · one item at a time",
            color = whiteN.copy(alpha = 0.7f),
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 128.dp)
                .background(Color.Black.copy(0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        IconButton(
            onClick = {
                stopScanningNow()
                navController.popBackStack()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 10.dp, bottom = 15.dp)
                .size(30.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = whiteN,
                modifier = Modifier.size(30.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 15.dp, top = 55.dp)
                .size(18.dp)
                .drawBehind {
                    drawCircle(camColor, radius = size.minDimension / 2f, alpha = pulse * 0.55f)
                    drawCircle(camColor, radius = size.minDimension * 0.36f, alpha = pulse * 0.8f)
                    drawCircle(camColor, radius = size.minDimension * 0.22f)
                }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomInsetDp + 10.dp, end = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(orangeN.copy(alpha = 0.18f))
                .border(1.dp, orangeN.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .clickable(remember { MutableInteractionSource() }, null) { showComplaintSheet = true }
                .padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Warning, null, tint = orangeN, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                "REPORT",
                color = orangeN,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .padding(bottom = 20.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(borderN, RoundedCornerShape(2.dp))
            )

            // Action buttons row: Camera switch FAB + Generate QR pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera flip FAB
                androidx.compose.material3.IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier
                        .size(52.dp)
                        .background(surfAltN, CircleShape)
                        .border(1.dp, borderN, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera_switch),
                        contentDescription = "Flip camera",
                        tint = whiteN,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Generate QR button — fills remaining width
                Button(
                    onClick = {
                        Log.d(TAG, "Generate QR tapped, items=${detectedItems.size}")
                        isScanningRef.set(false)
                        showReviewSheet = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = redN),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_qr_code),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generate QR", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(
            visible = showReviewSheet,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
        ) {
            ReviewSheet(
                items = detectedItems,
                onConfirm = {
                    if (hasNavigatedRef.getAndSet(true)) return@ReviewSheet
                    Log.d(TAG, "Review confirmed, writing to Supabase then local DB")
                    stopScanningNow()
                    showReviewSheet = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            // Generate a unique box name
                            val existingNames = repository.getAllBoxNames().toSet()
                            val boxName = BoxNameGenerator.generateUnique(existingNames)

                            // 1. Write to Supabase first — throws on network/server error
                            supabaseRepo.saveBoxWithItems(boxName, detectedItems.toList())

                            // 2. Only persist locally after Supabase succeeds
                            val boxId = repository.createBox(boxName)
                            repository.saveItems(boxId, detectedItems.toList())

                            withContext(Dispatchers.Main) {
                                navController.navigate("qr_display_screen/$boxId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Supabase save failed: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                hasNavigatedRef.set(false)
                                saveError = "Could not save box — check your internet connection."
                            }
                        }
                    }
                },
                onDismiss = {
                    showReviewSheet = false
                    isScanningRef.set(true)
                }
            )
        }

        AnimatedVisibility(
            visible = showComplaintSheet,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
        ) {
            ComplaintSheet(onDismiss = { showComplaintSheet = false })
        }

        // ── Save-error dialog (shown when Supabase write fails) ─────────────
        if (saveError != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    saveError = null
                    isScanningRef.set(true)
                },
                title = {
                    Text("Save Failed", color = whiteN, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        saveError ?: "",
                        color = mutedN,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            saveError = null
                            isScanningRef.set(true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = redN)
                    ) {
                        Text("OK", color = Color.White)
                    }
                },
                containerColor = surfN,
                titleContentColor = whiteN
            )
        }
    }
}

/** Returns true if any new item was added or an existing item's count increased. */
private fun mergeDetections(current: SnapshotStateList<DetectedItem>, fresh: List<DetectedItem>): Boolean {
    var changed = false
    fresh.forEach { newItem ->
        val idx = current.indexOfFirst { it.label == newItem.label }
        if (idx >= 0) {
            if (newItem.count > current[idx].count) {
                current[idx] = newItem
                changed = true
            }
        } else {
            current.add(newItem)
            changed = true
        }
    }
    return changed
}

@Composable
fun ReviewSheet(
    items: SnapshotStateList<DetectedItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Animate scrim alpha and sheet scale/alpha on first composition
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scrimAlpha  by animateFloatAsState(if (entered) 0.6f else 0f,  tween(280), label = "scrim")
    val sheetScale  by animateFloatAsState(if (entered) 1f  else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "scale")
    val sheetAlpha  by animateFloatAsState(if (entered) 1f  else 0f,  tween(220), label = "alpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .scale(sheetScale)
                .alpha(sheetAlpha)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(borderN, RoundedCornerShape(2.dp))
            )
            Text("Are you sure?", color = whiteN, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Review quantities before generating QR",
                color = mutedN,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfAltN)
                    .border(1.dp, borderN, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (items.isEmpty()) {
                    Text(
                        "No items detected yet.\nYou can still generate a QR for an empty box.",
                        color = mutedN,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    items.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = borderN)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.label, color = whiteN, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SmallQtyButton("−") {
                                    if (item.count > 1) items[i] = item.copy(count = item.count - 1)
                                    else items.removeAt(i)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("${item.count}", color = whiteN, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(10.dp))
                                SmallQtyButton("+", active = true) { items[i] = item.copy(count = item.count + 1) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val reviewTotal: Int = items.sumOf { it.count }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total items", color = mutedN, fontSize = 13.sp)
                Text("$reviewTotal", color = tealN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = redN)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Finalize", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SmallQtyButton(text: String, active: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text,
        color = whiteN,
        fontSize = 16.sp,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (active) tealN.copy(alpha = 0.20f) else surfN)
            .border(1.dp, if (active) tealN.copy(alpha = 0.55f) else borderN, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .wrapContentSize(Alignment.Center)
    )
}

@Composable
fun ComplaintSheet(onDismiss: () -> Unit) {
    val reasons = listOf("Item damaged", "Wrong item", "Torn / worn out", "Missing label", "Other")
    var selected  by remember { mutableStateOf<String?>(null) }
    var notes     by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scrimAlpha by animateFloatAsState(if (entered) 0.6f else 0f, tween(280), label = "cScrim")
    val sheetScale by animateFloatAsState(if (entered) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "cScale")
    val sheetAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(220), label = "cAlpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .scale(sheetScale)
                .alpha(sheetAlpha)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(borderN, RoundedCornerShape(2.dp))
            )

            if (submitted) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(tealN.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, tealN.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = tealN, modifier = Modifier.size(28.dp))
                    }
                    Text("Report Submitted", color = whiteN, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Your complaint has been logged.", color = mutedN, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = redN)
                    ) { Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = orangeN, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Report an Issue", color = whiteN, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Select the issue type below.",
                    color = mutedN,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasons.forEach { reason ->
                        val isSelected = selected == reason
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) orangeN.copy(alpha = 0.12f) else surfAltN)
                                .border(
                                    1.dp,
                                    if (isSelected) orangeN.copy(alpha = 0.6f) else borderN,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(remember { MutableInteractionSource() }, null) { selected = reason }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .border(1.5.dp, if (isSelected) orangeN else mutedN, CircleShape)
                                    .background(if (isSelected) orangeN else Color.Transparent, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                reason,
                                color = if (isSelected) whiteN else mutedN,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { value -> notes = value },
                    placeholder = { Text("Additional notes (optional)…", color = mutedN, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tealN,
                        unfocusedBorderColor = borderN,
                        focusedTextColor = whiteN,
                        unfocusedTextColor = whiteN,
                        cursorColor = tealN,
                        focusedContainerColor = surfAltN,
                        unfocusedContainerColor = surfAltN
                    ),
                    maxLines = 3
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { if (selected != null) submitted = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = orangeN,
                        disabledContainerColor = orangeN.copy(alpha = 0.3f)
                    ),
                    enabled = selected != null
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit Report", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderN),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = mutedN)
                ) { Text("Cancel", fontSize = 14.sp) }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}