import os
import re

def update_file(path, replacements):
    with open(path, 'r') as f:
        content = f.read()
    for old, new in replacements:
        content = content.replace(old, new)
    with open(path, 'w') as f:
        f.write(content)

base = "/Users/bhavya_agarwal/Desktop/Numcheck/ParcelPay/app"

# 1. build.gradle.kts - add splashscreen
gradle_path = os.path.join(base, "build.gradle.kts")
update_file(gradle_path, [
    ('implementation("androidx.core:core-ktx:1.12.0")', 
     'implementation("androidx.core:core-ktx:1.12.0")\n    implementation("androidx.core:core-splashscreen:1.0.1")')
])

# 2. MainActivity.kt - add splashscreen
main_activity = os.path.join(base, "src/main/java/com/parcelpay/app/MainActivity.kt")
update_file(main_activity, [
    ('import android.os.Bundle', 'import android.os.Bundle\nimport androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen'),
    ('super.onCreate(savedInstanceState)', 'val splashScreen = installSplashScreen()\n        super.onCreate(savedInstanceState)')
])

# 3. NavGraph.kt - add transitions
nav_graph = os.path.join(base, "src/main/java/com/parcelpay/app/ui/navigation/NavGraph.kt")
update_file(nav_graph, [
    ('import androidx.compose.runtime.getValue', 
     'import androidx.compose.runtime.getValue\nimport androidx.compose.animation.AnimatedContentTransitionScope\nimport androidx.compose.animation.core.tween\nimport androidx.compose.animation.fadeIn\nimport androidx.compose.animation.fadeOut'),
    ('NavHost(\n        navController = navController,\n        startDestination = "home"\n    )',
     '''NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
    )''')
])
print("Done patching NavGraph and Splashscreen")
