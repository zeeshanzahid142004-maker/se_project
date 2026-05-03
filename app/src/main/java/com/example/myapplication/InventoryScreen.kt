package com.example.myapplication
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.data.DayStats
import com.example.myapplication.data.SupabaseRepository
import com.example.myapplication.data.TotalStats
import com.example.myapplication.data.WarehouseUser
import com.example.myapplication.ui.theme.SB
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin



private object UiTuning {
    // BIGGER boxes
    val itemWidth = 170.dp
    val canvasWidth = 190.dp
    val canvasHeight = 200.dp

    val rowSpacing = 18.dp
    val screenHorizontalPadding = 16.dp

    const val maxFlapDeg = 116f
    const val flapStagger = 0.03f
    const val flapBounceBack = 0.93f

    const val openMs = 400
    const val bounceDownMs = 70
    const val bounceUpMs = 85
    const val rippleMs = 720
    const val dustMs = 700
    const val navDelayMs = 30L

    const val bloomAlpha = 0.18f
    const val dustParticles = 20
    const val dustMaxAlpha = 0.62f
}

private object BoxLayout {
    const val boxL = 0.20f
    const val boxR = 0.80f
    const val hingeY = 0.36f
    const val bodyBot = 0.92f
    const val flapHBase = 0.10f
    const val bodyCorner = 10f
    const val flapCorner = 8f
    const val rippleCoreHalf = 0.09f
    const val rippleGlowHalf = 0.22f
}

private object BoxPalette {
    val body = Color(0xFFC88C43)
    val bodyShade = Color(0xFF8F5926)
    val flap = Color(0xFFD59B4C)
    val flapUnder = Color(0xFF8B5A25)
    val border = Color(0xFF764816)
}

private object HomePalette {
    val bg = Color(0xFF080C10)
    val surface = Color(0xFF161B22)
    val surfaceAlt = Color(0xFF1C2333)
    val teal = Color(0xFF2DD4BF)
    val red = Color(0xFFE53E3E)
    val white = Color(0xFFF0F6FC)
    val muted = Color(0xFF8B949E)
    val border = Color(0xFF30363D)
    val shimmerA = Color(0xFF1C2333)
    val shimmerB = Color(0xFF232D3F)
}

private const val TAG_INV = "InventoryScreen"

@Composable
fun InventoryScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val supabaseRepository = remember { SupabaseRepository() }
    val currentMonth = remember { YearMonth.now() }
    val today = remember { LocalDate.now() }

    var currentUserId by remember { mutableStateOf<String?>(null) }
    var profileLoading by remember { mutableStateOf(true) }
    var profileMessage by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<WarehouseUser?>(null) }

    var activityLoading by remember { mutableStateOf(true) }
    var activityDates by remember { mutableStateOf<List<LocalDate>>(emptyList()) }

    var statsLoading by remember { mutableStateOf(true) }
    var totalStats by remember { mutableStateOf<TotalStats?>(null) }

    val activeDateSet = remember(activityDates) { activityDates.toSet() }

    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var dayStatsLoading by remember { mutableStateOf(false) }
    var dayStats by remember { mutableStateOf<DayStats?>(null) }

    LaunchedEffect(Unit) {
        currentUserId = SupabaseModule.client.auth.currentUserOrNull()?.id
    }

    LaunchedEffect(currentUserId) {
        val userId = currentUserId
        if (userId == null) {
            profileLoading = false
            profileMessage = "No employee signed in"
            activityLoading = false
            statsLoading = false
            activityDates = emptyList()
            totalStats = TotalStats(0, 0)
            return@LaunchedEffect
        }

        profileLoading = true
        statsLoading = true
        activityLoading = true
        profileMessage = null

        try {
            profile = withContext(Dispatchers.IO) { supabaseRepository.fetchEmployeeProfile(userId) }
            if (profile == null) {
                profileMessage = "Employee record not found"
            }
        } catch (e: Exception) {
            Log.e(TAG_INV, "Employee profile load failed: ${e.message}", e)
            profileMessage = "Unable to load employee profile"
        } finally {
            profileLoading = false
        }

        try {
            activityDates = withContext(Dispatchers.IO) {
                supabaseRepository.fetchActivityDates(userId, currentMonth.year, currentMonth.monthValue)
            }
        } catch (e: Exception) {
            Log.e(TAG_INV, "Activity dates load failed: ${e.message}", e)
            activityDates = emptyList()
        } finally {
            activityLoading = false
        }

        try {
            totalStats = withContext(Dispatchers.IO) { supabaseRepository.fetchTotalStats(userId) }
        } catch (e: Exception) {
            Log.e(TAG_INV, "Total stats load failed: ${e.message}", e)
            totalStats = TotalStats(0, 0)
        } finally {
            statsLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), // TWEAK: screen padding
            verticalArrangement = Arrangement.spacedBy(10.dp) // TWEAK: section gaps
        ) {
            Spacer(Modifier.height(12.dp)) // TWEAK: top gap

            Text(
                "Welcome back,",
                color = HomePalette.muted,
                fontSize = 12.sp // TWEAK: welcome size
            )

            EmployeeProfileCard(
                modifier = Modifier.fillMaxWidth(),
                loading = profileLoading,
                profile = profile,
                message = profileMessage,
                totalStats = totalStats,
                statsLoading = statsLoading
            )

            Text(
                "Activity Calendar",
                color = HomePalette.white,
                fontSize = 13.sp, // TWEAK
                fontWeight = FontWeight.SemiBold
            )

            CalendarActivityCard(
                modifier = Modifier.fillMaxWidth(),
                month = currentMonth,
                today = today,
                loading = activityLoading,
                activeDates = activeDateSet,
                onDayClick = { date ->
                    selectedDay = date
                    val userId = currentUserId
                    if (userId == null || date !in activeDateSet) {
                        dayStatsLoading = false
                        dayStats = DayStats(0, 0)
                        return@CalendarActivityCard
                    }
                    dayStatsLoading = true
                    dayStats = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val stats = supabaseRepository.fetchDayStats(userId, date)
                            withContext(Dispatchers.Main) {
                                dayStats = stats
                                dayStatsLoading = false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG_INV, "Day stats load failed: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                dayStats = DayStats(0, 0)
                                dayStatsLoading = false
                            }
                        }
                    }
                }
            )

            // Quick actions label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp) // TWEAK: accent bar width
                        .height(16.dp) // TWEAK: accent bar height
                        .background(HomePalette.teal)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "QUICK ACTIONS",
                    color = HomePalette.white,
                    fontSize = 12.sp, // TWEAK
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp // TWEAK
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // TWEAK
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    InteractiveBoxItem("Scan QR code", "LOOK UP BOX", navController, "scanner_screen", true)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    InteractiveBoxItem("New box", "START SCANNING", navController, "new_box_screen", false)
                }
            }

            Spacer(Modifier.height(4.dp)) // TWEAK: bottom breath
        }
    }

    DayStatsDialog(
        selectedDay = selectedDay,
        loading = dayStatsLoading,
        stats = dayStats,
        onDismiss = {
            selectedDay = null
            dayStats = null
            dayStatsLoading = false
        }
    )
}

@Composable
private fun EmployeeProfileCard(
    modifier: Modifier,
    loading: Boolean,
    profile: WarehouseUser?,
    message: String?,
    totalStats: TotalStats?,
    statsLoading: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    if (loading) {
        CardShimmer(modifier = modifier)
        return
    }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.shadow(
                elevation    = 20.dp,                             // TWEAK: profile card shadow depth
                shape        = shape,
                spotColor    = Color.White.copy(alpha = 0.10f),   // TWEAK: profile card spot shadow
                ambientColor = Color.White.copy(alpha = 0.05f)    // TWEAK: profile card ambient shadow
            )
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = HomePalette.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, HomePalette.border)
            ) {
                if (profile == null) {
                    Text(
                        text = message ?: "Employee profile unavailable",
                        color = HomePalette.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp) // TWEAK: card padding
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp), // TWEAK: card padding
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT: name, role pill, email
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (profile.fullName.isBlank()) "Unnamed Employee" else profile.fullName,
                                color = HomePalette.white,
                                fontSize = 17.sp, // TWEAK
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(HomePalette.teal.copy(alpha = 0.12f))
                                    .border(1.dp, HomePalette.teal.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = profile.role.ifBlank { "Role unknown" },
                                    color = HomePalette.teal,
                                    fontSize = 10.sp, // TWEAK
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (profile.email.isBlank()) "Email not available" else profile.email,
                                color = HomePalette.muted,
                                fontSize = 11.sp // TWEAK
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        // RIGHT: two stat columns separated by vertical divider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Boxes stat
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (statsLoading) {
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
                                            .height(22.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(rememberShimmerBrush())
                                    )
                                } else {
                                    Text(
                                        text = (totalStats?.totalBoxes ?: 0).toString(),
                                        color = HomePalette.teal,
                                        fontSize = 22.sp, // TWEAK
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text("Boxes", color = HomePalette.muted, fontSize = 10.sp) // TWEAK
                            }

                            Spacer(Modifier.width(12.dp))

                            // Vertical divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(36.dp) // TWEAK
                                    .background(HomePalette.border)
                            )

                            Spacer(Modifier.width(12.dp))

                            // Items stat
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (statsLoading) {
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
                                            .height(22.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(rememberShimmerBrush())
                                    )
                                } else {
                                    Text(
                                        text = (totalStats?.totalItems ?: 0).toString(),
                                        color = HomePalette.white,
                                        fontSize = 22.sp, // TWEAK
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text("Items", color = HomePalette.muted, fontSize = 10.sp) // TWEAK
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarActivityCard(
    modifier: Modifier,
    month: YearMonth,
    today: LocalDate,
    loading: Boolean,
    activeDates: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.shadow(
                elevation    = 16.dp,                             // TWEAK: calendar card shadow depth
                shape        = shape,
                spotColor    = Color.White.copy(alpha = 0.08f),   // TWEAK: calendar card spot shadow
                ambientColor = Color.White.copy(alpha = 0.04f)    // TWEAK: calendar card ambient shadow
            )
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = HomePalette.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, HomePalette.border)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { // TWEAK
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$monthLabel ${month.year}",
                            color = HomePalette.teal,
                            fontSize = 15.sp, // TWEAK
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Activity",
                            color = HomePalette.muted,
                            fontSize = 11.sp // TWEAK
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (loading) {
                        CalendarShimmer()
                    } else {
                        WeekStrip(
                            today = today,
                            activeDates = activeDates,
                            onDayClick = onDayClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    today: LocalDate,
    activeDates: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit
) {
    val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
    // currentWeekDays = today's week Sun–Sat (DayOfWeek: Mon=1 … Sun=7)
    val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong())
    val currentWeekDays = (0..6).map { weekStart.plusDays(it.toLong()) }

    // Day-of-week headers
    Row(modifier = Modifier.fillMaxWidth()) {
        dayHeaders.forEach { label ->
            Text(
                text = label,
                color = HomePalette.muted,
                fontSize = 11.sp, // TWEAK
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(6.dp))

    // Day cells
    Row(modifier = Modifier.fillMaxWidth()) {
        currentWeekDays.forEach { date ->
            val isToday  = date == today
            val isActive = activeDates.contains(date)
            val textColor = when {
                isToday  -> HomePalette.white
                isActive -> HomePalette.teal
                else     -> HomePalette.muted
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDayClick(date) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = textColor,
                        fontSize = 14.sp, // TWEAK
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp) // TWEAK
                                .background(HomePalette.teal, CircleShape)
                        )
                    } else {
                        Spacer(Modifier.size(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarShimmer() {
    val brush = rememberShimmerBrush()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(7) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun CardShimmer(modifier: Modifier) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .border(1.dp, HomePalette.border, RoundedCornerShape(16.dp))
            .height(120.dp)
    )
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerHome")
    val x by transition.animateFloat(
        initialValue = -500f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "shimmerHomeX"
    )
    return Brush.linearGradient(
        colors = listOf(HomePalette.shimmerA, HomePalette.shimmerB, HomePalette.shimmerA),
        start = Offset(x, 0f),
        end = Offset(x + 500f, 200f)
    )
}

@Composable
private fun DayStatsDialog(
    selectedDay: LocalDate?,
    loading: Boolean,
    stats: DayStats?,
    onDismiss: () -> Unit
) {
    if (selectedDay == null) return
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d") }
    val formattedSelectedDate = remember(selectedDay) { selectedDay.format(formatter) }
    val boxCount = stats?.boxes ?: 0
    val itemCount = stats?.items ?: 0
    val hasActivity = !loading && stats != null && (stats.boxes > 0 || stats.items > 0)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Scrim + radial depth layer — clickable to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)) // TWEAK: scrim
                    .background(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            radius = 800f // TWEAK: radial depth
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f) // TWEAK: popup width
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume click — prevent dismiss */ },
                shape = RoundedCornerShape(20.dp), // TWEAK
                colors = CardDefaults.cardColors(containerColor = HomePalette.surfaceAlt),
                elevation = CardDefaults.cardElevation(defaultElevation = 32.dp), // TWEAK
                border = BorderStroke(1.dp, HomePalette.teal.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp), // TWEAK
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp) // TWEAK
                ) {
                    Text(
                        text = formattedSelectedDate,
                        color = HomePalette.teal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp // TWEAK
                    )
                    HorizontalDivider(color = HomePalette.border)

                    when {
                        loading -> Text(
                            text = "Loading activity…",
                            color = HomePalette.muted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        hasActivity -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Boxes scanned", color = HomePalette.muted, fontSize = 14.sp)
                                Text(
                                    "$boxCount",
                                    color = HomePalette.white,
                                    fontSize = 26.sp, // TWEAK
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Items scanned", color = HomePalette.muted, fontSize = 14.sp)
                                Text(
                                    "$itemCount",
                                    color = HomePalette.teal,
                                    fontSize = 26.sp, // TWEAK
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            HorizontalDivider(color = HomePalette.border)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HomePalette.teal.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        HomePalette.teal.copy(alpha = 0.25f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$itemCount items across $boxCount boxes",
                                    color = HomePalette.teal,
                                    fontSize = 13.sp, // TWEAK
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        else -> {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(56.dp) // TWEAK
                                    .clip(CircleShape)
                                    .background(HomePalette.border),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("—", color = HomePalette.muted, fontSize = 20.sp)
                            }
                            Text(
                                "No scans recorded",
                                color = HomePalette.muted,
                                fontSize = 15.sp, // TWEAK
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "for this day",
                                color = HomePalette.muted.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp), // TWEAK
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HomePalette.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HomePalette.muted
                        )
                    ) {
                        Text("Close", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}


@Composable
fun InteractiveBoxItem(
    title: String,
    subtitle: String,
    navController: NavController,
    targetRoute: String,
    isQrBox: Boolean
) {
    var busy by remember { mutableStateOf(false) }

    val progress = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    val dust = remember { Animatable(0f) }
    val bloom = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(UiTuning.itemWidth)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!busy) {
                    busy = true
                    scope.launch {
                        progress.snapTo(0f)
                        ripple.snapTo(0f)
                        dust.snapTo(0f)
                        bloom.snapTo(0f)

                        val rippleJob = launch {
                            ripple.animateTo(
                                1f,
                                animationSpec = tween(UiTuning.rippleMs, easing = CubicBezierEasing(0.18f, 0f, 0.2f, 1f))
                            )
                        }
                        val dustJob = launch {
                            delay(20)
                            dust.animateTo(
                                1f,
                                animationSpec = tween(UiTuning.dustMs, easing = CubicBezierEasing(0.12f, 0f, 0.2f, 1f))
                            )
                        }
                        val bloomJob = launch {
                            delay(200)
                            bloom.animateTo(1f, tween(70, easing = FastOutSlowInEasing))
                            bloom.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                        }

                        progress.animateTo(
                            1f,
                            animationSpec = tween(UiTuning.openMs, easing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f))
                        )
                        progress.animateTo(UiTuning.flapBounceBack, tween(UiTuning.bounceDownMs, easing = FastOutSlowInEasing))
                        progress.animateTo(1f, tween(UiTuning.bounceUpMs, easing = FastOutSlowInEasing))

                        // Navigate right after the box-open animation completes.
                        // Ripple/dust/bloom are decorative — cancel them via coroutine scope
                        // rather than blocking navigation until they finish.
                        rippleJob.cancel()
                        dustJob.cancel()
                        bloomJob.cancel()

                        delay(UiTuning.navDelayMs)
                        navController.navigate(targetRoute)

                        progress.snapTo(0f)
                        ripple.snapTo(0f)
                        dust.snapTo(0f)
                        bloom.snapTo(0f)
                        busy = false
                    }
                }
            }
    ) {
        FlatBoxButton(
            modifier = Modifier.size(UiTuning.canvasWidth, UiTuning.canvasHeight),
            progress = progress.value,
            rippleProgress = ripple.value,
            dustProgress = dust.value,
            bloomProgress = bloom.value,
            isQrBox = isQrBox
        )

        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = if (busy) SB.teal else SB.white,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
        Text(
            subtitle,
            color = SB.muted,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private fun flapPhaseToDeg(phase: Float): Float {
    val t = phase.coerceIn(0f, 1f)
    return when {
        t < 0.78f -> {
            val x = t / 0.78f
            UiTuning.maxFlapDeg * (1f - (1f - x) * (1f - x))
        }
        t < 0.89f -> {
            val x = (t - 0.78f) / 0.11f
            UiTuning.maxFlapDeg * (1f + 0.06f * x)
        }
        else -> {
            val x = (t - 0.89f) / 0.11f
            UiTuning.maxFlapDeg * (1.06f - 0.06f * x)
        }
    }
}

@Composable
fun FlatBoxButton(
    modifier: Modifier = Modifier,
    progress: Float,
    rippleProgress: Float,
    dustProgress: Float,
    bloomProgress: Float,
    isQrBox: Boolean
) {
    val p = progress.coerceIn(0f, 1f)
    val rp = rippleProgress.coerceIn(0f, 1f)
    val dp = dustProgress.coerceIn(0f, 1f)
    val bp = bloomProgress.coerceIn(0f, 1f)

    val scanline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            scanline.snapTo(0f)
            scanline.animateTo(
                1f,
                animationSpec = tween(1800, easing = LinearEasing)
            )
            delay(350)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val boxL = w * BoxLayout.boxL
        val boxR = w * BoxLayout.boxR
        val boxW = boxR - boxL

        val hingeY = h * BoxLayout.hingeY
        val bodyBot = h * BoxLayout.bodyBot
        val bodyH = bodyBot - hingeY

        val centerX = w * 0.5f
        val centerY = hingeY + bodyH * 0.5f

        val flapW = boxW / 2f
        val flapHBase = h * BoxLayout.flapHBase
        val bodyCorner = CornerRadius(5f, 5f)

        val leftP = p
        val rightP = ((p - UiTuning.flapStagger) / (1f - UiTuning.flapStagger)).coerceIn(0f, 1f)

        val leftDeg = flapPhaseToDeg(leftP)
        val rightDeg = flapPhaseToDeg(rightP)

        val leftProjH =
            (flapHBase * abs(cos(Math.toRadians(leftDeg.toDouble()).toFloat()))).coerceAtLeast(1f)
        val rightProjH =
            (flapHBase * abs(cos(Math.toRadians(rightDeg.toDouble()).toFloat()))).coerceAtLeast(1f)

        val leftFill = if (leftDeg > 90f) SB.boxFlapUnder else SB.boxFlap
        val rightFill = if (rightDeg > 90f) SB.boxFlapUnder else SB.boxFlap

        // underglow
        drawOval(
            color = SB.teal,
            topLeft = Offset(boxL + boxW * 0.14f, bodyBot + h * 0.005f),
            size = Size(boxW * 0.72f, h * 0.06f),
            alpha = (0.08f + 0.10f * p).coerceIn(0f, 0.18f)
        )

        // base shadow
        drawOval(
            color = Color.Black,
            topLeft = Offset(boxL + boxW * 0.10f, bodyBot - h * 0.004f),
            size = Size(boxW * 0.80f, h * 0.08f),
            alpha = (0.12f + p * 0.12f).coerceIn(0f, 0.26f)
        )

        // body
        drawRoundRect(SB.boxBody, Offset(boxL, hingeY), Size(boxW, bodyH), bodyCorner)
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to SB.boxBodyShade.copy(alpha = 0.18f)
            ),
            topLeft = Offset(boxL, hingeY),
            size = Size(boxW, bodyH),
            cornerRadius = bodyCorner
        )

        // side depth
        drawRoundRect(
            color = SB.boxBodyShade.copy(alpha = 0.16f),
            topLeft = Offset(boxL + 1.5f, hingeY + 2f),
            size = Size(boxW * 0.055f, bodyH - 4f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = SB.boxBodyShade.copy(alpha = 0.11f),
            topLeft = Offset(boxR - boxW * 0.055f - 1.5f, hingeY + 2f),
            size = Size(boxW * 0.055f, bodyH - 4f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // bottom compression
        drawRoundRect(
            color = SB.boxBodyShade.copy(alpha = 0.22f),
            topLeft = Offset(boxL + 2f, bodyBot - 8f),
            size = Size(boxW - 4f, 6f),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )

        // top edge light
        drawRoundRect(
            color = SB.teal.copy(alpha = 0.16f),
            topLeft = Offset(boxL + 1f, hingeY + 1f),
            size = Size(boxW - 2f, 2.2f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        if (p > 0.02f) {
            val rimH = (flapHBase * 0.24f * p).coerceAtMost(flapHBase * 0.24f)
            drawRoundRect(
                SB.boxBodyShade,
                Offset(boxL, hingeY),
                Size(boxW, rimH),
                CornerRadius(6f, 6f),
                alpha = 0.55f
            )
        }

        // tap ripple
        if (rp > 0f) {
            val waveY = hingeY + rp * bodyH
            val coreHalf = bodyH * BoxLayout.rippleCoreHalf
            val glowHalf = bodyH * BoxLayout.rippleGlowHalf

            clipRect(boxL, hingeY, boxR, bodyBot) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SB.teal.copy(alpha = 0f),
                            SB.teal.copy(alpha = 0.10f),
                            SB.teal.copy(alpha = 0f),
                            Color.Transparent
                        ),
                        startY = waveY - glowHalf,
                        endY = waveY + glowHalf
                    ),
                    topLeft = Offset(boxL, hingeY),
                    size = Size(boxW, bodyH)
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SB.scanLine.copy(alpha = 0f),
                            SB.scanLine.copy(alpha = 0.22f),
                            SB.scanLine.copy(alpha = 0f),
                            Color.Transparent
                        ),
                        startY = waveY - coreHalf,
                        endY = waveY + coreHalf
                    ),
                    topLeft = Offset(boxL, hingeY),
                    size = Size(boxW, bodyH)
                )
                drawLine(
                    color = SB.scanLine.copy(alpha = 0.58f),
                    start = Offset(boxL + 2f, waveY),
                    end = Offset(boxR - 2f, waveY),
                    strokeWidth = 1.4f
                )
            }
        }

        // idle scanline
        val lineY = hingeY + scanline.value * bodyH
        clipRect(boxL, hingeY, boxR, bodyBot) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        SB.teal.copy(alpha = 0f),
                        SB.teal.copy(alpha = 0.06f),
                        SB.teal.copy(alpha = 0f),
                        Color.Transparent
                    ),
                    startY = lineY - bodyH * 0.12f,
                    endY = lineY + bodyH * 0.12f
                ),
                topLeft = Offset(boxL, hingeY),
                size = Size(boxW, bodyH)
            )
            drawLine(
                color = SB.scanLine.copy(alpha = 0.18f),
                start = Offset(boxL + 3f, lineY),
                end = Offset(boxR - 3f, lineY),
                strokeWidth = 1f
            )
        }

        // body border only
        drawRoundRect(
            color = SB.boxBorder,
            topLeft = Offset(boxL, hingeY),
            size = Size(boxW, bodyH),
            cornerRadius = bodyCorner,
            style = Stroke(width = 1.8f)
        )

        if (isQrBox) drawQrIcon(centerX, centerY, boxW * 0.50f)
        else drawPlusIcon(centerX, centerY, boxW * 0.20f)

        if (bp > 0f) {
            drawRect(
                color = SB.teal,
                topLeft = Offset(boxL, hingeY),
                size = Size(boxW, bodyH),
                alpha = UiTuning.bloomAlpha * bp
            )
        }

        if (dp > 0f) {
            val baseY = hingeY - 2f
            for (i in 0 until UiTuning.dustParticles) {
                val t = (i + 1) / UiTuning.dustParticles.toFloat()
                val seed = i * 1.37f
                val spread = boxW * 0.62f
                val x = centerX + (t - 0.5f) * spread + sin(seed * 3.1f + dp * 7.8f) * 2.6f
                val rise = dp * (12f + 30f * t)
                val y = baseY - rise
                val r = 1.0f + (i % 3) * 0.45f
                val a = ((1f - dp * 0.78f) * UiTuning.dustMaxAlpha).coerceIn(0f, 0.7f)
                drawCircle(color = SB.white, radius = r, center = Offset(x, y), alpha = a)
            }
        }

        drawLine(
            color = SB.boxBorder.copy(alpha = 0.78f),
            start = Offset(boxL, hingeY),
            end = Offset(boxR, hingeY),
            strokeWidth = 1.6f
        )

        // LEFT welded flap path
        withTransform({ rotate(-leftDeg, pivot = Offset(boxL, hingeY)) }) {
            val l = boxL
            val t = hingeY - leftProjH
            val r = boxL + flapW
            val b = hingeY
            val rad = 7f

            val leftFlapPath = Path().apply {
                moveTo(l, b) // square weld at bottom-left
                lineTo(l, t + rad)
                quadraticBezierTo(l, t, l + rad, t) // outer top-left round
                lineTo(r - 0.6f, t) // inner top
                lineTo(r - 0.6f, b) // inner side down (square weld)
                close()
            }

            drawPath(leftFlapPath, color = leftFill)
            drawPath(leftFlapPath, color = SB.boxBorder, style = Stroke(width = 2f))

            // center fold mark
            drawLine(
                color = SB.boxBorder.copy(alpha = 0.42f),
                start = Offset(r - 0.8f, t + 2f),
                end = Offset(r - 0.8f, b - 2f),
                strokeWidth = 1.0f
            )
        }

        // RIGHT welded flap path
        withTransform({ rotate(rightDeg, pivot = Offset(boxR, hingeY)) }) {
            val l = boxR - flapW
            val t = hingeY - rightProjH
            val r = boxR
            val b = hingeY
            val rad = 7f

            val rightFlapPath = Path().apply {
                moveTo(l + 0.6f, b) // square weld at inner bottom
                lineTo(l + 0.6f, t) // inner side up
                lineTo(r - rad, t)
                quadraticBezierTo(r, t, r, t + rad) // outer top-right round
                lineTo(r, b) // square weld at bottom-right
                close()
            }

            drawPath(rightFlapPath, color = rightFill)
            drawPath(rightFlapPath, color = SB.boxBorder, style = Stroke(width = 2f))

            // center fold mark
            drawLine(
                color = SB.boxBorder.copy(alpha = 0.42f),
                start = Offset(l + 0.8f, t + 2f),
                end = Offset(l + 0.8f, b - 2f),
                strokeWidth = 1.0f
            )
        }
    }
}
private val inkWhite = Color(0xFFF5EFE6)
private val inkWhiteDim = Color(0x99F5EFE6)

private fun DrawScope.drawQrIcon(cx: Float, cy: Float, size: Float) {
    val u = size / 8f
    val ox = cx - 3.5f * u
    val oy = cy - 3.5f * u
    listOf(
        Offset(ox, oy),
        Offset(ox + 4.5f * u, oy),
        Offset(ox, oy + 4.5f * u)
    ).forEach { fc ->
        drawRoundRect(
            inkWhite,
            fc,
            Size(u * 3f, u * 3f),
            CornerRadius(u * 0.5f),
            style = Stroke(u * 0.55f),
            alpha = 0.92f
        )
        drawRoundRect(
            inkWhiteDim,
            Offset(fc.x + u * 0.85f, fc.y + u * 0.85f),
            Size(u * 1.30f, u * 1.30f),
            CornerRadius(u * 0.3f),
            alpha = 0.68f
        )
    }

    val db = Offset(ox + 4.5f * u, oy + 4.5f * u)
    for (row in 0..2) for (col in 0..2) {
        if ((row + col) % 2 == 0) {
            drawRoundRect(
                inkWhite,
                Offset(db.x + col * u * 1.1f, db.y + row * u * 1.1f),
                Size(u * 0.80f, u * 0.80f),
                CornerRadius(u * 0.2f),
                alpha = 0.70f
            )
        }
    }
}

private fun DrawScope.drawPlusIcon(cx: Float, cy: Float, arm: Float) {
    val sw = arm * 0.40f
    drawLine(
        inkWhite,
        Offset(cx, cy - arm),
        Offset(cx, cy + arm),
        strokeWidth = sw,
        cap = StrokeCap.Round,
        alpha = 0.95f
    )
    drawLine(
        inkWhite,
        Offset(cx - arm, cy),
        Offset(cx + arm, cy),
        strokeWidth = sw,
        cap = StrokeCap.Round,
        alpha = 0.95f
    )
}
