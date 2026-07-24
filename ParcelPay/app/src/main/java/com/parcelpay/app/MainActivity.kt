package com.parcelpay.app

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.parcelpay.app.ui.navigation.ParcelPayNavGraph
import com.parcelpay.app.ui.theme.ParcelPayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            ParcelPayTheme {
                ParcelPayNavGraph()
            }
        }
    }
}
