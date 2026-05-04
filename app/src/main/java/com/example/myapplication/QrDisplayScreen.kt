package com.example.myapplication

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.BoxRepository
import com.example.myapplication.data.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

private val tealQ   = Color(0xFF2DD4BF)
private val redQ    = Color(0xFFE53E3E)
private val surfQ   = Color(0xFF161B22)
private val borderQ = Color(0xFF30363D)
private val mutedQ  = Color(0xFF8B949E)
private val whiteQ  = Color(0xFFF0F6FC)
private val bgQ     = Color(0xFF0D1117)

/**
 * Display the QR code and item list for the box identified by [boxId].
 * Data is loaded from local Room DB — fast but we enforce a minimum shimmer
 * window so the QR bitmap has time to generate and the transition feels
 * intentional rather than a jarring flash of content.
 */
@Composable
fun QrDisplayScreen(
    navController: NavController,
    boxId: Long
) {
    val context    = LocalContext.current
    val repository = remember { BoxRepository(AppDatabase.getInstance(context)) }

    var boxName   by remember { mutableStateOf("") }
    var contents  by remember { mutableStateOf<List<DetectedItem>>(emptyList()) }
    var createdAt by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(boxId) {
        // Read from Room on IO thread — completes in microseconds
        withContext(Dispatchers.IO) {
            val box = repository.getBoxById(boxId)
            if (box != null) {
                boxName   = box.name
                createdAt = box.createdAt
                contents  = repository.getItems(boxId)
            }
        }
        // Minimum shimmer window:
        //   - Gives the QR bitmap generation coroutine (Dispatchers.Default inside
        //     QrDisplayContent) time to start and produce at least one frame.
        //   - Matches the tween(600) entry fade-in so content never snaps in abruptly.
        //   - Makes the "fast feel" shimmer actually visible rather than a single-frame flash.
        delay(650L)
        isLoading = false
    }

    QrDisplayContent(
        navController = navController,
        boxName       = boxName,
        contents      = contents,
        createdAt     = createdAt,
        isDataLoading = isLoading
    )
}

/**
 * Display the scan result for the box identified by [boxLabel],
 * loading data from Supabase (remote DB only — nothing is saved locally).
 *
 * Uses [ScanResultContent] — an items-first layout — rather than the
 * "attach QR to box" layout used after creating a new box.
 *
 * Shows an error card if the box is not found or there is no internet.
 */
@Composable
fun QrDisplayScreenByLabel(
    navController: NavController,
    boxLabel: String
) {
    val supabaseRepo = remember { SupabaseRepository() }

    var boxName   by remember { mutableStateOf("") }
    var contents  by remember { mutableStateOf<List<DetectedItem>>(emptyList()) }
    var createdAt by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(boxLabel) {
        withContext(Dispatchers.IO) {
            try {
                val result = supabaseRepo.fetchBoxWithItems(boxLabel)
                if (result != null) {
                    boxName   = result.first.boxLabel
                    contents  = result.second
                    createdAt = parseSupabaseTimestamp(result.first.createdAt)
                } else {
                    loadError = "This box was not found in the remote database."
                }
            } catch (e: Exception) {
                loadError = "Could not reach the server. Please check your internet connection."
            }
            isLoading = false
        }
    }

    when {
        isLoading -> ScanResultShimmer()
        loadError != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgQ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(redQ.copy(alpha = 0.12f))
                            .border(1.dp, redQ.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚠", fontSize = 32.sp)
                    }
                    Text(
                        "Could Not Load Box",
                        color      = redQ,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        loadError!!,
                        color       = mutedQ,
                        fontSize    = 14.sp,
                        textAlign   = TextAlign.Center,
                        lineHeight  = 20.sp
                    )
                    Button(
                        onClick  = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = redQ)
                    ) {
                        Text(
                            "Go Back",
                            color      = Color.White,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        else -> ScanResultContent(
            navController = navController,
            boxName       = boxName,
            contents      = contents,
            createdAt     = createdAt
        )
    }
}

/**
 * Parses an ISO-8601 timestamp string (as returned by Supabase/PostgREST,
 * e.g. "2024-01-01T12:00:00+00:00" or "2024-01-01T12:00:00Z") to epoch millis.
 * Returns 0 on blank input or any parse failure.
 */
private fun parseSupabaseTimestamp(s: String): Long {
    if (s.isBlank()) return 0L
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.parse(
                s.replace(Regex("\\+00:00$"), "Z")
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            @Suppress("SimpleDateFormat")
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(s)?.time ?: 0L
        }
    } catch (_: Exception) {
        0L
    }
}

// ── QrDisplayContent ──────────────────────────────────────────────────────────

/** Shared display composable used by both [QrDisplayScreen] and [QrDisplayScreenByLabel]. */
@Composable
private fun QrDisplayContent(
    navController: NavController,
    boxName:       String,
    contents:      List<DetectedItem>,
    createdAt:     Long,
    isDataLoading: Boolean = false
) {
    val context = LocalContext.current

    // Generate QR bitmap off main thread once boxName is ready.
    // This runs concurrently with the shimmer delay in QrDisplayScreen,
    // so by the time isLoading flips to false the bitmap is usually ready.
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(boxName, contents) {
        if (boxName.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            qrBitmap = QrUtils.generate(QrUtils.buildPayload(boxName, contents))
        }
    }

    // Entry animations
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val fadeIn  by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label         = "fade"
    )
    val slideUp by animateFloatAsState(
        targetValue   = if (visible) 0f else 40f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "slide"
    )

    // Subtle ambient glow pulse behind QR card
    val shimmer      = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmer.animateFloat(
        initialValue  = 0.03f,
        targetValue   = 0.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "shimmerAlpha"
    )

    val formattedDate = remember(createdAt) {
        if (createdAt == 0L) "—"
        else SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()).format(Date(createdAt))
    }

    // Both data AND QR bitmap must be ready before we swap shimmer → real content
    val isSectionLoading = isDataLoading || qrBitmap == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(0f to bgQ, 0.6f to bgQ, 1f to Color(0xFF0A1628))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Status row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(fadeIn),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tealQ.copy(alpha = 0.12f))
                        .border(1.dp, tealQ.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(tealQ, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint     = bgQ,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "BOX LOGGED",
                        color         = tealQ,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    boxName,
                    color        = mutedQ,
                    fontSize     = 11.sp,
                    fontFamily   = FontFamily.Monospace,
                    modifier     = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfQ)
                        .border(1.dp, borderQ, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Heading ────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(fadeIn)
                    .offset(y = slideUp.dp)
            ) {
                Text(
                    "SCAN TO IDENTIFY",
                    color      = whiteQ,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Attach this QR to your box",
                    color     =tealQ,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── QR card — shimmer until bitmap is ready ────────────────────
            Box(modifier = Modifier.alpha(fadeIn).scale(0.9f + 0.1f * fadeIn)) {
                // Ambient glow behind the card
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .align(Alignment.Center)
                        .drawBehind {
                            drawCircle(
                                tealQ.copy(alpha = shimmerAlpha),
                                radius = size.minDimension * 0.58f
                            )
                        }
                )
                // White QR card
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = qrBitmap
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap             = bmp.asImageBitmap(),
                            contentDescription = "QR code for $boxName",
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.fillMaxSize()
                        )
                    } else {
                        // Shimmer placeholder inside the white card while bitmap generates
                        ShimmerBox(
                            modifier     = Modifier.fillMaxSize(),
                            cornerRadius = 12
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Contents card — shimmer rows until data is ready ───────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(fadeIn)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfQ)
                    .border(1.dp, borderQ, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "BOX CONTENTS",
                        color         = mutedQ,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    if (!isDataLoading) {
                        Text(
                            "${contents.sumOf { it.count }} items",
                            color      = tealQ,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        // Shimmer pill in place of the count badge
                        ShimmerBox(
                            modifier     = Modifier.width(60.dp).height(16.dp),
                            cornerRadius = 8
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (isDataLoading) {
                    // Shimmer placeholder rows while data loads
                    repeat(4) { idx ->
                        if (idx > 0) {
                            HorizontalDivider(
                                color    = borderQ,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        ShimmerBox(
                            modifier     = Modifier.fillMaxWidth().height(20.dp),
                            cornerRadius = 8
                        )
                    }
                } else if (contents.isEmpty()) {
                    Text(
                        "No items recorded for this box.",
                        color     = mutedQ,
                        fontSize  = 13.sp,
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    contents.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(
                            color    = borderQ,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(tealQ, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    item.label,
                                    color      = whiteQ,
                                    fontSize   = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                "×${item.count}",
                                color      = mutedQ,
                                fontSize   = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = borderQ)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Logged at", color = mutedQ, fontSize = 11.sp)
                    if (isDataLoading) {
                        ShimmerBox(Modifier.width(120.dp).height(12.dp), cornerRadius = 6)
                    } else {
                        Text(
                            formattedDate,
                            color      = mutedQ,
                            fontSize   = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Action buttons — shimmer until QR bitmap + data both ready ─
            Column(
                modifier              = Modifier.fillMaxWidth().alpha(fadeIn),
                verticalArrangement   = Arrangement.spacedBy(10.dp)
            ) {
                if (isSectionLoading) {
                    // Share/Print button shimmer
                    ShimmerBox(
                        modifier     = Modifier.fillMaxWidth().height(52.dp),
                        cornerRadius = 12
                    )
                } else {
                    Button(
                        onClick = {
                            val bmp = qrBitmap ?: return@Button
                            val cachePath = File(context.cacheDir, "shared_qr")
                            cachePath.mkdirs()
                            val file = File(cachePath, "qr_${boxName}.png")
                            try {
                                FileOutputStream(file).use {
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share QR Code via")
                                )
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = redQ)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SHARE / PRINT QR",
                            color      = Color.White,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick  = {
                        navController.navigate("inventory_home") {
                            popUpTo("inventory_home") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, borderQ),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = whiteQ)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint     = mutedQ,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "BACK TO HOME",
                        color      = mutedQ,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Shimmer helpers ───────────────────────────────────────────────────────────

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerQ")
    val x by transition.animateFloat(
        initialValue  = -600f,
        targetValue   =  1400f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "shimmerXQ"
    )
    return Brush.linearGradient(
        colors = listOf(Color(0xFF1C2333), Color(0xFF232D3F), Color(0xFF1C2333)),
        start  = Offset(x, 0f),
        end = Offset(x + 300f, 150f)
    )
}

@Composable
private fun ShimmerBox(modifier: Modifier, cornerRadius: Int = 12) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(brush)
    )
}

// ── Full-screen shimmer skeletons ─────────────────────────────────────────────

@Composable
private fun QrDisplayShimmer() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShimmerBox(Modifier.width(130.dp).height(32.dp), cornerRadius = 20)
            ShimmerBox(Modifier.width(90.dp).height(32.dp),  cornerRadius = 8)
        }
        Spacer(Modifier.height(32.dp))
        ShimmerBox(Modifier.width(180.dp).height(26.dp))
        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.width(220.dp).height(16.dp))
        Spacer(Modifier.height(36.dp))
        ShimmerBox(Modifier.size(210.dp), cornerRadius = 20)
        Spacer(Modifier.height(24.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(190.dp), cornerRadius = 16)
        Spacer(Modifier.height(20.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(52.dp))
        Spacer(Modifier.height(10.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(52.dp))
    }
}

@Composable
private fun ScanResultShimmer() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgQ)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ShimmerBox(Modifier.size(40.dp),                cornerRadius = 20)
                ShimmerBox(Modifier.width(120.dp).height(20.dp))
                ShimmerBox(Modifier.width(60.dp).height(28.dp), cornerRadius = 14)
            }
            Spacer(Modifier.height(32.dp))
            ShimmerBox(Modifier.size(100.dp), cornerRadius = 50)
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.width(140.dp).height(20.dp))
            Spacer(Modifier.height(28.dp))
            repeat(3) {
                ShimmerBox(Modifier.fillMaxWidth().height(68.dp), cornerRadius = 14)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(52.dp))
        }
    }
}

// ── ScanResultContent — items-first layout shown after scanning a QR ──────────

@Composable
private fun ScanResultContent(
    navController: NavController,
    boxName:       String,
    contents:      List<DetectedItem>,
    createdAt:     Long
) {
    val totalCount = contents.sumOf { it.count }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val fadeIn  by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label         = "srFade"
    )
    val slideUp by animateFloatAsState(
        targetValue   = if (visible) 0f else 30f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label         = "srSlide"
    )

    val formattedDate = remember(createdAt) {
        if (createdAt == 0L) "—"
        else SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()).format(Date(createdAt))
    }

    val inf        = rememberInfiniteTransition(label = "srPulse")
    val pulseAlpha by inf.animateFloat(
        initialValue  = 0.08f,
        targetValue   = 0.22f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "srPulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(0f to bgQ, 0.7f to bgQ, 1f to Color(0xFF0A1628))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth().alpha(fadeIn),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick  = { navController.popBackStack() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(surfQ, CircleShape)
                        .border(1.dp, borderQ, CircleShape)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint     = whiteQ,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tealQ.copy(alpha = 0.12f))
                        .border(1.dp, tealQ.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(tealQ, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint     = bgQ,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "BOX SCANNED",
                        color         = tealQ,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Hero count badge ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .alpha(fadeIn)
                    .offset(y = slideUp.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .drawBehind {
                            drawCircle(
                                tealQ.copy(alpha = pulseAlpha),
                                radius = size.minDimension * 0.52f
                            )
                        }
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(tealQ.copy(alpha = 0.25f), tealQ.copy(alpha = 0.08f))
                            )
                        )
                        .border(2.dp, tealQ.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$totalCount",
                            color      = whiteQ,
                            fontSize   = 34.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp
                        )
                        Text(
                            if (totalCount == 1) "item" else "items",
                            color      = tealQ,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                boxName,
                color      = mutedQ,
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier
                    .alpha(fadeIn)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfQ)
                    .border(1.dp, borderQ, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )

            Spacer(Modifier.height(28.dp))

            // ── Contents header ────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().alpha(fadeIn),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "CONTENTS",
                    color         = mutedQ,
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "${contents.size} type${if (contents.size == 1) "" else "s"}",
                    color    = mutedQ,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Item cards ─────────────────────────────────────────────────
            if (contents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(fadeIn)
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfQ)
                        .border(1.dp, borderQ, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No items recorded for this box.",
                        color     = mutedQ,
                        fontSize  = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier              = Modifier.fillMaxWidth().alpha(fadeIn),
                    verticalArrangement   = Arrangement.spacedBy(10.dp)
                ) {
                    contents.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(surfQ)
                                .border(1.dp, borderQ, RoundedCornerShape(14.dp))
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(tealQ, CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    item.label,
                                    color      = whiteQ,
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tealQ.copy(alpha = 0.15f))
                                    .border(1.dp, tealQ.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${item.count}",
                                    color      = tealQ,
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Metadata row ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(fadeIn)
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfQ)
                    .border(1.dp, borderQ, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Logged at", color = mutedQ, fontSize = 12.sp)
                Text(
                    formattedDate,
                    color      = whiteQ,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Back button ────────────────────────────────────────────────
            OutlinedButton(
                onClick  = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp).alpha(fadeIn),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, borderQ),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = whiteQ)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint     = mutedQ,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Back to Scanner",
                    color      = mutedQ,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}