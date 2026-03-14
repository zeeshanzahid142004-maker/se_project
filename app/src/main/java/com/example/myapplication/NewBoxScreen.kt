package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

private const val TAG = "NewBoxScreen"

private val tealN    = Color(0xFF2DD4BF)
private val redN     = Color(0xFFE53E3E)
private val orangeN  = Color(0xFFF97316)
private val surfN    = Color(0xFF161B22)
private val surfAltN = Color(0xFF1C2333)
private val borderN  = Color(0xFF30363D)
private val mutedN   = Color(0xFF8B949E)
private val whiteN   = Color(0xFFF0F6FC)

// ─────────────────────────────────────────────────────────────────────────────
//  NewBoxScreen
// ─────────────────────────────────────────────────────────────────────────────
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
        // Show a simple permission screen instead of crashing
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF080C10)),
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

// ─────────────────────────────────────────────────────────────────────────────
//  NewBoxContent — only reached when camera permission is granted
// ─────────────────────────────────────────────────────────────────────────────
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun NewBoxContent(navController: androidx.navigation.NavController) {

    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val detectedItems = remember { mutableStateListOf<DetectedItem>() }
    var showReviewSheet    by remember { mutableStateOf(false) }
    var showComplaintSheet by remember { mutableStateOf(false) }

    // ── Safely init YoloDetector — null if tflite asset missing ──────────
    val detector = remember {
        try {
            Log.d(TAG, "YoloDetector: loading model from assets")
            YoloDetector(context).also {
                Log.d(TAG, "YoloDetector: loaded OK")
            }
        } catch (e: Exception) {
            Log.e(TAG, "YoloDetector: FAILED to load — ${e.message}", e)
            null   // null = no inference, camera still shows
        }
    }
    DisposableEffect(Unit) { onDispose { detector?.close() } }

    var lastInferenceMs by remember { mutableStateOf(0L) }
    val previewViewRef  = remember { mutableStateOf<PreviewView?>(null) }

    val inf = rememberInfiniteTransition(label = "anim")
    val scanY by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanY"
    )
    val pulse by inf.animateFloat(
        initialValue = 0.1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080C10))) {

        // ── CameraX Preview ───────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                Log.d(TAG, "AndroidView: creating PreviewView")
                val pv = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef.value = pv

                val mainExecutor = ContextCompat.getMainExecutor(ctx)
                // Dedicated single-thread executor for image analysis — never blocks UI
                val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
                val future = ProcessCameraProvider.getInstance(ctx)

                future.addListener({
                    try {
                        val provider = future.get()
                        Log.d(TAG, "CameraProvider: obtained")

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(pv.surfaceProvider)
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { ia ->
                                ia.setAnalyzer(analysisExecutor) { imageProxy ->

                                    // Throttle to every 800ms
                                    val now = System.currentTimeMillis()
                                    if (now - lastInferenceMs < 800L) {
                                        imageProxy.close(); return@setAnalyzer
                                    }
                                    lastInferenceMs = now

                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        Log.w(TAG, "Analyzer: mediaImage is null")
                                        imageProxy.close(); return@setAnalyzer
                                    }

                                    // If no detector (tflite missing), just show camera
                                    if (detector == null) {
                                        Log.w(TAG, "Analyzer: detector is null, skipping inference")
                                        imageProxy.close(); return@setAnalyzer
                                    }

                                    val rotation  = imageProxy.imageInfo.rotationDegrees
                                    val isRotated = rotation == 90 || rotation == 270
                                    val imgW = if (isRotated) mediaImage.height else mediaImage.width
                                    val imgH = if (isRotated) mediaImage.width  else mediaImage.height

                                    val pv2   = previewViewRef.value
                                    val viewW = pv2?.width?.takeIf  { it > 0 } ?: imgW
                                    val viewH = pv2?.height?.takeIf { it > 0 } ?: imgH

                                    val density = ctx.resources.displayMetrics.density
                                    val hPadPx  = 28f * density
                                    val tPadPx  = 120f * density  // top bar height
                                    val bPadPx  = 280f * density  // bottom sheet height — key fix

                                    val cropW = ((viewW - 2 * hPadPx) * (imgW.toFloat() / viewW)).toInt().coerceIn(1, imgW)
                                    val cropH = ((viewH - tPadPx - bPadPx) * (imgH.toFloat() / viewH)).toInt().coerceIn(1, imgH)
                                    val cropX = ((imgW - cropW) / 2).coerceAtLeast(0)
                                    val cropY = (tPadPx * (imgH.toFloat() / viewH)).toInt().coerceAtLeast(0)

                                    Log.d(TAG, "Crop: imgW=$imgW imgH=$imgH cropX=$cropX cropY=$cropY cropW=$cropW cropH=$cropH")

                                    try {
                                        val full    = imageProxy.toBitmap()
                                        val matrix  = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                        val rotated = android.graphics.Bitmap.createBitmap(full, 0, 0, full.width, full.height, matrix, true)
                                        val safeCropW = cropW.coerceAtMost(rotated.width  - cropX).coerceAtLeast(1)
                                        val safeCropH = cropH.coerceAtMost(rotated.height - cropY).coerceAtLeast(1)
                                        val cropped = android.graphics.Bitmap.createBitmap(rotated, cropX, cropY, safeCropW, safeCropH)

                                        Log.d(TAG, "Bitmap ready: ${cropped.width}x${cropped.height}, running inference")

                                        // Already on analysisExecutor (background) — call detect directly
                                        try {
                                            val results = detector.detect(cropped)
                                            Log.d(TAG, "Inference results: $results")
                                            mainExecutor.execute {
                                                mergeDetections(detectedItems, results)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Inference failed: ${e.message}", e)
                                        } finally {
                                            cropped.recycle()
                                            rotated.recycle()
                                            full.recycle()
                                            imageProxy.close()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Bitmap crop failed: ${e.message}", e)
                                        imageProxy.close()
                                    }
                                }
                            }

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, analysis
                        )
                        Log.d(TAG, "Camera bound to lifecycle OK")

                    } catch (e: Exception) {
                        Log.e(TAG, "CameraProvider setup failed: ${e.message}", e)
                    }
                }, mainExecutor)
                pv
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Vignette silhouette with rounded corners on the clear zone ────
        val topInsetDp    = 68.dp   // single top bar row height
        val bottomInsetDp = 280.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val t    = topInsetDp.toPx()
                    val b    = bottomInsetDp.toPx()
                    val cr   = 20.dp.toPx()   // corner radius of the clear window
                    val dark = androidx.compose.ui.graphics.Color(0xCC000000)

                    // top strip
                    drawRect(dark, Offset(0f, 0f), Size(size.width, t))
                    // bottom strip
                    drawRect(dark, Offset(0f, size.height - b), Size(size.width, b))

                    // round the top-left corner of the clear zone
                    drawRect(dark, Offset(0f, t), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr,
                        Offset(cr, t + cr), blendMode = BlendMode.Clear)

                    // round the top-right corner
                    drawRect(dark, Offset(size.width - cr, t), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr,
                        Offset(size.width - cr, t + cr), blendMode = BlendMode.Clear)

                    // round the bottom-left corner
                    drawRect(dark, Offset(0f, size.height - b - cr), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr,
                        Offset(cr, size.height - b - cr), blendMode = BlendMode.Clear)

                    // round the bottom-right corner
                    drawRect(dark, Offset(size.width - cr, size.height - b - cr), Size(cr, cr))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent, cr,
                        Offset(size.width - cr, size.height - b - cr), blendMode = BlendMode.Clear)
                }
        )

        // ── Detection wireframe — RED, tight centred box ──────────────────
        val flashAnim by rememberInfiniteTransition(label = "flash").animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "flash"
        )
        if (detectedItems.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInsetDp + 24.dp)
                    .padding(horizontal = 56.dp)
                    .size(210.dp, 190.dp)
                    .drawBehind {
                        val cLen = 30.dp.toPx()
                        val sw   = 2.4f
                        val col  = redN.copy(alpha = flashAnim)
                        // sharp corner brackets — red
                        drawLine(col, Offset(0f, cLen), Offset(0f, 0f), sw)
                        drawLine(col, Offset(0f, 0f), Offset(cLen, 0f), sw)
                        drawLine(col, Offset(size.width, cLen), Offset(size.width, 0f), sw)
                        drawLine(col, Offset(size.width - cLen, 0f), Offset(size.width, 0f), sw)
                        drawLine(col, Offset(0f, size.height - cLen), Offset(0f, size.height), sw)
                        drawLine(col, Offset(0f, size.height), Offset(cLen, size.height), sw)
                        drawLine(col, Offset(size.width, size.height - cLen), Offset(size.width, size.height), sw)
                        drawLine(col, Offset(size.width - cLen, size.height), Offset(size.width, size.height), sw)
                        // centre dot
                        drawCircle(redN, 3.dp.toPx(),
                            Offset(size.width / 2, size.height / 2), alpha = flashAnim)
                    }
            )
            // Label badge — white text, dark pill
            Text(
                "● ${detectedItems.last().label.uppercase()}",
                color = Color.White.copy(alpha = flashAnim),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInsetDp + 8.dp)
                    .background(redN.copy(0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // ── Viewfinder brackets — edge to edge ────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 4.dp, top = topInsetDp, bottom = bottomInsetDp)
                .drawBehind {
                    val bLen = 42.dp.toPx()
                    val sw   = 2.6f
                    val r    = 12.dp.toPx()
                    val col  = tealN.copy(alpha = pulse)

                    drawLine(col, Offset(0f, r + bLen), Offset(0f, r), sw)
                    drawArc(col, 180f, 90f, false, Offset(0f, 0f), Size(r*2,r*2), style = Stroke(sw))
                    drawLine(col, Offset(r, 0f), Offset(r + bLen, 0f), sw)

                    drawLine(col, Offset(size.width, r + bLen), Offset(size.width, r), sw)
                    drawArc(col, 270f, 90f, false, Offset(size.width - r*2, 0f), Size(r*2,r*2), style = Stroke(sw))
                    drawLine(col, Offset(size.width - r, 0f), Offset(size.width - r - bLen, 0f), sw)

                    drawLine(col, Offset(0f, size.height - r - bLen), Offset(0f, size.height - r), sw)
                    drawArc(col, 90f, 90f, false, Offset(0f, size.height - r*2), Size(r*2,r*2), style = Stroke(sw))
                    drawLine(col, Offset(r, size.height), Offset(r + bLen, size.height), sw)

                    drawLine(col, Offset(size.width, size.height - r - bLen), Offset(size.width, size.height - r), sw)
                    drawArc(col, 0f, 90f, false, Offset(size.width - r*2, size.height - r*2), Size(r*2,r*2), style = Stroke(sw))
                    drawLine(col, Offset(size.width - r, size.height), Offset(size.width - r - bLen, size.height), sw)

                    val ly = size.height * scanY
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, tealN.copy(0.8f), tealN, tealN.copy(0.8f), Color.Transparent)),
                        Offset(0f, ly), Offset(size.width, ly), 1.6f
                    )
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, tealN.copy(0.22f), Color.Transparent)),
                        Offset(0f, ly), Offset(size.width, ly), 10f
                    )
                }
        )

        // Hint text
        Text(
            "Hold steady · one item at a time",
            color = whiteN.copy(alpha = 0.7f),
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 288.dp)
                .background(Color.Black.copy(0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        // ── Top bar ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: back + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(38.dp)
                        .background(surfN.copy(alpha = 0.88f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = whiteN, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("New Box", color = whiteN, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            // Right: REPORT + pulse dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(orangeN.copy(alpha = 0.15f))
                        .border(1.dp, orangeN.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable(remember { MutableInteractionSource() }, null) { showComplaintSheet = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = orangeN, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("REPORT", color = orangeN, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.size(22.dp).drawBehind {
                    drawCircle(tealN, radius = size.minDimension / 2f, alpha = pulse * 0.55f)
                    drawCircle(tealN, radius = size.minDimension * 0.36f, alpha = pulse * 0.8f)
                    drawCircle(tealN, radius = size.minDimension * 0.22f)
                })
            }
        }

        // ── Bottom sheet ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .padding(bottom = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp, bottom = 12.dp)
                    .width(36.dp).height(4.dp)
                    .background(borderN, RoundedCornerShape(2.dp))
            )
            Text("Point camera at items", color = mutedN, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DETECTED ITEMS", color = mutedN, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val total: Int = detectedItems.sumOf { it.count }
                Text("$total total", color = tealN, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(102.dp)   // ~1.4 items — clips second card to hint scrollability
                    .padding(horizontal = 20.dp)
            ) {
                if (detectedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(64.dp)
                            .border(1.dp, borderN, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Waiting for detection…", color = mutedN, fontSize = 13.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detectedItems.reversed().forEach { item: DetectedItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(surfAltN)
                                    .border(1.dp, borderN, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.label, color = whiteN, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text("Detected", color = mutedN, fontSize = 11.sp)
                                }
                                Text("×${item.count}", color = whiteN, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    Log.d(TAG, "Generate QR tapped, items=${detectedItems.size}")
                    showReviewSheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = redN),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                ),
                enabled = true
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate QR", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Overlays ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showReviewSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit  = slideOutVertically(targetOffsetY  = { it }) + fadeOut()
        ) {
            ReviewSheet(
                items     = detectedItems,
                onConfirm = {
                    Log.d(TAG, "Review confirmed, navigating to qr_display_screen")
                    showReviewSheet = false
                    navController.navigate("qr_display_screen")
                },
                onDismiss = { showReviewSheet = false }
            )
        }

        AnimatedVisibility(
            visible = showComplaintSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit  = slideOutVertically(targetOffsetY  = { it }) + fadeOut()
        ) {
            ComplaintSheet(onDismiss = { showComplaintSheet = false })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Merge detections — accumulating history
//  - New label seen for first time → add it
//  - Label seen again with higher count → update upward only
//  - Label disappears from frame → keep it (user can remove manually in review)
// ─────────────────────────────────────────────────────────────────────────────
private fun mergeDetections(current: SnapshotStateList<DetectedItem>, fresh: List<DetectedItem>) {
    fresh.forEach { newItem: DetectedItem ->
        val idx = current.indexOfFirst { it.label == newItem.label }
        if (idx >= 0) {
            // Only update if new count is higher — don't decrease when item leaves frame
            if (newItem.count > current[idx].count) {
                current[idx] = newItem
            }
        } else {
            // Brand new label — add to history
            current.add(newItem)
        }
    }
    // Never remove items — history persists until user clears in review sheet
}

// ─────────────────────────────────────────────────────────────────────────────
//  ReviewSheet
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ReviewSheet(
    items: SnapshotStateList<DetectedItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp)
                    .width(36.dp).height(4.dp)
                    .background(borderN, RoundedCornerShape(2.dp))
            )
            Text("Are you sure?", color = whiteN, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Review quantities before generating QR", color = mutedN, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

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
                    Text("No items detected yet.\nYou can still generate a QR for an empty box.",
                        color = mutedN, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    items.forEachIndexed { i: Int, item: DetectedItem ->
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
                Text("Generate QR", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SmallQtyButton
// ─────────────────────────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
//  ComplaintSheet
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComplaintSheet(onDismiss: () -> Unit) {
    val reasons  = listOf("Item damaged", "Wrong item", "Torn / worn out", "Missing label", "Other")
    var selected  by remember { mutableStateOf<String?>(null) }
    var notes     by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp)
                    .width(36.dp).height(4.dp)
                    .background(borderN, RoundedCornerShape(2.dp))
            )

            if (submitted) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(56.dp)
                            .background(tealN.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, tealN.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = tealN, modifier = Modifier.size(28.dp))
                    }
                    Text("Report Submitted", color = whiteN, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Your complaint has been logged.", color = mutedN, fontSize = 13.sp,
                        textAlign = TextAlign.Center)
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
                Text("Select the issue type below.", color = mutedN, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasons.forEach { reason: String ->
                        val isSelected = selected == reason
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) orangeN.copy(alpha = 0.12f) else surfAltN)
                                .border(1.dp, if (isSelected) orangeN.copy(alpha = 0.6f) else borderN, RoundedCornerShape(10.dp))
                                .clickable(remember { MutableInteractionSource() }, null) { selected = reason }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(16.dp)
                                    .border(1.5.dp, if (isSelected) orangeN else mutedN, CircleShape)
                                    .background(if (isSelected) orangeN else Color.Transparent, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(reason, color = if (isSelected) whiteN else mutedN, fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { value: String -> notes = value },
                    placeholder = { Text("Additional notes (optional)…", color = mutedN, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tealN, unfocusedBorderColor = borderN,
                        focusedTextColor = whiteN, unfocusedTextColor = whiteN,
                        cursorColor = tealN,
                        focusedContainerColor = surfAltN, unfocusedContainerColor = surfAltN
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