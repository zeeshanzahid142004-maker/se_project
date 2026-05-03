package com.example.myapplication

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush

// Updated Supabase v3 Imports
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private const val TAG_AUTH = "AuthScreens"

private val authBg = Color(0xFF080C10)
private val authSurface = Color(0xFF161B22)
private val authAccent = Color(0xFF2DD4BF)
private val authError = Color(0xFFE53E3E)
private val authText = Color(0xFFF0F6FC)
private val authMuted = Color(0xFF8B949E)
private val authBorder = Color(0xFF30363D)

@Composable
fun LauncherScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        delay(2000)
        val hasSession = withContext(Dispatchers.IO) {
            runCatching { SupabaseModule.client.auth.currentSessionOrNull() != null }
                .getOrDefault(false)
        }
        val destination = if (hasSession) "inventory_home" else "sign_in"
        navController.navigate(destination) {
            popUpTo("launcher") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(authBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                // ✅ Use the foreground vector drawable, which Compose fully supports
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "App logo",
                modifier = Modifier.size(88.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = authText)) { append("Stack") }
                    withStyle(SpanStyle(color = authAccent)) { append("BoxAI") }
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "WAREHOUSE OPERATIONS",
                color = authMuted,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable

fun SignInScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // NEW: State to track password visibility
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun navigateToHome() {
        navController.navigate("inventory_home") {
            popUpTo("sign_in") { inclusive = true }
        }
    }

    fun submit() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            errorMessage = "Email is required."
            return
        }
        if (password.isBlank()) {
            errorMessage = "Password is required."
            return
        }
        if (isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            val accessAllowed = withContext(Dispatchers.IO) {
                runCatching {
                    val results = SupabaseModule.client.postgrest["warehouse_users"]
                        .select(Columns.list("id")) {
                            filter { eq("email", trimmedEmail) }
                        }
                        .decodeList<WarehouseUserRow>()
                    results.isNotEmpty()
                }
            }
            if (accessAllowed.isFailure) {
                Log.e(TAG_AUTH, "Access check failed: ${accessAllowed.exceptionOrNull()?.message}", accessAllowed.exceptionOrNull())
                errorMessage = "Unable to verify access. Please try again."
                isLoading = false
                return@launch
            }
            if (accessAllowed.getOrDefault(false).not()) {
                errorMessage = "Access denied. Contact your administrator."
                isLoading = false
                return@launch
            }
            val signInResult = withContext(Dispatchers.IO) {
                runCatching {
                    SupabaseModule.client.auth.signInWith(Email) {
                        this.email = trimmedEmail
                        this.password = password
                    }
                }
            }
            if (signInResult.isFailure) {
                Log.e(TAG_AUTH, "Sign-in failed: ${signInResult.exceptionOrNull()?.message}", signInResult.exceptionOrNull())
                errorMessage = "Sign in failed. Check your credentials."
                isLoading = false
                return@launch
            }
            isLoading = false
            navigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C10))
            .statusBarsPadding()
    ) {
        // TOP ZONE — hero branding (upper ~45% of screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f), // TWEAK: hero zone height fraction
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp)) // TWEAK: hero section top offset

            Box(
                modifier = Modifier
                    .size(72.dp) // TWEAK: logo size
                    .clip(RoundedCornerShape(16.dp)) // TWEAK: logo corner radius
                    .background(Color(0xFF161B22)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "App logo",
                    modifier = Modifier.size(52.dp) // TWEAK: foreground icon size inside box
                )
            }

            Spacer(Modifier.height(16.dp)) // TWEAK: gap between logo and app name

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFF0F6FC))) { append("Stack") }
                    withStyle(SpanStyle(color = Color(0xFF2DD4BF))) { append("BoxAI") }
                },
                fontSize = 32.sp, // TWEAK: app name font size
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp)) // TWEAK: gap between name and subtitle

            Text(
                "WAREHOUSE OPERATIONS",
                color = Color(0xFF8B949E),
                fontSize = 10.sp, // TWEAK: subtitle font size
                letterSpacing = 2.5.sp // TWEAK: subtitle letter spacing
            )
        }

        // BOTTOM ZONE — form card anchored to bottom
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Shadow wrapper for white glow effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp, // TWEAK: sign in card shadow depth
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        spotColor = Color.White.copy(alpha = 0.08f),    // TWEAK: sign in card spot shadow
                        ambientColor = Color.White.copy(alpha = 0.04f)  // TWEAK: sign in card ambient shadow
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), // TWEAK: card top corner radius
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 24.dp // TWEAK: material card lift
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 0.dp) // TWEAK: card horizontal padding
                            .padding(top = 16.dp, bottom = 32.dp)         // TWEAK: card inner padding
                    ) {
                        // Drag handle pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)  // TWEAK: drag handle width
                                .height(4.dp)  // TWEAK: drag handle height
                                .background(Color(0xFF30363D), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(20.dp)) // TWEAK: drag handle bottom gap

                        Text(
                            "Employee Sign In",
                            color = Color(0xFF2DD4BF),
                            fontSize = 22.sp, // TWEAK: card title font size
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Use your assigned credentials to continue",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp, // TWEAK: card subtitle font size
                            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp) // TWEAK: subtitle spacing
                        )

                        // Email field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", color = authMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor    = Color(0xFF2DD4BF),
                                unfocusedBorderColor  = Color(0xFF30363D),
                                focusedLabelColor     = Color(0xFF2DD4BF),
                                unfocusedLabelColor   = Color(0xFF8B949E),
                                focusedTextColor      = Color(0xFFF0F6FC),
                                unfocusedTextColor    = Color(0xFFF0F6FC),
                                cursorColor           = Color(0xFF2DD4BF),
                                focusedContainerColor   = Color(0xFF1C2333),
                                unfocusedContainerColor = Color(0xFF1C2333)
                            )
                        )

                        Spacer(Modifier.height(14.dp)) // TWEAK: gap between email and password fields

                        // Password field with toggle
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password", color = authMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = description, tint = authMuted)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor    = Color(0xFF2DD4BF),
                                unfocusedBorderColor  = Color(0xFF30363D),
                                focusedLabelColor     = Color(0xFF2DD4BF),
                                unfocusedLabelColor   = Color(0xFF8B949E),
                                focusedTextColor      = Color(0xFFF0F6FC),
                                unfocusedTextColor    = Color(0xFFF0F6FC),
                                cursorColor           = Color(0xFF2DD4BF),
                                focusedContainerColor   = Color(0xFF1C2333),
                                unfocusedContainerColor = Color(0xFF1C2333)
                            )
                        )

                        Spacer(Modifier.height(8.dp)) // TWEAK: gap below password field

                        // Forgot password — keep existing onClick
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "Forgot password?",
                                color = Color(0xFF2DD4BF),
                                fontSize = 12.sp, // TWEAK: forgot password font size
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { navController.navigate("forgot_password") }
                            )
                        }

                        Spacer(Modifier.height(20.dp)) // TWEAK: gap above sign in button

                        // SIGN IN BUTTON — bouncy + glossy teal
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f, // TWEAK: button press scale
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy, // TWEAK: button bounce damping
                                stiffness    = Spring.StiffnessLow              // TWEAK: button bounce stiffness
                            ),
                            label = "buttonScale"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp) // TWEAK: button height
                                .scale(buttonScale)
                                .shadow(
                                    elevation    = if (isPressed) 2.dp else 12.dp, // TWEAK: button shadow depth
                                    shape        = RoundedCornerShape(12.dp),
                                    spotColor    = Color(0xFF2DD4BF).copy(alpha = 0.35f), // TWEAK: button glow color
                                    ambientColor = Color(0xFF2DD4BF).copy(alpha = 0.15f)  // TWEAK: button ambient glow
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF3EECD4),  // TWEAK: button gradient top color (lighter teal)
                                            Color(0xFF1BA898)   // TWEAK: button gradient bottom color (darker teal)
                                        )
                                    )
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = !isLoading
                                ) { submit() },
                            contentAlignment = Alignment.Center
                        ) {
                            // Glossy sheen overlay — top half lighter
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f) // TWEAK: gloss overlay height fraction
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.12f), // TWEAK: gloss top alpha
                                                Color.White.copy(alpha = 0.0f)   // TWEAK: gloss bottom alpha
                                            )
                                        )
                                    )
                            )
                            if (isLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF080C10),
                                        modifier = Modifier.size(18.dp), // TWEAK: loading spinner size
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Signing in…",
                                        color = Color(0xFF080C10),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Text(
                                    "Sign In",
                                    color = Color(0xFF080C10), // dark text on teal
                                    fontSize = 15.sp, // TWEAK: button text size
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Error message — keep existing errorMessage logic
                        if (errorMessage != null) {
                            Spacer(Modifier.height(14.dp)) // TWEAK: gap above error message
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(8.dp).background(Color(0xFFE53E3E), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(errorMessage.orEmpty(), color = Color(0xFFE53E3E), fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp)) // TWEAK: gap above skip link

                        // Skip link — keep existing navigateToHome() onClick
                        Text(
                            "Skip for now",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp, // TWEAK: skip link font size
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { navigateToHome() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(authBg)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(remember { MutableInteractionSource() }, null) { navController.popBackStack() }
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = authText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back", color = authText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Forgot Password",
                color = authText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "To reset your password, please contact your warehouse administrator.",
                color = authMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Serializable
private data class WarehouseUserRow(
    val id: String
)