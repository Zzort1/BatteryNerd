package com.evagames.batterynerd.ui

import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel
import com.evagames.batterynerd.data.BatterySnapshot

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDashboard(viewModel: BatteryViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snapshot = uiState.snapshot

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Battery Nerd")
                        Text(
                            text = "Fast battery telemetry dashboard",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Reading battery telemetry…")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    HeroCard(
                        snapshot = snapshot,
                        sampleIntervalMs = uiState.sampleIntervalMs,
                        maxObservedPowerMw = uiState.maxObservedPowerMw,
                        minObservedPowerMw = uiState.minObservedPowerMw
                    )
                }
                item {
                    SamplingSelector(
                        selected = uiState.sampleIntervalMs,
                        onSelected = viewModel::setSampleInterval
                    )
                }
                item {
                    SectionTitle("Live interpretation")
                }
                item {
                    InsightGrid(snapshot)
                }
                item {
                    SectionTitle("Raw and derived telemetry")
                }
                items(detailRows(snapshot)) { row ->
                    MetricRow(label = row.first, value = row.second)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    snapshot: BatterySnapshot,
    sampleIntervalMs: Long,
    maxObservedPowerMw: Float?,
    minObservedPowerMw: Float?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Net battery-side charge power",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        formatPower(snapshot.netPowerMw),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Rounded.Bolt, contentDescription = null)
            }
            LinearProgressIndicator(
                progress = { ((snapshot.percent ?: 0f) / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${formatPercent(snapshot.percent)} battery") },
                    leadingIcon = {
                        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null)
                    })
                AssistChip(onClick = {}, label = { Text(sourceLabel(snapshot.plugType)) })
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel(snapshot.status, snapshot.isCharging)) })
                AssistChip(onClick = {}, label = { Text("Sample ${sampleIntervalMs} ms") })
            }
            Text(
                text = "Observed window: min ${formatPower(minObservedPowerMw)} • max ${
                    formatPower(
                        maxObservedPowerMw
                    )
                }",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SamplingSelector(selected: Long, onSelected: (Long) -> Unit) {
    val options = listOf(250L, 500L, 1000L, 2000L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Refresh cadence", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { interval ->
                FilterChip(
                    selected = selected == interval,
                    onClick = { onSelected(interval) },
                    label = { Text("${interval} ms") }
                )
            }
        }
        Text(
            "Lower intervals feel more live, but many devices only refresh battery telemetry at hardware-defined rates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InsightGrid(snapshot: BatterySnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NerdCard("Charge current now", formatCurrent(snapshot.currentNowMa), Icons.Rounded.Bolt)
        NerdCard(
            "Charge current average",
            formatCurrent(snapshot.currentAverageMa),
            Icons.Rounded.Memory
        )
        NerdCard(
            "Battery temperature",
            formatTemperature(snapshot.temperatureC),
            Icons.Rounded.Thermostat
        )
        NerdCard("Voltage", formatVoltage(snapshot.voltageV), Icons.Rounded.BatteryChargingFull)
    }
}

@Composable
private fun NerdCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(icon, contentDescription = null)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun detailRows(snapshot: BatterySnapshot): List<Pair<String, String>> {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)
        .withZone(ZoneId.systemDefault())

    return listOf(
        "Captured at" to formatter.format(snapshot.capturedAt),
        "Battery level (intent)" to nullable(snapshot.level?.toString()),
        "Battery scale" to nullable(snapshot.scale?.toString()),
        "Battery percent (derived)" to formatPercent(snapshot.percent),
        "BatteryManager capacity" to nullable(snapshot.capacityPercent?.let { "$it %" }),
        "Status" to statusLabel(snapshot.status, snapshot.isCharging),
        "Health" to healthLabel(snapshot.health),
        "Plug type" to sourceLabel(snapshot.plugType),
        "Is charging" to snapshot.isCharging.toString(),
        "Current now" to nullable(snapshot.currentNowUa?.let { "$it µA" }),
        "Current average" to nullable(snapshot.currentAverageUa?.let { "$it µA" }),
        "Current now (mA)" to formatCurrent(snapshot.currentNowMa),
        "Current average (mA)" to formatCurrent(snapshot.currentAverageMa),
        "Voltage" to nullable(snapshot.voltageMv?.let { "$it mV" }),
        "Voltage (derived)" to formatVoltage(snapshot.voltageV),
        "Estimated net power" to formatPower(snapshot.netPowerMw),
        "Charge counter" to nullable(snapshot.chargeCounterUah?.let { "$it µAh" }),
        "Energy counter" to nullable(snapshot.energyCounterNwh?.let { "$it nWh" }),
        "Stored energy (derived)" to nullable(snapshot.storedEnergyMwh?.let {
            String.format(
                Locale.US,
                "%.2f mWh",
                it
            )
        }),
        "Charge time remaining" to nullable(snapshot.chargeTimeRemainingMs?.let { formatDuration(it) }),
        "Temperature" to nullable(snapshot.temperatureDeciC?.let { "$it deci-°C" }),
        "Temperature (derived)" to formatTemperature(snapshot.temperatureC),
        "Technology" to nullable(snapshot.technology),
        "Battery present" to nullable(snapshot.present?.toString()),
        "Cycle count" to nullable(snapshot.cycleCount?.toString()),
        "Charging policy" to nullable(snapshot.chargingPolicy?.toString()),
        "State of health" to nullable(snapshot.stateOfHealthPercent?.let { "$it %" })
    )
}

private fun nullable(value: String?): String = value ?: "Unsupported / unavailable"

private fun formatPercent(percent: Float?): String =
    percent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"

private fun formatCurrent(valueMa: Float?): String =
    valueMa?.let { String.format(Locale.US, "%.1f mA", it) } ?: "Unsupported / unavailable"

private fun formatVoltage(valueV: Float?): String =
    valueV?.let { String.format(Locale.US, "%.3f V", it) } ?: "Unsupported / unavailable"

private fun formatTemperature(valueC: Float?): String =
    valueC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Unsupported / unavailable"

private fun formatPower(powerMw: Float?): String =
    powerMw?.let { String.format(Locale.US, "%.2f mW", it) } ?: "Unsupported / unavailable"

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return "%dh %02dm".format(Locale.US, hours, minutes)
}

private fun statusLabel(status: Int?, isCharging: Boolean): String = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
    BatteryManager.BATTERY_STATUS_FULL -> "Full"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> if (isCharging) "On power / full" else "Not charging"
    BatteryManager.BATTERY_STATUS_UNKNOWN, null -> if (isCharging) "Charging" else "Unknown"
    else -> "Status $status"
}

private fun sourceLabel(source: Int?): String = when (source) {
    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
    BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
    0, null -> "On battery"
    else -> "Plug $source"
}

private fun healthLabel(health: Int?): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
    BatteryManager.BATTERY_HEALTH_UNKNOWN, null -> "Unknown"
    else -> "Health $health"
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}
