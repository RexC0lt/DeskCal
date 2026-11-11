package com.deskcal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskcal.di.AppContainer

@Composable
fun DeskCalRoot(viewModel: WeekViewModel, container: AppContainer) {
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    when {
        !settings.setupCompleted -> SetupWizard(container = container, onFinished = {})
        showSettings -> SettingsScreen(container = container, onClose = { showSettings = false })
        else -> WeekScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
    }
}
