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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tealQ   = Color(0xFF2DD4BF)
private val redQ    = Color(0xFFE53E3E)
private val surfQ   = Color(0xFF161B22)
private val borderQ = Color(0xFF30363D)
private val mutedQ  = Color(0xFF8B949E)
private val whiteQ  = Color(0xFFF0F6FC)
private val bgQ     = Color(0xFF0D1117)

/**
 * Display the QR code and item list for the box identified by [boxId].
 *
 * All data is loaded from the local Room database so the screen is fully
 * dynamic — no hardcoded box names or item lists.
 */
@Composable
fun QrDisplayScreen(
    navController: NavController,
    boxId: Long
) {
    val context    = LocalContext.current
    val repository = remember { BoxRepository(AppDatabase.getInstance(context)) }

    // ── Load box + items from DB ───────────────────────────────────────────
    var boxName  by remember { mutableStateOf("") }
    var contents by remember { mutableStateOf<List<DetectedItem>>(emptyList()) }
    var createdAt by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(boxId) {
        withContext(Dispatchers.IO) {
            val box  = repository.getBoxById(boxId)
            if (box != null) {
                boxName   = box.name
                createdAt = box.createdAt
                contents  = repository.getItems(boxId)
            }
            isLoading = false
        }
    }

    // ── Generate QR bitmap off main thread once both boxName and contents are ready
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(boxName, contents) {
        if (boxName.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            qrBitmap = QrUtils.generate(QrUtils.buildPayload(boxName, contents))
        }
    }

    // ── Entry animations ───────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val fadeIn  by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "fade")
    val slideUp by animateFloatAsState(
        if (visible) 0f else 40f,
        tween(600, easing = FastOutSlowInEasing), label = "slide"
    )
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmer.animateFloat(
        0.04f, 0.13f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )

    val formattedDate = remember(createdAt) {
        if (createdAt == 0L) "—"
        else SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()).format(Date(createdAt))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to bgQ, 0.6f to bgQ, 1f to Color(0xFF0A1628)))
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = tealQ,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

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
                modifier = Modifier.fillMaxWidth().alpha(fadeIn),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tealQ.copy(alpha = 0.12f))
                        .border(1.dp, tealQ.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(Modifier.size(16.dp).background(tealQ, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = bgQ, modifier = Modifier.size(10.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("BOX LOGGED", color = tealQ, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Text(boxName, color = mutedQ, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfQ)
                        .border(1.dp, borderQ, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp))
            }

            Spacer(Modifier.height(28.dp))

            // ── Heading ────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(fadeIn).offset(y = slideUp.dp)
            ) {
                Text("Scan to identify", color = whiteQ, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Attach this QR to your box", color = mutedQ,
                    fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(32.dp))

            // ── QR card ────────────────────────────────────────────────────
            Box(modifier = Modifier.alpha(fadeIn).scale(0.9f + 0.1f * fadeIn)) {
                // Glow
                Box(modifier = Modifier.size(250.dp).align(Alignment.Center).drawBehind {
                    drawCircle(tealQ.copy(alpha = shimmerAlpha), radius = size.minDimension * 0.58f)
                })
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
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "QR code for $boxName",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = tealQ, strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Contents card ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth().alpha(fadeIn)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfQ)
                    .border(1.dp, borderQ, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("BOX CONTENTS", color = mutedQ, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("${contents.sumOf { it.count }} items",
                        color = tealQ, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                if (contents.isEmpty()) {
                    Text(
                        "No items recorded for this box.",
                        color = mutedQ, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    contents.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = borderQ,
                            modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(tealQ, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(item.label, color = whiteQ, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium)
                            }
                            Text("×${item.count}", color = mutedQ, fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = borderQ)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Logged at", color = mutedQ, fontSize = 11.sp)
                    Text(formattedDate, color = mutedQ, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Action buttons ─────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth().alpha(fadeIn),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { /* TODO: share qrBitmap via Intent */ },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = redQ)
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share / Print QR", color = Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {
                        navController.navigate("inventory_home") {
                            popUpTo("inventory_home") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderQ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = whiteQ)
                ) {
                    Icon(Icons.Default.Home, null, tint = mutedQ, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Back to Home", color = mutedQ,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}