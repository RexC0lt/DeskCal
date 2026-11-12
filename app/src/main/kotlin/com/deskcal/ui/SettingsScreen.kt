package com.deskcal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskcal.caldav.CalDavException
import com.deskcal.data.Settings
import com.deskcal.data.db.CalendarEntity
import com.deskcal.di.AppContainer
import com.deskcal.ui.theme.DeskCalError
import com.deskcal.ui.theme.DeskCalOk
import com.deskcal.ui.theme.DeskCalWarn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onClose: () -> Unit) {
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle()
    var calendars by remember { mutableStateOf<List<CalendarEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        container.repository.observeCalendars().collect { calendars = it }
    }
    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var username by remember(settings.username) { mutableStateOf(settings.username) }
    var password by remember(settings.password) { mutableStateOf(settings.password) }
    var trustSelfSigned by remember(settings.trustSelfSigned) { mutableStateOf(settings.trustSelfSigned) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save(block: Settings.() -> Settings) = container.settings.update(block)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Server") }
            item {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; save { copy(serverUrl = it.trim()) } },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://calendar.example.lan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; save { copy(username = it.trim()) } },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; save { copy(password = it) } },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SwitchRow(
                    title = "Trust self-signed certificates",
                    checked = trustSelfSigned,
                    onCheckedChange = { checked ->
                        trustSelfSigned = checked
                        save { copy(trustSelfSigned = checked) }
                    },
                )
            }
            if (trustSelfSigned) {
                item {
                    Text(
                        text = "Warning: this disables certificate validation for the server " +
                            "connection. Only enable this on a network you trust.",
                        color = DeskCalWarn,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && serverUrl.isNotBlank() && username.isNotBlank(),
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                val result = container.repository.testConnection(
                                    serverUrl.trim(), username.trim(), password, trustSelfSigned,
                                )
                                message = result.fold(
                                    onSuccess = { "Connection OK — found ${it.size} calendar(s)." },
                                    onFailure = { CalDavException.describe(it) },
                                )
                                messageOk = result.isSuccess
                                busy = false
                            }
                        },
                    ) {
                        Text(if (busy) "Testing…" else "Test connection")
                    }
                    TextButton(
                        enabled = !busy && serverUrl.isNotBlank() && username.isNotBlank(),
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                try {
                                    val found = container.repository.discoverCalendars(
                                        serverUrl.trim(), username.trim(), password, trustSelfSigned,
                                    )
                                    message = "Discovered ${found.size} calendar(s)."
                                    messageOk = true
                                } catch (e: Throwable) {
                                    message = CalDavException.describe(e)
                                    messageOk = false
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Text("Discover calendars")
                    }
                }
            }
            if (message != null) {
                item {
                    Text(
                        text = message!!,
                        color = if (messageOk) DeskCalOk else DeskCalError,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { SectionTitle("Calendars") }
            if (calendars.isEmpty()) {
                item {
                    Text(
                        text = "No calendars discovered yet. Use \"Discover calendars\" above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(calendars, key = { it.path }) { cal ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = settings.selectedCalendars.contains(cal.path),
                        onCheckedChange = { checked ->
                            save {
                                copy(
                                    selectedCalendars = if (checked) {
                                        selectedCalendars + cal.path
                                    } else {
                                        selectedCalendars - cal.path
                                    }
                                )
                            }
                        },
                    )
                    Column {
                        Text(cal.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            cal.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle("Display") }
            item {
                SwitchRow(
                    title = "24-hour time",
                    checked = settings.timeFormat24h,
                    onCheckedChange = { save { copy(timeFormat24h = it) } },
                )
            }
            item {
                SwitchRow(
                    title = "Hide days without events",
                    checked = settings.hideEmptyDays,
                    onCheckedChange = { save { copy(hideEmptyDays = it) } },
                )
            }
            item {
                OutlinedTextField(
                    value = settings.lookAheadWeeks.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.coerceIn(1, 52)?.let { save { copy(lookAheadWeeks = it) } }
                    },
                    label = { Text("Look-ahead (weeks)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { SectionTitle("Refresh") }
            item {
                Column {
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.refreshIntervalMinutes == minutes,
                                onClick = { save { copy(refreshIntervalMinutes = minutes) } },
                            )
                            Text("Every ${"$"}minutes minutes")
                        }
                    }
                }
            }

            item { SectionTitle("Desk behaviour") }
            item {
                SwitchRow(
                    title = "Night mode (dim screen)",
                    checked = settings.nightModeEnabled,
                    onCheckedChange = { save { copy(nightModeEnabled = it) } },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.nightModeStartHour.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.coerceIn(0, 23)?.let { save { copy(nightModeStartHour = it) } }
                        },
                        label = { Text("From hour") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = settings.nightModeEndHour.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.coerceIn(0, 23)?.let { save { copy(nightModeEndHour = it) } }
                        },
                        label = { Text("To hour") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                SwitchRow(
                    title = "Launch on boot",
                    checked = settings.launchOnBoot,
                    onCheckedChange = { save { copy(launchOnBoot = it) } },
                )
            }
            item {
                SwitchRow(
                    title = "Burn-in protection (slow pixel shift)",
                    checked = settings.burnInProtection,
                    onCheckedChange = { save { copy(burnInProtection = it) } },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
