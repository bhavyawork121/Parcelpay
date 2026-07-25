package com.parcelpay.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.parcelpay.app.ui.screens.*
import com.parcelpay.app.viewmodel.ReviewViewModel

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) }
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToCapture = { navController.navigate("capture") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        
        composable("capture") {
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReview = { imagePath -> 
                    val encodedPath = android.net.Uri.encode(imagePath)
                    navController.navigate("review?imagePath=$encodedPath") 
                }
            )
        }
        
        composable(
            route = "review?imagePath={imagePath}",
            arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val reviewViewModel: ReviewViewModel = viewModel()
            ReviewScreen(
                photoPath = imagePath,
                viewModel = reviewViewModel,
                onBack = { navController.popBackStack() },
                onSend = { phone ->
                    reviewViewModel.saveParcelToSupabase(phone)
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }
        
        composable("settings") {
            val settingsViewModel: com.parcelpay.app.viewmodel.SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
