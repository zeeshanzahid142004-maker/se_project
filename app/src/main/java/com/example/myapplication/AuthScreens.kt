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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
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
                painter = painterResource(id = R.mipmap.ic_launcher),
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun navigateToHome() {
        navController.navigate("inventory_home") {
            popUpTo("sign_in") { inclusive = true }
        }
    }

    fun submit() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            errorMessage = "Email and password are required."
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
            .background(authBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(56.dp))
            Text(
                "Employee Sign In",
                color = authText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Use your assigned account to continue.",
                color = authMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = authMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = authAccent,
                    unfocusedBorderColor = authBorder,
                    focusedTextColor = authText,
                    unfocusedTextColor = authText,
                    cursorColor = authAccent,
                    focusedContainerColor = authSurface,
                    unfocusedContainerColor = authSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = authMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = authAccent,
                    unfocusedBorderColor = authBorder,
                    focusedTextColor = authText,
                    unfocusedTextColor = authText,
                    cursorColor = authAccent,
                    focusedContainerColor = authSurface,
                    unfocusedContainerColor = authSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Forgot password?",
                    color = authAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(
                        remember { MutableInteractionSource() },
                        null
                    ) { navController.navigate("forgot_password") }
                )
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { submit() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = authAccent,
                    disabledContainerColor = authAccent.copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Sign In", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(authSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, authError.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(authError, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = errorMessage.orEmpty(),
                        color = authError,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Text(
            text = "Skip for now",
            color = authMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
                .clickable(remember { MutableInteractionSource() }, null) { navigateToHome() }
        )
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
