package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "inventory_home") {

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