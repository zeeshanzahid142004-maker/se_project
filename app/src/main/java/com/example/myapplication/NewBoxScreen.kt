package com.example.myapplication

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.camera.core.*
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.BoxRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.github.jan.supabase.auth.auth
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NewBoxScreen"
private const val DEBUG_TAG = "[COMPLAINT_FLOW]"
private const val MODEL_INPUT_SIZE = 640f
private val detectionBoxColor = Color.Green

private data class SaveErrorInfo(
    val title: String = "Save Failed",
    val userMessage: String,
    val technicalDetails: String = "",
    val errorCode: String = ""
)

private val tealN    = Color(0xFF2DD4BF)
private val redN     = Color(0xFFE53E3E)
private val orangeN  = Color(0xFFF97316)
private val surfN    = Color(0xFF161B22)
private val surfAltN = Color(0xFF1C2333)
private val borderN  = Color(0xFF30363D)
private val mutedN   = Color(0xFF8B949E)
private val whiteN   = Color(0xFFF0F6FC)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewBoxScreen(navController: androidx.navigation.NavController) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }
    if (!cameraPermission.status.isGranted) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF080C10)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required", color = whiteN, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { cameraPermission.launchPermissionRequest() },
                    colors  = ButtonDefaults.buttonColors(containerColor = tealN)
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
    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val repository     = remember { BoxRepository(AppDatabase.getInstance(context)) }
    val supabaseRepo   = remember { com.example.myapplication.data.SupabaseRepository() }

    val detectedItems       = remember { mutableStateListOf<DetectedItem>() }
    var detectionBoxes      by remember { mutableStateOf<List<DetectionBox>>(emptyList()) }
    var showReviewSheet     by remember { mutableStateOf(false) }
    var showComplaintSheet  by remember { mutableStateOf(false) }
    var saveError           by remember { mutableStateOf<SaveErrorInfo?>(null) }
    var retryAction         by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cameraReady         by remember { mutableStateOf(false) }
    var isSaving            by remember { mutableStateOf(false) }
    var useFrontCamera      by remember { mutableStateOf(false) }
    var scanFlashActive     by remember { mutableStateOf(false) }
    var frameProjection     by remember { mutableStateOf<FrameProjection?>(null) }
    val isScanningRef       = remember { AtomicBoolean(true) }
    val hasNavigatedRef     = remember { AtomicBoolean(false) }
    var showScanRequiredSheet    by remember { mutableStateOf(false) }
    var showComplaintSuccessSheet by remember { mutableStateOf(false) }

    LaunchedEffect(useFrontCamera) { isScanningRef.set(true); cameraReady = false }

    var detector by remember { mutableStateOf<YoloDetector?>(null) }

    fun addItemToList(item: DetectedItem) {
        val index = detectedItems.indexOfFirst { it.label == item.label }
        if (index >= 0) detectedItems[index] = detectedItems[index].copy(count = detectedItems[index].count + item.count)
        else detectedItems.add(item)
    }

    fun launchSave(boxName: String, itemsSnapshot: List<DetectedItem>) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "launchSave: starting — boxName=$boxName items=${itemsSnapshot.size}")

                val boxId = repository.createBox(boxName)
                Log.d(TAG, "launchSave: box created — boxId=$boxId")

                repository.saveItems(boxId, itemsSnapshot)
                Log.d(TAG, "launchSave: items saved — navigating to qr_display_screen/$boxId")

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "launchSave: on Main thread — calling navigate")
                    navController.navigate("qr_display_screen/$boxId") {
                        popUpTo("new_box_screen") { inclusive = true }
                    }
                    Log.d(TAG, "launchSave: navigate called successfully")
                }

                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    val currentUserId = SupabaseModule.client.auth.currentUserOrNull()?.id
                    if (currentUserId == null) {
                        Log.w(TAG, "Supabase sync skipped: no authenticated user")
                        return@launch
                    }
                    try { supabaseRepo.saveBoxWithItems(boxName, itemsSnapshot, currentUserId) }
                    catch (e: Exception) { Log.e(TAG, "Supabase sync failed: ${e.message}", e) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "launchSave: CRASHED — ${e.message}", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "launchSave: showing error dialog")
                    hasNavigatedRef.set(false); isSaving = false
                    retryAction = { isSaving = true; launchSave(boxName, itemsSnapshot) }
                    saveError = SaveErrorInfo(
                        userMessage      = "Could not save the box locally. Please try again.",
                        technicalDetails = friendlyError(e),
                        errorCode        = classifyError(e)
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val d = YoloDetector(context)
                withContext(Dispatchers.Main) { detector = d }
            } catch (e: Exception) {
                Log.e("YOLO_DEBUG", "Model load CRASHED: ${e.message}", e)
            }
        }
    }

    val boxScaleAnim = remember { Animatable(1.0f) }

    LaunchedEffect(scanFlashActive) {
        if (!scanFlashActive) return@LaunchedEffect
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 90L, 60L, 130L), intArrayOf(0, 240, 0, 210), -1))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 90, 60, 130), -1)
        }
        boxScaleAnim.snapTo(1.0f)
        boxScaleAnim.animateTo(0.86f, animationSpec = tween(70, easing = FastOutSlowInEasing))
        boxScaleAnim.animateTo(1.10f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        boxScaleAnim.animateTo(1.0f,  animationSpec = tween(180))
        scanFlashActive = false
    }

    DisposableEffect(Unit) {
        onDispose { try { detector?.close() } catch (_: Exception) {} }
    }

    val inf   = rememberInfiniteTransition(label = "anim")
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

    val sideInsetDp   = 4.dp
    val topInsetDp    = 68.dp
    val bottomInsetDp = 120.dp

    // ── Detection outline state ───────────────────────────────────────────────
    val isDetecting   = detectionBoxes.isNotEmpty()
    val detectedLabel = detectionBoxes.firstOrNull()?.label ?: ""

    // TWEAK: idle stroke=1.8, detected stroke=4 — increase for thicker border
    val outlineStroke by animateFloatAsState(
        targetValue   = if (isDetecting) 4f else 1.8f,
        animationSpec = tween(120),
        label         = "stroke"
    )
    // TWEAK: idle padding=26dp (~85% coverage), detected=14dp (~90% coverage)
    // decrease either value to make the outline larger
    val outlinePadding by animateDpAsState(
        targetValue   = if (isDetecting) 14.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "padding"
    )
    // TWEAK: idle alpha=0.30 (dim), detected=1.0 (full brightness)
    val outlineAlpha by animateFloatAsState(
        targetValue   = if (isDetecting) 1f else 0.30f,
        animationSpec = tween(120),
        label         = "alpha"
    )
    val labelScale by animateFloatAsState(
        targetValue   = if (isDetecting) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label         = "labelScale"
    )

    val density          = LocalDensity.current
    val labelPaintOutline = remember {
        android.graphics.Paint().apply {
            color          = android.graphics.Color.BLACK
            isFakeBoldText = true
            isAntiAlias    = true
            textAlign      = android.graphics.Paint.Align.CENTER
        }
    }
    labelPaintOutline.textSize = with(density) { 13.sp.toPx() } * labelScale.coerceAtLeast(0.01f)

    val camColor = if (cameraReady) tealN else redN

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080C10))) {

        // ── Camera preview ────────────────────────────────────────────────────
        CameraPreview(
            useFrontCamera    = useFrontCamera,
            detectorProvider  = { detector },
            isScanningRef     = isScanningRef,
            sideInsetDp       = sideInsetDp,
            topInsetDp        = topInsetDp,
            bottomInsetDp     = bottomInsetDp,
            onCameraReady     = { cameraReady = it },
            onFrameProjection = { frameProjection = it },
            onBoxesDetected   = { detectionBoxes = it },
            onNewItems        = { newly ->
                newly.groupingBy { it.label }.eachCount()
                    .forEach { (label, n) -> addItemToList(DetectedItem(label, n)) }
                scanFlashActive = true
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Bounding box overlay ──────────────────────────────────────────────
        BoxOverlay(
            boxes           = detectionBoxes,
            frameProjection = frameProjection,
            scaleValue      = boxScaleAnim.value,
            modifier        = Modifier.fillMaxSize()
        )

        // ── Vignette overlay ──────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                val t    = topInsetDp.toPx()
                val b    = bottomInsetDp.toPx()
                val cr   = 20.dp.toPx()
                val dark = Color(0xCC000000)
                drawRect(dark, Offset(0f, 0f), Size(size.width, t))
                drawRect(dark, Offset(0f, size.height - b), Size(size.width, b))
                drawRect(dark, Offset(0f, t), Size(cr, cr))
                drawCircle(Color.Transparent, cr, Offset(cr, t + cr), blendMode = BlendMode.Clear)
                drawRect(dark, Offset(size.width - cr, t), Size(cr, cr))
                drawCircle(Color.Transparent, cr, Offset(size.width - cr, t + cr), blendMode = BlendMode.Clear)
                drawRect(dark, Offset(0f, size.height - b - cr), Size(cr, cr))
                drawCircle(Color.Transparent, cr, Offset(cr, size.height - b - cr), blendMode = BlendMode.Clear)
                drawRect(dark, Offset(size.width - cr, size.height - b - cr), Size(cr, cr))
                drawCircle(Color.Transparent, cr, Offset(size.width - cr, size.height - b - cr), blendMode = BlendMode.Clear)
            }
        )


        // ── Corner brackets + scan line ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sideInsetDp, end = sideInsetDp, top = topInsetDp, bottom = bottomInsetDp)
                .drawBehind {
                    val bLen = 42.dp.toPx()
                    val sw   = 2.6f
                    val r    = 12.dp.toPx()
                    val col  = camColor.copy(alpha = pulse)

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
                        Brush.horizontalGradient(listOf(Color.Transparent, camColor.copy(0.8f), camColor, camColor.copy(0.8f), Color.Transparent)),
                        Offset(0f, ly), Offset(size.width, ly), 1.6f
                    )
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, camColor.copy(0.22f), Color.Transparent)),
                        Offset(0f, ly), Offset(size.width, ly), 10f
                    )
                }
        )

        // ── Hold steady hint ──────────────────────────────────────────────────
        Text(
            "Hold steady · one item at a time",
            color    = whiteN.copy(alpha = 0.7f),
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 128.dp)
                .background(Color.Black.copy(0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        // ── Back button ───────────────────────────────────────────────────────
        IconButton(
            onClick  = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 10.dp, bottom = 15.dp)
                .size(30.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = whiteN, modifier = Modifier.size(30.dp))
        }

        // ── Camera-ready dot ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 15.dp, top = 55.dp)
                .size(18.dp)
                .drawBehind {
                    drawCircle(camColor, radius = size.minDimension / 2f,    alpha = pulse * 0.55f)
                    drawCircle(camColor, radius = size.minDimension * 0.36f, alpha = pulse * 0.8f)
                    drawCircle(camColor, radius = size.minDimension * 0.22f)
                }
        )

        // ── Report button ─────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomInsetDp + 10.dp, end = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(orangeN.copy(alpha = 0.18f))
                .border(1.dp, orangeN.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .clickable(remember { MutableInteractionSource() }, null) {
                    if (detectedItems.isEmpty()) {
                        showScanRequiredSheet = true
                    } else {
                        isScanningRef.set(false); showComplaintSheet = true
                    }
                }
                .padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Warning, null, tint = orangeN, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text("REPORT", color = orangeN, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }

        // ── Bottom panel ──────────────────────────────────────────────────────
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
                    .padding(top = 12.dp, bottom = 14.dp)
                    .width(36.dp).height(4.dp)
                    .background(borderN, RoundedCornerShape(2.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(
                    onClick  = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier.size(52.dp).background(surfAltN, CircleShape).border(1.dp, borderN, CircleShape)
                ) {
                    Icon(painterResource(id = R.drawable.ic_camera_switch), contentDescription = "Flip camera", tint = whiteN, modifier = Modifier.size(24.dp))
                }
                Button(
                    onClick  = {
                        if (detectedItems.isEmpty()) {
                            showScanRequiredSheet = true
                        } else {
                            isScanningRef.set(false); showReviewSheet = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp).shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = redN),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_qr_code), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate QR", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Review sheet ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showReviewSheet,
            enter   = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialOffsetY = { it }) + fadeIn(tween(220)),
            exit    = slideOutVertically(animationSpec = tween(280, easing = FastOutSlowInEasing), targetOffsetY = { it }) + fadeOut(tween(220, easing = FastOutSlowInEasing))
        ) {
            ReviewSheet(
                items     = detectedItems,
                isSaving  = isSaving,
                onConfirm = {
                    if (hasNavigatedRef.getAndSet(true)) return@ReviewSheet
                    isSaving = true; showReviewSheet = false
                    scope.launch(Dispatchers.IO) {
                        val existingNames = repository.getAllBoxNames().toSet()
                        launchSave(BoxNameGenerator.generateUnique(existingNames), detectedItems.toList())
                    }
                },
                onDismiss = { showReviewSheet = false; isScanningRef.set(true) }
            )
        }

        // ── Complaint sheet ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showComplaintSheet,
            enter   = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialOffsetY = { it }) + fadeIn(tween(220)),
            exit    = slideOutVertically(animationSpec = tween(280, easing = FastOutSlowInEasing), targetOffsetY = { it }) + fadeOut(tween(220, easing = FastOutSlowInEasing))
        ) {
            ComplaintSheet(
                    onDismiss = { showComplaintSheet = false; isScanningRef.set(true) },
                    onSuccess = {
                        showComplaintSheet = false
                        showComplaintSuccessSheet = true
                    },
                    onSubmitReport = { complaintInfo ->
                        Log.d(DEBUG_TAG, "onSubmitReport invoked")
                        val userId = SupabaseModule.client.auth.currentUserOrNull()?.id
                        val item   = detectedItems.firstOrNull()
                        Log.d(DEBUG_TAG, "validation — userId present=${userId != null}, item=${item?.label}")
                        if (userId == null || item == null) {
                            Log.w(DEBUG_TAG, "Aborting: userId null=${userId == null}, item null=${item == null}")
                            false
                        } else {
                            try {
                                Log.d(DEBUG_TAG, "Calling supabaseRepo.submitItemComplaint")
                                withContext(Dispatchers.IO) {
                                    supabaseRepo.submitItemComplaint(userId, item, complaintInfo)
                                }
                                Log.d(DEBUG_TAG, "supabaseRepo.submitItemComplaint returned — success")
                                true
                            } catch (e: Exception) {
                                Log.e(DEBUG_TAG, "submitItemComplaint FAILED: ${e.message}", e)
                                false
                            }
                        }
                    }
                )
        }

        // ── Scan-required info sheet ──────────────────────────────────────────
        AnimatedVisibility(
            visible = showScanRequiredSheet,
            enter   = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialOffsetY = { it }) + fadeIn(tween(220)),
            exit    = slideOutVertically(animationSpec = tween(280, easing = FastOutSlowInEasing), targetOffsetY = { it }) + fadeOut(tween(220, easing = FastOutSlowInEasing))
        ) {
            InfoBottomSheet(
                title      = "Scan Required",
                message    = "You need to scan an item before proceeding.",
                buttonText = "Close",
                onDismiss  = { showScanRequiredSheet = false }
            )
        }

        // ── Complaint success info sheet ──────────────────────────────────────
        AnimatedVisibility(
            visible = showComplaintSuccessSheet,
            enter   = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialOffsetY = { it }) + fadeIn(tween(220)),
            exit    = slideOutVertically(animationSpec = tween(280, easing = FastOutSlowInEasing), targetOffsetY = { it }) + fadeOut(tween(220, easing = FastOutSlowInEasing))
        ) {
            InfoBottomSheet(
                title      = "Complaint Submitted",
                message    = "Your complaint has been successfully logged.",
                buttonText = "Okay",
                onDismiss  = { showComplaintSuccessSheet = false; isScanningRef.set(true) }
            )
        }

        // ── Save-error dialog ─────────────────────────────────────────────────
        if (saveError != null) {
            AlertDialog(
                onDismissRequest = { saveError = null; retryAction = null; isScanningRef.set(true) },
                title    = { Text(saveError!!.title, color = whiteN, fontWeight = FontWeight.Bold) },
                text     = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(saveError!!.userMessage, color = mutedN, fontSize = 14.sp, lineHeight = 20.sp)
                        if (saveError!!.technicalDetails.isNotBlank() || saveError!!.errorCode.isNotBlank()) {
                            HorizontalDivider(color = borderN)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (saveError!!.technicalDetails.isNotBlank()) {
                                    Text(saveError!!.technicalDetails, color = mutedN.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                                }
                                if (saveError!!.errorCode.isNotBlank()) {
                                    Text("Code: ${saveError!!.errorCode}", color = orangeN, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { saveError = null; retryAction = null; isScanningRef.set(true) }) {
                        Text("Dismiss", color = mutedN)
                    }
                },
                confirmButton = {
                    if (retryAction != null) {
                        Button(
                            onClick = {
                                val r = retryAction
                                saveError   = null
                                retryAction = null
                                hasNavigatedRef.set(true)
                                isSaving = true
                                r?.invoke()
                            },
                            colors  = ButtonDefaults.buttonColors(containerColor = redN)
                        ) { Text("Retry", color = Color.White) }
                    }
                },
                containerColor    = surfN,
                titleContentColor = whiteN
            )
        }
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

data class FrameProjection(
    val imageWidth: Int, val imageHeight: Int,
    val cropX: Int, val cropY: Int,
    val cropWidth: Int, val cropHeight: Int,
    val viewWidth: Int, val viewHeight: Int,
    val mirrored: Boolean
)

data class TrackedDetection(val label: String, val box: DetectionBox, val lastSeenMs: Long)
data class TemporalDebounceResult(val visibleBoxes: List<DetectionBox>, val newlyRegistered: List<DetectionBox>)
data class CooldownKey(val label: String, val cellX: Int, val cellY: Int)

// ── NMS + temporal debounce ───────────────────────────────────────────────────

private const val COOLDOWN_CLEANUP_MULTIPLIER = 2L
private const val COOLDOWN_SPATIAL_GRID_BINS  = 8f

fun applySameLabelNms(boxes: List<DetectionBox>, iouThreshold: Float): List<DetectionBox> {
    val sorted     = boxes.sortedByDescending { it.confidence }
    val suppressed = BooleanArray(sorted.size)
    val kept       = mutableListOf<DetectionBox>()
    for (i in sorted.indices) {
        if (suppressed[i]) continue
        kept.add(sorted[i])
        for (j in i + 1 until sorted.size)
            if (!suppressed[j] && sorted[i].label == sorted[j].label && detectionIoU(sorted[i], sorted[j]) >= iouThreshold)
                suppressed[j] = true
    }
    return kept
}

fun applyTemporalDebounce(
    boxes: List<DetectionBox>, tracks: MutableList<TrackedDetection>,
    cooldownMap: MutableMap<CooldownKey, Long>, nowMs: Long,
    iouThreshold: Float, cooldownMs: Long, staleTrackMs: Long
): TemporalDebounceResult {
    tracks.removeAll { nowMs - it.lastSeenMs > staleTrackMs }
    cooldownMap.entries.removeAll { nowMs - it.value > cooldownMs * COOLDOWN_CLEANUP_MULTIPLIER }

    val visible = mutableListOf<DetectionBox>()
    val newlyRegistered = mutableListOf<DetectionBox>()
    val maxGridIndex = COOLDOWN_SPATIAL_GRID_BINS.toInt() - 1

    for (box in boxes.sortedByDescending { it.confidence }) {
        val key = CooldownKey(
            label = box.label,
            cellX = (box.cx * COOLDOWN_SPATIAL_GRID_BINS).toInt().coerceIn(0, maxGridIndex),
            cellY = (box.cy * COOLDOWN_SPATIAL_GRID_BINS).toInt().coerceIn(0, maxGridIndex)
        )
        val bestMatch = tracks.indices.asSequence()
            .filter { tracks[it].label == box.label }
            .map { idx -> idx to detectionIoU(tracks[idx].box, box) }
            .maxByOrNull { it.second }

        if (bestMatch != null && bestMatch.second >= iouThreshold)
            tracks[bestMatch.first] = tracks[bestMatch.first].copy(box = box, lastSeenMs = nowMs)
        else
            tracks.add(TrackedDetection(label = box.label, box = box, lastSeenMs = nowMs))

        visible.add(box)
        val last = cooldownMap[key]
        if (last == null || nowMs - last >= cooldownMs) { cooldownMap[key] = nowMs; newlyRegistered.add(box) }
    }
    return TemporalDebounceResult(visibleBoxes = visible, newlyRegistered = newlyRegistered)
}

fun detectionIoU(a: DetectionBox, b: DetectionBox): Float {
    val ax1 = a.cx - a.w / 2f; val ay1 = a.cy - a.h / 2f
    val ax2 = a.cx + a.w / 2f; val ay2 = a.cy + a.h / 2f
    val bx1 = b.cx - b.w / 2f; val by1 = b.cy - b.h / 2f
    val bx2 = b.cx + b.w / 2f; val by2 = b.cy + b.h / 2f
    val interW = (minOf(ax2, bx2) - maxOf(ax1, bx1)).coerceAtLeast(0f)
    val interH = (minOf(ay2, by2) - maxOf(ay1, by1)).coerceAtLeast(0f)
    val inter  = interW * interH
    val union  = a.w * a.h + b.w * b.h - inter
    return if (union <= 0f) 0f else inter / union
}

private fun classifyError(e: Exception): String {
    val m = e.message ?: ""
    return Regex("\\b([1-5]\\d{2})\\b").find(m)?.let { "HTTP_${it.value}" } ?: e::class.java.name
}

// ── ReviewSheet ───────────────────────────────────────────────────────────────

@Composable
fun ReviewSheet(items: SnapshotStateList<DetectedItem>, isSaving: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scrimAlpha by animateFloatAsState(if (entered) 0.6f else 0f,   tween(280), label = "scrim")
    val sheetScale by animateFloatAsState(if (entered) 1f  else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "scale")
    val sheetAlpha by animateFloatAsState(if (entered) 1f  else 0f,   tween(220), label = "alpha")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)).clickable(remember { MutableInteractionSource() }, null) { onDismiss() }) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter).fillMaxWidth()
                .scale(sheetScale).alpha(sheetAlpha)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp).width(36.dp).height(4.dp).background(borderN, RoundedCornerShape(2.dp)))
            Text("Are you sure?", color = whiteN, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Review quantities before generating QR", color = mutedN, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(surfAltN).border(1.dp, borderN, RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (items.isEmpty()) {
                    Text("No items detected yet.\nYou can still generate a QR for an empty box.", color = mutedN, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    items.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = borderN)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, color = whiteN, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SmallQtyButton("−") { if (item.count > 1) items[i] = item.copy(count = item.count - 1) else items.removeAt(i) }
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
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total items", color = mutedN, fontSize = 13.sp)
                Text("${items.sumOf { it.count }}", color = tealN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onConfirm, enabled = !isSaving, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = redN)) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Finalize", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SmallQtyButton(text: String, active: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text, color = whiteN, fontSize = 16.sp,
        modifier = Modifier.size(28.dp).clip(CircleShape)
            .background(if (active) tealN.copy(alpha = 0.20f) else surfN)
            .border(1.dp, if (active) tealN.copy(alpha = 0.55f) else borderN, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .wrapContentSize(Alignment.Center)
    )
}

// ── ComplaintSheet ────────────────────────────────────────────────────────────

@Composable
fun ComplaintSheet(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onSubmitReport: suspend (complaintInfo: String) -> Boolean,
) {
    val reasons      = listOf("Item damaged", "Wrong item", "Torn / worn out", "Missing label", "Other")
    var selected     by remember { mutableStateOf<String?>(null) }
    var notes        by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val sheetScope   = rememberCoroutineScope()
    var entered      by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scrimAlpha by animateFloatAsState(if (entered) 0.6f else 0f,   tween(280), label = "cScrim")
    val sheetScale by animateFloatAsState(if (entered) 1f  else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "cScale")
    val sheetAlpha by animateFloatAsState(if (entered) 1f  else 0f,   tween(220), label = "cAlpha")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)).clickable(remember { MutableInteractionSource() }, null) { onDismiss() }) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter).fillMaxWidth()
                .scale(sheetScale).alpha(sheetAlpha)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(surfN)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp).width(36.dp).height(4.dp).background(borderN, RoundedCornerShape(2.dp)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = orangeN, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report an Issue", color = whiteN, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text("Select the issue type below.", color = mutedN, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    val isSelected = selected == reason
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) orangeN.copy(alpha = 0.12f) else surfAltN)
                            .border(1.dp, if (isSelected) orangeN.copy(alpha = 0.6f) else borderN, RoundedCornerShape(10.dp))
                            .clickable(remember { MutableInteractionSource() }, null) { selected = reason }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Box(modifier = Modifier.size(16.dp).border(1.5.dp, if (isSelected) orangeN else mutedN, CircleShape).background(if (isSelected) orangeN else Color.Transparent, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(reason, color = if (isSelected) whiteN else mutedN, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                placeholder = { Text("Additional notes (optional)…", color = mutedN, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = tealN, unfocusedBorderColor = borderN,
                    focusedTextColor = whiteN, unfocusedTextColor = whiteN,
                    cursorColor = tealN, focusedContainerColor = surfAltN, unfocusedContainerColor = surfAltN
                ), maxLines = 3
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = {
                    if (selected != null) {
                        Log.d(DEBUG_TAG, "Submit button clicked — selected='$selected'")
                        sheetScope.launch {
                            isSubmitting = true
                            Log.d(DEBUG_TAG, "coroutine started — isSubmitting=true")
                            try {
                                val complaintInfo = buildString {
                                    append(selected!!)
                                    if (notes.isNotBlank()) append(": $notes")
                                }
                                Log.d(DEBUG_TAG, "complaintInfo built (length=${complaintInfo.length})")
                                val success = onSubmitReport(complaintInfo)
                                Log.d(DEBUG_TAG, "onSubmitReport returned success=$success")
                                if (success) {
                                    Log.d(DEBUG_TAG, "Submission succeeded — calling onSuccess()")
                                    onSuccess()
                                }
                            } catch (e: Exception) {
                                Log.e(DEBUG_TAG, "Unexpected error in submit coroutine: ${e.message}", e)
                            } finally {
                                isSubmitting = false
                                Log.d(DEBUG_TAG, "finally — isSubmitting=false")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = orangeN, disabledContainerColor = orangeN.copy(alpha = 0.3f)),
                enabled  = selected != null && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Submitting...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit Report", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, borderN),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = mutedN)
            ) { Text("Cancel", fontSize = 14.sp) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
