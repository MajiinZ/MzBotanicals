package org.mz.killrs

import android.view.ViewGroup
import android.widget.ImageView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen only ONCE
        val splashScreen = installSplashScreen()

        // Exit animation
        splashScreen.setOnExitAnimationListener { splashProvider ->
            val root = window.decorView as ViewGroup
            val artwork = ImageView(this).apply {
                setImageResource(R.drawable.splash_artwork)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            root.addView(artwork, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            splashProvider.remove()
            artwork.animate().alpha(0f).setDuration(650L)
                .withEndAction { root.removeView(artwork) }.start()
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        super.onCreate(savedInstanceState)
        setContent {
            App() // ✅ Your composable app entry
        }
    }
}
