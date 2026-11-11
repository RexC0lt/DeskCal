package com.deskcal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.deskcal.caldav.CalDavException
import com.deskcal.caldav.DiscoveredCalendar
import com.deskcal.di.AppContainer
import com.deskcal.ui.theme.DeskCalError
import com.deskcal.ui.theme.DeskCalWarn
import kotlinx.coroutines.launch

@Composable
fun SetupWizard(container: AppContainer, onFinished: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var trustSelfSigned by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var calendars by remember { mutableStateOf<List<DiscoveredCalendar>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "DeskCal setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (step == 1) {
                Text("Step 1 of 2 — connect to your Radicale server")
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://calendar.example.lan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = trustSelfSigned, onCheckedChange = { trustSelfSigned = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Trust self-signed certificates")
                }
                if (trustSelfSigned) {
                    Text(
                        text = "Warning: this disables certificate validation for the " +
                            "connection. Only enable this on a trusted local network.",
                        color = DeskCalWarn,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                val found = container.repository.discoverCalendars(
                                    serverUrl.trim(), username.trim(), password, trustSelfSigned,
                                )
                                calendars = found
                                if (found.isEmpty()) {
                                    error = "Connected, but no calendars were found for this account."
                                } else {
                                    step = 2
                                }
                            } catch (e: Throwable) {
                                error = CalDavException.describe(e)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && serverUrl.isNotBlank() && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Connecting…" else "Connect")
                }
                if (error != null) {
                    Text(error!!, color = DeskCalError, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Step 2 of 2 — choose the calendars to display")
                if (calendars.isEmpty()) {
                    Text("No calendars discovered.")
                }
                calendars.forEach { cal ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = selected.contains(cal.path),
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + cal.path else selected - cal.path
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
                Button(
                    onClick = {
                        container.settings.update {
                            copy(
                                serverUrl = serverUrl.trim(),
                                username = username.trim(),
                                password = password,
                                trustSelfSigned = trustSelfSigned,
                                selectedCalendars = selected,
                                setupCompleted = true,
                            )
                        }
                        scope.launch { container.repository.syncNow() }
                        onFinished()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Finish")
                }
            }
        }
    }
}
