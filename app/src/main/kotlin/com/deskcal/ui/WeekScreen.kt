package com.deskcal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.SwipeRefresh
import androidx.compose.material.pullrefresh.rememberSwipeRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskcal.data.SyncStatus
import com.deskcal.ui.theme.DeskCalError
import com.deskcal.ui.theme.DeskCalOk
import com.deskcal.ui.theme.DeskCalWarn
import com.deskcal.week.DayUi
import com.deskcal.week.EventUi
import com.deskcal.week.TimeFormat
import com.deskcal.week.WeekLogic
import com.deskcal.week.buildDays
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WeekScreen(viewModel: WeekViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = WeekViewModel.CENTER_PAGE) { WeekViewModel.PAGE_COUNT }

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            viewModel.onPageSelected(page, state.currentWeekStart)
        }
    }

    LaunchedEffect(state.currentWeekStart) {
        pagerState.animateScrollToPage(WeekViewModel.CENTER_PAGE)
    }

    var shift by remember { mutableStateOf(IntOffset(0, 0)) }
    LaunchedEffect(state.settings.burnInProtection) {
        if (state.settings.burnInProtection) {
            while (true) {
                delay(BURN_IN_INTERVAL_MS)
                shift = if (shift == IntOffset(0, 0)) IntOffset(2, 1) else IntOffset(0, 0)
            }
        } else {
            shift = IntOffset(0, 0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TimeFormat.weekLabel(state.displayedWeekStart)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .offset { shift },
        ) {
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = state.syncStatus is SyncStatus.Syncing),
                onRefresh = { viewModel.refresh() },
            ) {
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    HorizontalPager(state = pagerState) { page ->
                        val weekStart = WeekLogic.addWeeks(
                            state.currentWeekStart,
                            page - WeekViewModel.CENTER_PAGE,
                        )
                        WeekPage(weekStart = weekStart, state = state)
                    }
                }
            }
            StatusLine(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                state = state,
            )
        }
    }
}

@Composable
private fun WeekPage(weekStart: Long, state: WeekUiState) {
    val days = remember(weekStart, state.occurrences, state.now) {
        buildDays(weekStart, state.occurrences, state.now)
    }
    val visibleDays = if (state.settings.hideEmptyDays) {
        days.filter { it.events.isNotEmpty() || it.isToday }
    } else {
        days
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(visibleDays, key = { it.startMillis }) { day ->
            DaySection(day = day, hour24 = state.settings.timeFormat24h)
        }
    }
}

@Composable
private fun DaySection(day: DayUi, hour24: Boolean) {
    val today = day.isToday
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (today) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = TimeFormat.dayHeader(day.startMillis) + if (today) "  •  TODAY" else "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (today) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (day.events.isEmpty()) {
                Text(
                    text = "No events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.6f),
                )
            }
            day.events.forEach { event ->
                EventRow(event = event, hour24 = hour24)
            }
        }
    }
}

@Composable
private fun EventRow(event: EventUi, hour24: Boolean) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .alpha(if (event.isPast) 0.45f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = TimeFormat.timeRange(event.startMillis, event.endMillis, event.allDay, hour24),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(110.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            if (event.location.isNotBlank()) {
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(modifier: Modifier = Modifier, state: WeekUiState) {
    val s = state.settings
    val nextSync = if (s.lastSyncAt > 0) {
        s.lastSyncAt + s.refreshIntervalMinutes * 60_000L
    } else {
        0L
    }
    val (statusText, statusColor) = when {
        !state.online -> "Offline — showing cached data" to DeskCalWarn
        state.syncStatus is SyncStatus.Syncing -> "Syncing…" to MaterialTheme.colorScheme.primary
        state.syncStatus is SyncStatus.Error -> "Sync error: ${(state.syncStatus as SyncStatus.Error).message}" to DeskCalError
        else -> "OK" to DeskCalOk
    }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TimeFormat.statusDate(state.now),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "  ·  " + if (nextSync > 0) {
                    "next sync ${TimeFormat.time(nextSync, s.timeFormat24h)}"
                } else {
                    "next sync —"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor,
            )
        }
    }
}

private const val BURN_IN_INTERVAL_MS = 60_000L
