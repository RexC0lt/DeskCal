package com.deskcal

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deskcal.di.AppContainer
import com.deskcal.ui.DeskCalRoot
import com.deskcal.ui.WeekViewModel
import com.deskcal.ui.theme.DeskCalTheme
import com.deskcal.week.WeekLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val viewModel: WeekViewModel by viewModels {
        (application as DeskCalApp).container.weekViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent {
            DeskCalTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DeskCalRoot(
                        viewModel = viewModel,
                        container = (application as DeskCalApp).container,
                    )
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    applyNightMode()
                    delay(NIGHT_CHECK_INTERVAL_MS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        applyNightMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun applyNightMode() {
        val container: AppContainer = (application as DeskCalApp).container
        val s = container.settings.settingsFlow.value
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = s.nightModeEnabled &&
            WeekLogic.isInNightMode(hour, s.nightModeStartHour, s.nightModeEndHour)
        val lp = window.attributes
        lp.screenBrightness =
            if (night) NIGHT_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    companion object {
        private const val NIGHT_BRIGHTNESS = 0.06f
        private const val NIGHT_CHECK_INTERVAL_MS = 30_000L
    }
}
