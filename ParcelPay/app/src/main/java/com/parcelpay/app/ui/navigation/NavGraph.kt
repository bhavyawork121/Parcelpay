package com.parcelpay.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.parcelpay.app.ui.screens.CaptureScreen
import com.parcelpay.app.ui.screens.HomeScreen
import com.parcelpay.app.ui.screens.ReviewScreen
import com.parcelpay.app.ui.screens.SettingsScreen
import com.parcelpay.app.viewmodel.ParcelPayViewModel

@Composable
fun ParcelPayNavGraph(
    navController: NavHostController = rememberNavController(),
    viewModel: ParcelPayViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToCapture = { navController.navigate("capture") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            val settingsViewModel: com.parcelpay.app.viewmodel.SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("history") {
            val historyViewModel: com.parcelpay.app.viewmodel.HistoryViewModel = viewModel()
            val settingsViewModel: com.parcelpay.app.viewmodel.SettingsViewModel = viewModel()
            com.parcelpay.app.ui.screens.HistoryScreen(
                viewModel = historyViewModel,
                settingsViewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("capture") {
            CaptureScreen(
                onNavigateToReview = { path ->
                    val encodedPath = java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8.toString())
                    navController.navigate("review/$encodedPath")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "review/{photoPath}",
            arguments = listOf(androidx.navigation.navArgument("photoPath") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val photoPath = backStackEntry.arguments?.getString("photoPath")
            val decodedPath = photoPath?.let { java.net.URLDecoder.decode(it, java.nio.charset.StandardCharsets.UTF_8.toString()) }
            val reviewViewModel: com.parcelpay.app.viewmodel.ReviewViewModel = viewModel()
            val historyViewModel: com.parcelpay.app.viewmodel.HistoryViewModel = viewModel()
            val settingsViewModel: com.parcelpay.app.viewmodel.SettingsViewModel = viewModel()
            
            val context = androidx.compose.ui.platform.LocalContext.current
            val qrImagePath by settingsViewModel.qrImagePath.collectAsState(initial = null)
            
            ReviewScreen(
                photoPath = decodedPath,
                viewModel = reviewViewModel,
                onSend = { phoneNumber ->
                    if (decodedPath != null) {
                        historyViewModel.addParcel(decodedPath, phoneNumber)
                        com.parcelpay.app.ui.screens.sendViaWhatsApp(context, decodedPath, qrImagePath, phoneNumber)
                    }
                    navController.navigate("home") { popUpTo("home") { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
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
