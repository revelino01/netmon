package com.netmon.ui

import android.content.Intent
import android.net.VpnService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netmon.domain.AppTrafficStats
import com.netmon.domain.DashboardUiState
import com.netmon.vpn.VpnCaptureService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NetMon") },
                actions = {
                    IconButton(onClick = { viewModel.toggleMonitoring() }) {
                        Icon(
                            if (state.isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (state.isMonitoring) "Stop" else "Start"
                        )
                    }
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Summary cards
            SummaryCards(state)

            Spacer(Modifier.height(16.dp))

            // Per-app traffic list
            if (state.apps.isEmpty() && state.isMonitoring) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Waiting for traffic…")
                    }
                }
            } else if (state.apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Press ▶ to start monitoring", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.apps, key = { it.uid }) { app ->
                        AppTrafficRow(app)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCards(state: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard("↓ Download", formatBytes(state.totalRxBytes), Modifier.weight(1f))
        SummaryCard("↑ Upload", formatBytes(state.totalTxBytes), Modifier.weight(1f))
        SummaryCard("Apps", "${state.activeAppCount}", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppTrafficRow(app: AppTrafficStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("↓ ${formatBytes(app.totalRxBytes)}", style = MaterialTheme.typography.bodySmall)
                Text("↑ ${formatBytes(app.totalTxBytes)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}