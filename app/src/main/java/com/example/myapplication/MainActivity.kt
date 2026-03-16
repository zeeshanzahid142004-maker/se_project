package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "inventory_home",
                enterTransition = {
                    fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 10 }
                },
                exitTransition = {
                    fadeOut(tween(220, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    fadeIn(tween(220, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it / 10 }
                }
            ) {

                composable("inventory_home") {
                    InventoryScreen(navController = navController)
                }

                // ✅ Fixed: routes to real ScannerScreen
                composable("scanner_screen") {
                   ScannerScreen(navController = navController)
                }

                composable("new_box_screen") {
                    NewBoxScreen(navController = navController)
                }

                composable("qr_display_screen") {
                    QrDisplayScreen(navController = navController)
                }
            }
        }
    }
}