package com.example.myapplication
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.window.Dialog
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomePalette.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(28.dp))

            EmployeeProfileCard(
                modifier = Modifier.padding(horizontal = 20.dp),
                loading = profileLoading,
                profile = profile,
                message = profileMessage
            )

            Spacer(Modifier.height(16.dp))

            CalendarActivityCard(
                modifier = Modifier.padding(horizontal = 20.dp),
                month = currentMonth,
                today = today,
                loading = activityLoading,
                activeDates = activityDates,
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

            Spacer(Modifier.height(16.dp))

            StatsRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                loading = statsLoading,
                stats = totalStats
            )

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("//", color = HomePalette.teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
                Text("QUICK ACTIONS", color = HomePalette.muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiTuning.screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(UiTuning.rowSpacing, Alignment.CenterHorizontally)
            ) {
                InteractiveBoxItem("Scan QR code", "LOOK UP BOX", navController, "scanner_screen", true)
                InteractiveBoxItem("New box", "START SCANNING", navController, "new_box_screen", false)
            }

            Spacer(Modifier.height(32.dp))
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
    message: String?
) {
    val shape = RoundedCornerShape(16.dp)
    if (loading) {
        CardShimmer(modifier = modifier)
        return
    }
    Box(
        modifier = modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(HomePalette.surface)
            .border(1.dp, HomePalette.border, shape)
            .padding(18.dp)
    ) {
        if (profile == null) {
            Text(
                text = message ?: "Employee profile unavailable",
                color = HomePalette.muted,
                fontSize = 13.sp
            )
        } else {
            Column {
                Text(
                    text = if (profile.fullName.isBlank()) "Unnamed Employee" else profile.fullName,
                    color = HomePalette.white,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(HomePalette.teal.copy(alpha = 0.18f))
                            .border(1.dp, HomePalette.teal.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = profile.role.ifBlank { "Role unknown" },
                            color = HomePalette.teal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (profile.email.isBlank()) "Email not available" else profile.email,
                    color = HomePalette.muted,
                    fontSize = 12.sp
                )
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
    activeDates: List<LocalDate>,
    onDayClick: (LocalDate) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Box(
        modifier = modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(HomePalette.surface)
            .border(1.dp, HomePalette.border, shape)
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$monthLabel ${month.year}",
                    color = HomePalette.white,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Activity",
                    color = HomePalette.muted,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            if (loading) {
                CalendarShimmer()
            } else {
                CalendarGrid(
                    month = month,
                    today = today,
                    activeDates = activeDates,
                    onDayClick = onDayClick
                )
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    activeDates: List<LocalDate>,
    onDayClick: (LocalDate) -> Unit
) {
    val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val activeSet = remember(activeDates) { activeDates.toSet() }
    val firstOfMonth = month.atDay(1)
    val startOffset = firstOfMonth.dayOfWeek.value % 7
    val totalDays = month.lengthOfMonth()

    val days = buildList<LocalDate?> {
        repeat(startOffset) { add(null) }
        for (day in 1..totalDays) {
            add(month.atDay(day))
        }
        while (size % 7 != 0) add(null)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEach { label ->
            Text(
                text = label,
                color = HomePalette.muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    days.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clickable(enabled = date != null) { date?.let(onDayClick) },
                    contentAlignment = Alignment.Center
                ) {
                    if (date != null) {
                        val isToday = date == today
                        val isActive = activeSet.contains(date)
                        val color = when {
                            isToday -> HomePalette.white
                            isActive -> HomePalette.teal
                            else -> HomePalette.muted
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                color = color,
                                fontSize = 13.sp,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(4.dp))
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
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
    }
}

@Composable
private fun CalendarShimmer() {
    val brush = rememberShimmerBrush()
    Column {
        repeat(6) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(brush)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatsRow(
    modifier: Modifier,
    loading: Boolean,
    stats: TotalStats?
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loading) {
            StatChipShimmer(modifier = Modifier.weight(1f))
            StatChipShimmer(modifier = Modifier.weight(1f))
        } else {
            val totalBoxes = stats?.totalBoxes ?: 0
            val totalItems = stats?.totalItems ?: 0
            StatChip(value = totalBoxes.toString(), label = "Total boxes scanned", modifier = Modifier.weight(1f))
            StatChip(value = totalItems.toString(), label = "Total items scanned", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .shadow(4.dp, shape)
            .clip(shape)
            .background(HomePalette.surfaceAlt)
            .border(1.dp, HomePalette.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(value, color = HomePalette.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = HomePalette.muted, fontSize = 11.sp)
    }
}

@Composable
private fun StatChipShimmer(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .border(1.dp, HomePalette.border, RoundedCornerShape(16.dp))
            .height(64.dp)
    )
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
    val configuration = LocalConfiguration.current
    val dialogWidth = configuration.screenWidthDp.dp * 0.8f
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, HomePalette.border, RoundedCornerShape(16.dp)),
                color = HomePalette.surfaceAlt,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selectedDay.format(formatter),
                        color = HomePalette.white,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    when {
                        loading -> Text("Loading activity…", color = HomePalette.muted, fontSize = 13.sp)
                        stats == null || stats.boxes == 0 -> Text(
                            "No scans recorded for this day",
                            color = HomePalette.muted,
                            fontSize = 13.sp
                        )
                        else -> {
                            Text(
                                "Boxes scanned: ${stats.boxes}",
                                color = HomePalette.white,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Total items: ${stats.items}",
                                color = HomePalette.white,
                                fontSize = 13.sp
                            )
                        }
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
