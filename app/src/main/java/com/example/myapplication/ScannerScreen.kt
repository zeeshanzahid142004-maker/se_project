package com.example.myapplication

import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.atomic.AtomicBoolean

private val tealS    = Color(0xFF2DD4BF)
private val redS     = Color(0xFFE53E3E)
private val surfS    = Color(0xFF161B22)
private val surfAltS = Color(0xFF1C2333)
private val borderS  = Color(0xFF30363D)
private val mutedS   = Color(0xFF8B949E)
private val whiteS   = Color(0xFFF0F6FC)


@kotlin.OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(navController: NavController) {

    // ── Runtime camera permission ──────────────────────────────────────────
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    when {
        cameraPermission.status.isGranted -> {
            // Permission granted — show the real scanner
            ScannerContent(navController = navController)
        }
        cameraPermission.status.shouldShowRationale -> {
            // User denied once — explain why we need it
            PermissionRationaleScreen(
                onRequest = { cameraPermission.launchPermissionRequest() },
                onBack    = { navController.popBackStack() }
            )
        }
        else -> {
            // First request or permanently denied
            PermissionRationaleScreen(
                onRequest = { cameraPermission.launchPermissionRequest() },
                onBack    = { navController.popBackStack() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Permission rationale screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PermissionRationaleScreen(onRequest: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Camera icon placeholder
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tealS.copy(alpha = 0.12f))
                    .border(1.dp, tealS.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📷", fontSize = 32.sp)
            }

            Text(
                "Camera access needed",
                color = whiteS,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "BoxScan needs camera access to scan QR codes and identify box contents.",
                color = mutedS,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = redS)
            ) {
                Text("Grant Camera Access", color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderS),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = mutedS)
            ) {
                Text("Go Back", fontSize = 15.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Actual scanner UI — only rendered once permission is granted
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalGetImage::class)
@Composable
private fun ScannerContent(navController: NavController) {

    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var scannedValue by remember { mutableStateOf<String?>(null) }
    var navigated    by remember { mutableStateOf(false) }

    // Stop the ML Kit analyzer as soon as a code is found, so the camera
    // stops processing frames and the UI stays stable.
    val isScanningRef = remember { AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { isScanningRef.set(false) } }

    val inf = rememberInfiniteTransition(label = "scan")
    val scanY by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanY"
    )
    val pulse by inf.animateFloat(
        0.1f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val barcodeScanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    DisposableEffect(Unit) { onDispose { barcodeScanner.close() } }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080C10))) {

        // ── CameraX PreviewView ────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val executor = ContextCompat.getMainExecutor(ctx)
                // Dedicated background executor for image analysis so bitmap
                // conversion and ML Kit scanning never run on the main thread.
                val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && isScanningRef.get()) {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val isRotated = rotation == 90 || rotation == 270

                                    val imgW = if (isRotated) mediaImage.height else mediaImage.width
                                    val imgH = if (isRotated) mediaImage.width  else mediaImage.height

                                    val viewW = previewView.width.takeIf  { it > 0 } ?: imgW
                                    val viewH = previewView.height.takeIf { it > 0 } ?: imgH

                                    val boxPx  = 260f * context.resources.displayMetrics.density
                                    val scaleX = imgW.toFloat() / viewW.toFloat()
                                    val scaleY = imgH.toFloat() / viewH.toFloat()

                                    val cropW = (boxPx * scaleX).toInt().coerceAtMost(imgW)
                                    val cropH = (boxPx * scaleY).toInt().coerceAtMost(imgH)
                                    val cropX = ((imgW - cropW) / 2).coerceAtLeast(0)
                                    val cropY = ((imgH - cropH) / 2).coerceAtLeast(0)

                                    // Convert YUV ImageProxy → Bitmap, rotate, crop
                                    val fullBitmap = imageProxy.toBitmap()
                                    val matrix = android.graphics.Matrix().apply {
                                        postRotate(rotation.toFloat())
                                    }
                                    val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                        fullBitmap, 0, 0,
                                        fullBitmap.width, fullBitmap.height,
                                        matrix, true
                                    )
                                    val croppedBitmap = android.graphics.Bitmap.createBitmap(
                                        rotatedBitmap, cropX, cropY, cropW, cropH
                                    )

                                    val inputImage = com.google.mlkit.vision.common.InputImage
                                        .fromBitmap(croppedBitmap, 0)

                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            if (barcodes.isNotEmpty() && isScanningRef.get()) {
                                                val raw = barcodes.first().rawValue
                                                if (!raw.isNullOrBlank()) {
                                                    isScanningRef.set(false)  // stop scanning — QR found
                                                    scannedValue = raw
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            croppedBitmap.recycle()
                                            rotatedBitmap.recycle()
                                            fullBitmap.recycle()
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    }
                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Square vignette with rounded corners matching the bracket corners
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val boxSize = 260.dp.toPx()
                    val cornerR = 10.dp.toPx()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val half = boxSize / 2f
                    val dark = androidx.compose.ui.graphics.Color(0xDD000000)

                    // top strip — full width
                    drawRect(dark, Offset(0f, 0f), Size(size.width, cy - half))
                    // bottom strip — full width
                    drawRect(dark, Offset(0f, cy + half), Size(size.width, size.height - (cy + half)))
                    // left strip — between top and bottom strips
                    drawRect(dark, Offset(0f, cy - half), Size(cx - half, boxSize))
                    // right strip — between top and bottom strips
                    drawRect(dark, Offset(cx + half, cy - half), Size(size.width - (cx + half), boxSize))

                    // rounded corners — draw 4 small filled corner wedges to round the cutout
                    // top-left corner fill
                    drawRect(dark, Offset(cx - half, cy - half), Size(cornerR, cornerR))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent,
                        radius = cornerR,
                        center = Offset(cx - half + cornerR, cy - half + cornerR),
                        blendMode = BlendMode.Clear)

                    // top-right
                    drawRect(dark, Offset(cx + half - cornerR, cy - half), Size(cornerR, cornerR))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent,
                        radius = cornerR,
                        center = Offset(cx + half - cornerR, cy - half + cornerR),
                        blendMode = BlendMode.Clear)

                    // bottom-left
                    drawRect(dark, Offset(cx - half, cy + half - cornerR), Size(cornerR, cornerR))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent,
                        radius = cornerR,
                        center = Offset(cx - half + cornerR, cy + half - cornerR),
                        blendMode = BlendMode.Clear)

                    // bottom-right
                    drawRect(dark, Offset(cx + half - cornerR, cy + half - cornerR), Size(cornerR, cornerR))
                    drawCircle(androidx.compose.ui.graphics.Color.Transparent,
                        radius = cornerR,
                        center = Offset(cx + half - cornerR, cy + half - cornerR),
                        blendMode = BlendMode.Clear)
                }
        )

        // ── Viewfinder + scan line ─────────────────────────────────────────
        if (scannedValue == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(260.dp)          // ← square, fixed size
                    .drawBehind {
                        val bLen = 26.dp.toPx()
                        val sw   = 2.4f
                        val r    = 10.dp.toPx()
                        val col  = tealS.copy(alpha = pulse)

                        // top-left
                        drawLine(col, Offset(0f, r + bLen), Offset(0f, r), sw)
                        drawArc(col, 180f, 90f, false, Offset(0f, 0f), Size(r*2, r*2), style = Stroke(sw))
                        drawLine(col, Offset(r, 0f), Offset(r + bLen, 0f), sw)
                        // top-right
                        drawLine(col, Offset(size.width, r + bLen), Offset(size.width, r), sw)
                        drawArc(col, 270f, 90f, false, Offset(size.width - r*2, 0f), Size(r*2, r*2), style = Stroke(sw))
                        drawLine(col, Offset(size.width - r, 0f), Offset(size.width - r - bLen, 0f), sw)
                        // bottom-left
                        drawLine(col, Offset(0f, size.height - r - bLen), Offset(0f, size.height - r), sw)
                        drawArc(col, 90f, 90f, false, Offset(0f, size.height - r*2), Size(r*2, r*2), style = Stroke(sw))
                        drawLine(col, Offset(r, size.height), Offset(r + bLen, size.height), sw)
                        // bottom-right
                        drawLine(col, Offset(size.width, size.height - r - bLen), Offset(size.width, size.height - r), sw)
                        drawArc(col, 0f, 90f, false, Offset(size.width - r*2, size.height - r*2), Size(r*2, r*2), style = Stroke(sw))
                        drawLine(col, Offset(size.width - r, size.height), Offset(size.width - r - bLen, size.height), sw)

                        // Scan line
                        val ly = size.height * scanY
                        drawLine(
                            Brush.horizontalGradient(
                                0f to Color.Transparent, 0.3f to tealS.copy(0.85f),
                                0.5f to tealS, 0.7f to tealS.copy(0.85f), 1f to Color.Transparent
                            ),
                            Offset(0f, ly), Offset(size.width, ly), 1.6f
                        )
                        drawLine(
                            Brush.horizontalGradient(
                                0f to Color.Transparent, 0.5f to tealS.copy(0.25f), 1f to Color.Transparent
                            ),
                            Offset(0f, ly), Offset(size.width, ly), 10f
                        )
                    }
            )

            Text(
                "Align BoxScan QR within the frame",
                color = mutedS, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 155.dp)   // 130 (half of 260dp box) + 25dp gap
                    .fillMaxWidth(0.7f)
            )
        }

        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.size(38.dp).background(surfS.copy(alpha = 0.88f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = whiteS, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Scan QR Code", color = whiteS, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Look up a box", color = mutedS, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            // Pulsating dot — teal while scanning, green when found
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .drawBehind {
                        val dotColor = if (scannedValue == null) tealS else Color(0xFF22C55E)
                        // large outer ring — very visible pulse
                        drawCircle(dotColor, radius = size.minDimension / 2f, alpha = pulse * 0.55f)
                        // mid ring
                        drawCircle(dotColor, radius = size.minDimension * 0.36f, alpha = pulse * 0.8f)
                        // solid core
                        drawCircle(dotColor, radius = size.minDimension * 0.22f)
                    }
            )
        }

        // ── Bottom panel ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfS)
                .padding(bottom = 36.dp)
        ) {
            Box(modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 20.dp)
                .width(36.dp).height(4.dp)
                .background(borderS, RoundedCornerShape(2.dp)))

            AnimatedContent(
                targetState = scannedValue,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 4 })
                        .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 })
                },
                label = "scannerBottomContent"
            ) { value ->
            if (value == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("AWAITING SCAN", color = mutedS, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfAltS)
                            .border(1.dp, borderS, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Point camera at a BoxScan QR code",
                            color = mutedS, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                val displayId = remember(value) {
                    val safe = value ?: ""
                    safe.substringAfter("\"boxId\":\"")
                        .substringBefore("\"")
                        .ifBlank { safe }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BOX FOUND", color = tealS, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(displayId, color = mutedS, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(surfAltS)
                                .padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Spacer(Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfAltS)
                            .border(1.dp, borderS, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ScanInfoRow("Box ID",  displayId)
                        HorizontalDivider(color = borderS)
                        ScanInfoRow("Status",  "In Storage", highlight = true)
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!navigated) {
                                navigated = true
                                navController.navigate("qr_display_screen")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = redS)
                    ) {
                        Text("View Full Details", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ScanInfoRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = mutedS, fontSize = 12.sp)
        Text(value,
            color = if (highlight) tealS else whiteS,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal)
    }
}