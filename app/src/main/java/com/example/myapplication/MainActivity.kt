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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

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

                composable("scanner_screen") {
                    ScannerScreen(navController = navController)
                }

                composable("new_box_screen") {
                    NewBoxScreen(navController = navController)
                }

                // Dynamic route: boxId is the Room primary key of the created/scanned box
                composable(
                    route = "qr_display_screen/{boxId}",
                    arguments = listOf(navArgument("boxId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val boxId = backStackEntry.arguments?.getLong("boxId") ?: 0L
                    QrDisplayScreen(navController = navController, boxId = boxId)
                }
            }
        }
    }
}