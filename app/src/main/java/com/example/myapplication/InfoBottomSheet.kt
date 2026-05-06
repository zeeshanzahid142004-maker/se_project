package com.example.myapplication

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ibsRed     = Color(0xFFE53E3E)
private val ibsSurface = Color(0xFF161B22)
private val ibsBorder  = Color(0xFF30363D)
private val ibsWhite   = Color(0xFFF0F6FC)
private val ibsMuted   = Color(0xFF8B949E)

/**
 * A reusable bottom sheet for displaying informational messages such as
 * validation errors (e.g. "Scan an item first") or success confirmations
 * (e.g. "Complaint Submitted").
 *
 * @param title      Bold heading shown at the top of the sheet.
 * @param message    Dimmer subtitle text shown beneath the title.
 * @param buttonText Label for the full-width red action button at the bottom.
 * @param onDismiss  Called when the red button is clicked or the scrim is tapped.
 */
@Composable
fun InfoBottomSheet(
    title: String,
    message: String,
    buttonText: String,
    onDismiss: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val scrimAlpha by animateFloatAsState(
        targetValue   = if (entered) 0.6f else 0f,
        animationSpec = tween(280),
        label         = "ibsScrim"
    )
    val sheetScale by animateFloatAsState(
        targetValue   = if (entered) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "ibsScale"
    )
    val sheetAlpha by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(220),
        label         = "ibsAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .scale(sheetScale)
                .alpha(sheetAlpha)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(ibsSurface)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Pill-shaped drag handle
            Box(
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(ibsBorder, RoundedCornerShape(2.dp))
            )

            // Bold title
            Text(
                text       = title,
                color      = ibsWhite,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Dimmer message / subtitle
            Text(
                text      = message,
                color     = ibsMuted,
                fontSize  = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(bottom = 28.dp)
            )

            // Full-width pill-shaped red action button
            Button(
                onClick  = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ibsRed)
            ) {
                Text(
                    text       = buttonText,
                    color      = Color.White,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
