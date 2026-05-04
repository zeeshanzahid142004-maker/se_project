package com.example.myapplication

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
fun FullCalendarSheet(
    activityDates: Collection<LocalDate>,
    onDaySelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val sheetOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing), // TWEAK: sheet slide duration
        label = "sheetSlide"
    )

    // Compute full month grid
    val today = remember { LocalDate.now() }
    val firstOfMonth = remember { today.withDayOfMonth(1) }
    val daysInMonth = remember { today.lengthOfMonth() }
    // firstDayOffset: 0=Sun ... 6=Sat — used to pad start of grid
    val firstDayOffset = remember { firstOfMonth.dayOfWeek.value % 7 } // Sun=0
    // Total cells = offset + days, rounded up to full weeks
    val totalCells = firstDayOffset + daysInMonth

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)) // TWEAK: scrim alpha
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Sheet card slides up from bottom
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (sheetOffset * 600).dp) // TWEAK: slide distance
                    .clickable(remember { MutableInteractionSource() }, null) { /* consume click */ },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), // TWEAK: sheet radius
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 20.dp, // TWEAK: sheet horizontal padding
                        vertical = 24.dp    // TWEAK: sheet vertical padding
                    )
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)  // TWEAK: handle width
                            .height(4.dp)  // TWEAK: handle height
                            .background(Color(0xFF30363D), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(16.dp)) // TWEAK: handle bottom gap

                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            color = Color(0xFF2DD4BF),
                            fontSize = 18.sp, // TWEAK: sheet month title size
                            fontWeight = FontWeight.Bold
                        )
                        // Close button
                        Box(
                            modifier = Modifier
                                .size(32.dp) // TWEAK: close button size
                                .clip(CircleShape)
                                .background(Color(0xFF1C2333))
                                .border(1.dp, Color(0xFF30363D), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color(0xFF8B949E), fontSize = 12.sp) // TWEAK: close icon size
                        }
                    }

                    Spacer(Modifier.height(16.dp)) // TWEAK: gap below header

                    // Day of week headers
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                            Text(
                                text = d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp, // TWEAK: day header size
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp)) // TWEAK: gap below day headers

                    // Full month grid
                    // Renders rows of 7 cells, empty cells for offset days
                    val weeks = ceil(totalCells / 7.0).toInt()
                    repeat(weeks) { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            repeat(7) { col ->
                                val cellIndex = week * 7 + col
                                val dayNum = cellIndex - firstDayOffset + 1
                                val isValid = dayNum in 1..daysInMonth
                                val date = if (isValid) firstOfMonth.withDayOfMonth(dayNum) else null
                                val isActive = date != null && date in activityDates
                                val isToday = date == today

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp) // TWEAK: cell vertical padding
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (date != null)
                                                Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { onDaySelected(date) }
                                            else Modifier
                                        )
                                        .background(
                                            when {
                                                isActive -> Color(0xFF2DD4BF).copy(alpha = 0.06f) // TWEAK: active cell bg alpha
                                                else -> Color.Transparent
                                            }
                                        )
                                        .padding(vertical = 6.dp), // TWEAK: cell inner padding
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isValid) "$dayNum" else "",
                                        color = when {
                                            !isValid -> Color.Transparent
                                            isToday -> Color(0xFF2DD4BF)
                                            isActive -> Color(0xFF2DD4BF)
                                            else -> Color(0xFF8B949E)
                                        },
                                        fontSize = 13.sp, // TWEAK: day number size
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.height(3.dp)) // TWEAK: gap number to dot
                                    Box(
                                        modifier = Modifier
                                            .size(if (isActive) 5.dp else 0.dp) // TWEAK: dot size
                                            .background(Color(0xFF2DD4BF), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp)) // TWEAK: bottom breathing room

                    // Legend row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp) // TWEAK: legend dot size
                                .background(Color(0xFF2DD4BF), CircleShape)
                        )
                        Spacer(Modifier.width(6.dp)) // TWEAK: legend dot gap
                        Text(
                            text = "Scan activity",
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp // TWEAK: legend text size
                        )
                    }
                    Spacer(Modifier.height(16.dp)) // TWEAK: legend bottom gap
                }
            }
        }
    }
}
