package au.com.evagames.batterynerd.ui

import android.os.BatteryManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import au.com.evagames.batterynerd.data.BatterySnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private enum class DashboardTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Visual("Visual", Icons.Rounded.WaterDrop),
    Home("Home", Icons.Rounded.Home),
    Session("Session", Icons.Rounded.Memory),
    Wireless("Wireless", Icons.Rounded.Bolt),
    Details("Details", Icons.AutoMirrored.Rounded.List)
}

private enum class SessionSubTab { Stopwatch, Benchmarks }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDashboard(
    viewModel: BatteryViewModel = viewModel(),
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snapshot = uiState.snapshot
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    var selectedTab by remember { mutableStateOf(DashboardTab.Visual) }

    if (isInPictureInPicture) {
        if (snapshot == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Reading battery telemetry…")
            }
        } else {
            PipMonitor(snapshot = snapshot)
        }
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Battery Nerd")
                        Text(
                            text = when (selectedTab) {
                                DashboardTab.Visual -> "Animated battery visual"
                                DashboardTab.Home -> "Live battery telemetry"
                                DashboardTab.Session -> "Power sessions and charger benchmarks"
                                DashboardTab.Wireless -> "Wireless charge optimiser"
                                DashboardTab.Details -> "Detailed device readings"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = subtitleColor
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = onEnterPictureInPicture,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Rounded.OpenInFull,
                            contentDescription = "Enter picture-in-picture"
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                DashboardTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
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
            when (selectedTab) {
                DashboardTab.Visual -> VisualTab(
                    snapshot = snapshot,
                    history = uiState.history,
                    estimatedTimeMs = currentEstimateMs(uiState),
                    padding = padding
                )
                DashboardTab.Home -> HomeTab(
                    snapshot = snapshot,
                    history = uiState.history,
                    sampleIntervalMs = uiState.sampleIntervalMs,
                    maxObservedPowerW = uiState.maxObservedPowerW,
                    minObservedPowerW = uiState.minObservedPowerW,
                    estimatedTimeMs = currentEstimateMs(uiState),
                    rollingAveragePowerW = uiState.rollingAveragePowerW,
                    onSampleIntervalChanged = viewModel::setSampleInterval,
                    padding = padding
                )
                DashboardTab.Session -> RecordingTab(
                    activeRecording = uiState.activeRecording,
                    selectedRecording = uiState.selectedRecording,
                    recordings = uiState.recordings,
                    benchmarkDraftName = uiState.benchmarkDraftName,
                    activeBenchmark = uiState.activeBenchmark,
                    selectedBenchmark = uiState.selectedBenchmark,
                    benchmarks = uiState.benchmarks,
                    onStartRecording = viewModel::startPowerUsageRecording,
                    onStopRecording = viewModel::stopPowerUsageRecording,
                    onSelectRecording = viewModel::selectRecording,
                    onBenchmarkDraftNameChanged = viewModel::setBenchmarkDraftName,
                    onStartBenchmark = viewModel::startChargerBenchmark,
                    onStopBenchmark = viewModel::stopChargerBenchmark,
                    onSelectBenchmark = viewModel::selectBenchmark,
                    padding = padding
                )
                DashboardTab.Wireless -> WirelessTab(
                    snapshot = snapshot,
                    activeSession = uiState.activeWirelessSession,
                    onStart = viewModel::startWirelessAlignment,
                    onStop = viewModel::stopWirelessAlignment,
                    onReset = viewModel::resetWirelessAlignment,
                    estimatedTimeMs = currentEstimateMs(uiState),
                    padding = padding
                )
                DashboardTab.Details -> DetailsTab(
                    snapshot = snapshot,
                    padding = padding
                )
            }
        }
    }
}


@Composable
private fun RecordingTab(
    activeRecording: ActivePowerUsageRecording?,
    selectedRecording: PowerUsageRecord?,
    recordings: List<PowerUsageRecord>,
    benchmarkDraftName: String,
    activeBenchmark: ActiveChargerBenchmarkSession?,
    selectedBenchmark: ChargerBenchmarkRecord?,
    benchmarks: List<ChargerBenchmarkRecord>,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSelectRecording: (Long) -> Unit,
    onBenchmarkDraftNameChanged: (String) -> Unit,
    onStartBenchmark: () -> Unit,
    onStopBenchmark: () -> Unit,
    onSelectBenchmark: (Long) -> Unit,
    padding: PaddingValues,
) {
    var selectedSubTab by remember { mutableStateOf(SessionSubTab.Stopwatch) }
    val displayRecord = selectedRecording ?: recordings.firstOrNull()
    val displayBenchmark = selectedBenchmark ?: benchmarks.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedSubTab == SessionSubTab.Stopwatch,
                    onClick = { selectedSubTab = SessionSubTab.Stopwatch },
                    label = { Text("Stopwatch") }
                )
                FilterChip(
                    selected = selectedSubTab == SessionSubTab.Benchmarks,
                    onClick = { selectedSubTab = SessionSubTab.Benchmarks },
                    label = { Text("Benchmarks") }
                )
            }
        }

        when (selectedSubTab) {
            SessionSubTab.Stopwatch -> {
                item {
                    SessionRecorderCard(
                        activeRecording = activeRecording,
                        selectedRecording = displayRecord,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                    )
                }
                item {
                    SectionTitle("Saved session recordings")
                }
                if (recordings.isEmpty()) {
                    item {
                        EmptyStorageCard(
                            title = "No recordings yet",
                            body = "Recorded sessions are stored locally on this device after you stop them."
                        )
                    }
                } else {
                    items(recordings) { record ->
                        RecordingListItem(
                            record = record,
                            isSelected = selectedRecording?.id == record.id || (selectedRecording == null && recordings.firstOrNull()?.id == record.id),
                            onClick = { onSelectRecording(record.id) }
                        )
                    }
                }
            }
            SessionSubTab.Benchmarks -> {
                item {
                    ChargerBenchmarkCard(
                        draftName = benchmarkDraftName,
                        activeBenchmark = activeBenchmark,
                        selectedBenchmark = displayBenchmark,
                        onBenchmarkDraftNameChanged = onBenchmarkDraftNameChanged,
                        onStartBenchmark = onStartBenchmark,
                        onStopBenchmark = onStopBenchmark,
                    )
                }
                item {
                    SectionTitle("Saved charger benchmarks")
                }
                if (benchmarks.isEmpty()) {
                    item {
                        EmptyStorageCard(
                            title = "No charger benchmarks yet",
                            body = "Benchmarks are stored locally after each 60 second run so you can compare chargers and cables later."
                        )
                    }
                } else {
                    items(benchmarks) { benchmark ->
                        BenchmarkListItem(
                            benchmark = benchmark,
                            isSelected = selectedBenchmark?.id == benchmark.id || (selectedBenchmark == null && benchmarks.firstOrNull()?.id == benchmark.id),
                            onClick = { onSelectBenchmark(benchmark.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStorageCard(title: String, body: String) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionRecorderCard(
    activeRecording: ActivePowerUsageRecording?,
    selectedRecording: PowerUsageRecord?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Power usage stopwatch", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (activeRecording != null) {
                            "Recording ${formatRecordingElapsed(activeRecording)} / 5m max"
                        } else {
                            "Samples power every second. Saved locally on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (activeRecording == null) {
                    Button(onClick = onStartRecording) {
                        Text("Start")
                    }
                } else {
                    OutlinedButton(onClick = onStopRecording) {
                        Text("Stop")
                    }
                }
            }

            when {
                activeRecording != null -> {
                    Text(
                        text = recordingSummaryText(
                            totalEnergyMwh = activeRecording.totalEnergyMwh,
                            samples = activeRecording.samples.size,
                            isLive = true,
                            startRemainingEnergyMwh = activeRecording.startRemainingEnergyMwh,
                            startFullEnergyMwh = activeRecording.startFullEnergyMwh,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    RecordingPowerChart(
                        values = activeRecording.samples.map { it.powerW },
                        title = "Live recording",
                        subtitle = "One-second samples. Auto-stops at five minutes."
                    )
                }

                selectedRecording != null -> {
                    Text(
                        text = recordingSummaryText(
                            totalEnergyMwh = selectedRecording.totalEnergyMwh,
                            samples = selectedRecording.samples.size,
                            isLive = false,
                            startRemainingEnergyMwh = selectedRecording.startRemainingEnergyMwh,
                            startFullEnergyMwh = selectedRecording.startFullEnergyMwh,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    RecordingPowerChart(
                        values = selectedRecording.samples.map { it.powerW },
                        title = formatRecordingHeader(selectedRecording),
                        subtitle = if (selectedRecording.autoStopped) {
                            "Auto-stopped at the five-minute cap."
                        } else {
                            "Tap a row below to load another recording."
                        }
                    )
                }

                else -> {
                    Text(
                        "No session selected yet. Start a recording to capture the next chess game, video, or other short test.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargerBenchmarkCard(
    draftName: String,
    activeBenchmark: ActiveChargerBenchmarkSession?,
    selectedBenchmark: ChargerBenchmarkRecord?,
    onBenchmarkDraftNameChanged: (String) -> Unit,
    onStartBenchmark: () -> Unit,
    onStopBenchmark: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Charger benchmark", style = MaterialTheme.typography.titleLarge)
            Text(
                if (activeBenchmark != null) {
                    "Running ${formatBenchmarkElapsed(activeBenchmark)} / 1:00. Keep the charger and cable steady."
                } else {
                    "Name the charger or cable, then run a one-minute benchmark. Results are stored locally for later comparison."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = draftName,
                onValueChange = onBenchmarkDraftNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Benchmark name") },
                placeholder = { Text("Anker 25W + USB-C cable") },
                enabled = activeBenchmark == null,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onStartBenchmark, enabled = activeBenchmark == null) {
                    Text("Start")
                }
                OutlinedButton(onClick = onStopBenchmark, enabled = activeBenchmark != null) {
                    Text("Stop")
                }
            }

            when {
                activeBenchmark != null -> {
                    BenchmarkSummary(activeBenchmark.toPreviewRecord())
                    RecordingPowerChart(
                        values = activeBenchmark.samples.map { it.powerW },
                        title = "Live benchmark: ${activeBenchmark.name}",
                        subtitle = "Benchmarking current charger performance over a one-minute window."
                    )
                }
                selectedBenchmark != null -> {
                    BenchmarkSummary(selectedBenchmark)
                    RecordingPowerChart(
                        values = selectedBenchmark.samples.map { it.powerW },
                        title = selectedBenchmark.name,
                        subtitle = "Tap a row below to load another saved benchmark."
                    )
                }
                else -> {
                    Text(
                        "No benchmark selected yet. Run a benchmark to compare peak power, sustained averages, stability, and taper time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkSummary(benchmark: ChargerBenchmarkRecord) {
    val tempDelta = if (benchmark.startTemperatureC != null && benchmark.endTemperatureC != null) {
        benchmark.endTemperatureC - benchmark.startTemperatureC
    } else {
        null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = benchmark.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Peak ${formatPrecisePower(benchmark.peakPowerW)}") })
            AssistChip(onClick = {}, label = { Text("10s ${formatPrecisePower(benchmark.average10sPowerW)}") })
            AssistChip(onClick = {}, label = { Text("30s ${formatPrecisePower(benchmark.average30sPowerW)}") })
            AssistChip(onClick = {}, label = { Text("${benchmarkQualityLabel(benchmark)}") })
            AssistChip(onClick = {}, label = { Text(benchmark.sourceLabel) })
        }
        Text(
            text = "Stability ${formatStdDev(benchmark.stabilityStdDevW)} • Taper ${formatBenchmarkTaper(benchmark.timeToTaperMs)} • Battery ${formatPercent(benchmark.startPercent)} → ${formatPercent(benchmark.endPercent)} • ΔTemp ${formatTemperatureDelta(tempDelta)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BenchmarkListItem(
    benchmark: ChargerBenchmarkRecord,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth(0.82f)) {
                Text(benchmark.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatRecordingHeader(benchmark.startedAt)} • Peak ${formatPrecisePower(benchmark.peakPowerW)} • 30s ${formatPrecisePower(benchmark.average30sPowerW)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Stability ${formatStdDev(benchmark.stabilityStdDevW)} • ${benchmark.sourceLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isSelected) "Selected" else "View",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RecordingListItem(
    record: PowerUsageRecord,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatRecordingHeader(record), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatDurationShort(recordDurationMs(record))} • ${formatSignedEnergyCompact(record.totalEnergyMwh)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recordingUsagePercentLine(record)?.let { percentLine ->
                    Text(
                        percentLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (isSelected) "Selected" else "View",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RecordingPowerChart(
    values: List<Float>,
    title: String,
    subtitle: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val chartBackground = colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val chartOutline = colorScheme.outlineVariant
    val chartLine = colorScheme.primary
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = chartBackground,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(8.dp)
            ) {
                if (values.size < 2) {
                    drawLine(
                        color = chartOutline,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    return@Canvas
                }

                val minValue = minOf(values.minOrNull() ?: 0f, 0f)
                val maxValue = maxOf(values.maxOrNull() ?: 0f, 0f)
                val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
                val zeroY = size.height - ((0f - minValue) / range) * size.height

                drawLine(
                    color = chartOutline,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5.dp.toPx()
                )

                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = if (values.size == 1) 0f else index * (size.width / (values.size - 1))
                    val y = size.height - ((value - minValue) / range) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = chartLine,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            if (values.size >= 2) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = values.minOrNull()?.let { "Min ${formatPower(it)}" } ?: "Min —",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = values.maxOrNull()?.let { "Max ${formatPower(it)}" } ?: "Max —",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    snapshot: BatterySnapshot,
    history: List<BatterySnapshot>,
    sampleIntervalMs: Long,
    maxObservedPowerW: Float?,
    minObservedPowerW: Float?,
    estimatedTimeMs: Long?,
    rollingAveragePowerW: Float?,
    onSampleIntervalChanged: (Long) -> Unit,
    padding: PaddingValues
) {
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
                sampleIntervalMs = sampleIntervalMs,
                maxObservedPowerW = maxObservedPowerW,
                minObservedPowerW = minObservedPowerW,
                estimatedTimeMs = estimatedTimeMs,
                rollingAveragePowerW = rollingAveragePowerW
            )
        }
        item {
            SamplingSelector(
                selected = sampleIntervalMs,
                onSelected = onSampleIntervalChanged
            )
        }
        item {
            SectionTitle("Live chart")
        }
        item {
            LivePowerChart(history = history)
        }
        item {
            SectionTitle("Live interpretation")
        }
        item {
            InsightGrid(snapshot)
        }
    }
}

@Composable
private fun DetailsTab(
    snapshot: BatterySnapshot,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            SectionTitle("Raw and derived telemetry")
        }
        items(detailRows(snapshot)) { row ->
            MetricRow(label = row.first, value = row.second)
        }
    }
}

@Composable
private fun VisualTab(
    snapshot: BatterySnapshot,
    history: List<BatterySnapshot>,
    estimatedTimeMs: Long?,
    padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BatteryTankVisual(snapshot = snapshot)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(label = "Level", value = formatPercent(snapshot.percent))
                        MiniStat(label = "Power", value = formatPower(snapshot.netPowerW))
                        MiniStat(label = estimateMiniLabel(snapshot), value = formatEstimateDuration(estimatedTimeMs))
                    }
                    Text(
                        text = visualStatusText(snapshot),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WirelessTab(
    snapshot: BatterySnapshot,
    activeSession: ActiveWirelessAlignmentSession?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    estimatedTimeMs: Long?,
    padding: PaddingValues,
) {
    val isWireless = snapshot.plugType == BatteryManager.BATTERY_PLUGGED_WIRELESS
    val samples = activeSession?.samples ?: emptyList()
    val livePower = snapshot.netPowerW
    val avg5s = averagePowerSince(samples, snapshot.capturedAt.minusSeconds(5))
    val avg10s = averagePowerSince(samples, snapshot.capturedAt.minusSeconds(10))
    val sessionMin = samples.minOfOrNull { it.powerW }
    val sessionMax = samples.maxOfOrNull { it.powerW }
    val bestInstant = activeSession?.bestInstantPowerW?.takeIf { it.isFinite() }
    val bestSustained = activeSession?.bestSustained5sPowerW?.takeIf { it.isFinite() }
    val startTemperatureC = activeSession?.startTemperatureC
    val currentTemperatureC = snapshot.temperatureC
    val tempRise = if (startTemperatureC != null && currentTemperatureC != null) {
        currentTemperatureC - startTemperatureC
    } else {
        null
    }
    val stability = stabilityLabel(samples, snapshot.capturedAt)
    val trend = trendLabel(samples, snapshot.capturedAt)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Wireless charge optimiser", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (activeSession != null) "Rapid 100 ms polling active. Move the phone slowly and watch the averages." else "Start a rapid polling session to find the strongest wireless charging position.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isWireless) {
                        Text(
                            "Wireless charging is not currently detected. Place the phone on a wireless pad, then start or continue the alignment session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(label = "Power", value = formatPrecisePower(livePower))
                        MiniStat(label = "5s avg", value = formatPrecisePower(avg5s))
                        MiniStat(label = "Best", value = formatPrecisePower(bestInstant))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(label = "Best 5s", value = formatPrecisePower(bestSustained))
                        MiniStat(label = "Trend", value = trend)
                        MiniStat(label = "Stability", value = stability)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(label = "10s avg", value = formatPrecisePower(avg10s))
                        MiniStat(label = "Temp", value = formatTemperature(snapshot.temperatureC))
                        MiniStat(label = "ΔTemp", value = formatTemperatureDelta(tempRise))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(label = "Battery", value = formatPercent(snapshot.percent))
                        MiniStat(label = "Source", value = sourceLabel(snapshot.plugType))
                        MiniStat(label = estimateMiniLabel(snapshot), value = formatEstimateDuration(estimatedTimeMs))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onStart, enabled = activeSession == null) { Text("Start") }
                        OutlinedButton(onClick = onStop, enabled = activeSession != null) { Text("Stop") }
                        OutlinedButton(onClick = onReset, enabled = activeSession != null) { Text("Reset") }
                    }
                }
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Alignment session chart", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Shows rapid wireless power samples. Higher, steadier power usually means better alignment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            RecordingPowerChart(
                values = samples.map { it.powerW },
                title = "Wireless placement power",
                subtitle = "Current ${formatPrecisePower(livePower)} • Min ${formatPrecisePower(sessionMin)} • Max ${formatPrecisePower(sessionMax)}"
            )
        }
    }
}

@Composable
private fun PipMonitor(snapshot: BatterySnapshot) {
    val pipBackground = Color(0xFF090B13)
    val primaryText = Color(0xFFF5F7FF)
    val secondaryText = Color(0xFFD4DBF5)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pipBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BatteryTankVisual(snapshot = snapshot, tankHeight = 96.dp)
            Text(
                text = formatPipPower(snapshot.netPowerW),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = primaryText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isEffectivelyCharging(snapshot)) "Charging" else "On battery",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BatteryTankVisual(snapshot: BatterySnapshot, modifier: Modifier = Modifier, tankHeight: Dp = 280.dp) {
    val percent = ((snapshot.percent ?: 0f) / 100f).coerceIn(0f, 1f)
    val colorScheme = MaterialTheme.colorScheme
    val tankCapColor = colorScheme.outlineVariant
    val tankBorderColor = colorScheme.outline
    val liquidTopColor = colorScheme.primary.copy(alpha = 0.78f)
    val liquidMidColor = colorScheme.primary
    val liquidBottomColor = colorScheme.tertiary.copy(alpha = 0.92f)
    val dropletColor = colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "battery_visual")
    val waveShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_shift"
    )
    val dropletOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "droplet_offset"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(tankHeight)
    ) {
        val strokeWidth = 10.dp.toPx()
        val innerPadding = strokeWidth * 0.7f
        val capWidth = size.width * 0.22f
        val capHeight = size.height * 0.07f
        val tankLeft = size.width * 0.18f
        val tankTop = size.height * 0.12f
        val tankWidth = size.width * 0.64f
        val tankHeight = size.height * 0.72f
        val radius = 28.dp.toPx()
        val innerLeft = tankLeft + innerPadding
        val innerTop = tankTop + innerPadding
        val innerWidth = tankWidth - innerPadding * 2f
        val innerHeight = tankHeight - innerPadding * 2f
        val liquidTop = innerTop + innerHeight * (1f - percent)
        val powerW = snapshot.netPowerW ?: 0f
        val positivePowerW = powerW.coerceAtLeast(0f)
        val negativePowerW = (-powerW).coerceAtLeast(0f)
        val energyEnteringBattery = positivePowerW > 0.05f || (snapshot.isCharging && snapshot.plugType != null && snapshot.plugType != 0)
        val energyLeavingBattery = negativePowerW > 0.05f && !energyEnteringBattery
        val chargingDropletCount = if (energyEnteringBattery) {
            positivePowerW.toInt().coerceIn(1, 18)
        } else {
            0
        }
        val dischargingDropletCount = if (energyLeavingBattery) {
            negativePowerW.toInt().coerceIn(1, 18)
        } else {
            0
        }

        drawRoundRect(
            color = tankCapColor,
            topLeft = Offset(size.width / 2f - capWidth / 2f, tankTop - capHeight * 0.75f),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(capHeight / 2f, capHeight / 2f)
        )

        val tankShape = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = innerLeft,
                    top = innerTop,
                    right = innerLeft + innerWidth,
                    bottom = innerTop + innerHeight,
                    cornerRadius = CornerRadius((radius - innerPadding).coerceAtLeast(0f), (radius - innerPadding).coerceAtLeast(0f))
                )
            )
        }

        val liquidBrush = Brush.verticalGradient(
            colors = listOf(liquidTopColor, liquidMidColor, liquidBottomColor),
            startY = liquidTop,
            endY = innerTop + innerHeight
        )

        clipPath(tankShape) {
            val waveAmplitude = 6.dp.toPx() + positivePowerW.coerceAtMost(18f) * 0.8f
            val waveLength = innerWidth / 1.35f
            val wavePath = Path().apply {
                moveTo(innerLeft, innerTop + innerHeight)
                lineTo(innerLeft, liquidTop)
                var x = innerLeft
                while (x <= innerLeft + innerWidth + 8f) {
                    val progress = ((x - innerLeft) / waveLength) + waveShift
                    val y = liquidTop + sin(progress * 2f * PI).toFloat() * waveAmplitude
                    lineTo(x, y)
                    x += 8f
                }
                lineTo(innerLeft + innerWidth, innerTop + innerHeight)
                close()
            }
            drawPath(path = wavePath, brush = liquidBrush)
        }

        if (chargingDropletCount > 0) {
            val totalLaneCount = 25
            val laneInsetFraction = 0.10f
            val usableWidthFraction = 1f - laneInsetFraction * 2f

            repeat(chargingDropletCount) { index ->
                val row = index / totalLaneCount
                val motion = dropletOffset + index * 0.11f + row * 0.17f
                val cycle = floor(motion).toInt()
                val phase = motion - cycle

                val laneIndex = pseudoRandomInt(
                    seed = cycle * 131 + index * 97 + 17,
                    bound = totalLaneCount
                )
                val laneFraction = (laneIndex + 0.5f) / totalLaneCount.toFloat()
                val laneX = innerLeft + innerWidth * (
                        laneInsetFraction + laneFraction * usableWidthFraction
                        )

                val startY = tankTop - 34.dp.toPx() - row * 18.dp.toPx()
                val travel = (liquidTop - startY - 10.dp.toPx()).coerceAtLeast(24.dp.toPx())
                val y = startY + phase * travel
                val radiusPx = 2.8.dp.toPx() + (1f - phase) * 2.6.dp.toPx()

                drawCircle(
                    color = dropletColor.copy(alpha = 0.28f + (1f - phase) * 0.48f),
                    radius = radiusPx,
                    center = Offset(laneX, y)
                )
            }
        }

        if (dischargingDropletCount > 0) {
            val totalLaneCount = 25
            val laneInsetFraction = 0.10f
            val usableWidthFraction = 1f - laneInsetFraction * 2f

            repeat(dischargingDropletCount) { index ->
                val row = index / totalLaneCount
                val motion = dropletOffset + index * 0.11f + row * 0.17f
                val cycle = floor(motion).toInt()
                val phase = motion - cycle

                val laneIndex = pseudoRandomInt(
                    seed = cycle * 151 + index * 101 + 23,
                    bound = totalLaneCount
                )
                val laneFraction = (laneIndex + 0.5f) / totalLaneCount.toFloat()
                val jitter = (pseudoRandom01(cycle * 181 + index * 71 + 29) - 0.5f) *
                        (innerWidth / totalLaneCount) * 0.35f

                val laneX = innerLeft + innerWidth * (
                        laneInsetFraction + laneFraction * usableWidthFraction
                        ) + jitter

                val startY = (liquidTop + 12.dp.toPx() + row * 16.dp.toPx())
                    .coerceAtMost(innerTop + innerHeight - 10.dp.toPx())
                val endY = tankTop - 34.dp.toPx() - row * 18.dp.toPx()
                val travel = (startY - endY).coerceAtLeast(24.dp.toPx())
                val y = startY - phase * travel
                val radiusPx = 2.8.dp.toPx() + phase * 2.6.dp.toPx()

                drawCircle(
                    color = dropletColor.copy(alpha = 0.24f + phase * 0.52f),
                    radius = radiusPx,
                    center = Offset(laneX, y)
                )
            }
        }

        drawRoundRect(
            color = tankBorderColor,
            topLeft = Offset(tankLeft, tankTop),
            size = Size(tankWidth, tankHeight),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokeWidth)
        )

        val boltCenter = Offset(size.width / 2f, tankTop + tankHeight / 2.2f)
        val boltPath = Path().apply {
            moveTo(boltCenter.x - 12.dp.toPx(), boltCenter.y - 24.dp.toPx())
            lineTo(boltCenter.x + 2.dp.toPx(), boltCenter.y - 24.dp.toPx())
            lineTo(boltCenter.x - 4.dp.toPx(), boltCenter.y)
            lineTo(boltCenter.x + 14.dp.toPx(), boltCenter.y)
            lineTo(boltCenter.x - 8.dp.toPx(), boltCenter.y + 30.dp.toPx())
            lineTo(boltCenter.x - 2.dp.toPx(), boltCenter.y + 6.dp.toPx())
            lineTo(boltCenter.x - 18.dp.toPx(), boltCenter.y + 6.dp.toPx())
            close()
        }
        drawPath(
            path = boltPath,
            color = Color.White.copy(alpha = if (energyEnteringBattery) 0.92f else 0.35f)
        )
    }
}

private fun pseudoRandomInt(seed: Int, bound: Int): Int {
    if (bound <= 0) return 0
    var x = seed * 1103515245 + 12345
    x = x xor (x ushr 16)
    return (x and 0x7fffffff) % bound
}

@Composable
private fun LivePowerChart(history: List<BatterySnapshot>) {
    val colorScheme = MaterialTheme.colorScheme
    val chartBackground = colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val chartOutline = colorScheme.outlineVariant
    val chartLine = colorScheme.primary
    val mutedText = colorScheme.onSurfaceVariant
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Recent net battery power (displayed as W)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Positive values mean energy is entering the battery. Negative values mean it is draining.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        color = chartBackground,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(8.dp)
            ) {
                val values = history.mapNotNull { it.netPowerW }
                if (values.size < 2) {
                    drawLine(
                        color = chartOutline,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    return@Canvas
                }

                val minValue = minOf(values.minOrNull() ?: 0f, 0f)
                val maxValue = maxOf(values.maxOrNull() ?: 0f, 0f)
                val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
                val zeroY = size.height - ((0f - minValue) / range) * size.height

                drawLine(
                    color = chartOutline,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5.dp.toPx()
                )

                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = if (values.size == 1) 0f else index * (size.width / (values.size - 1))
                    val y = size.height - ((value - minValue) / range) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = chartLine,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Min ${formatPower(history.mapNotNull { it.netPowerW }.minOrNull())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Max ${formatPower(history.mapNotNull { it.netPowerW }.maxOrNull())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun visualStatusText(snapshot: BatterySnapshot): String {
    val power = snapshot.netPowerW
    return when {
        snapshot.isCharging && power != null && power > 0 ->
            "Battery is currently charging. Each Watt of power is 1 drop so faster charging visually fills faster."
        snapshot.isCharging ->
            "Power is connected, but this device is not exposing enough data to estimate the live battery-side power cleanly."
        else ->
            "The battery is currently running on stored energy. Each extra negative watt adds another upward droplet."
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    snapshot: BatterySnapshot,
    sampleIntervalMs: Long,
    maxObservedPowerW: Float?,
    minObservedPowerW: Float?,
    estimatedTimeMs: Long?,
    rollingAveragePowerW: Float?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Net battery power", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatPower(snapshot.netPowerW),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Rounded.Bolt, contentDescription = null)
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { ((snapshot.percent ?: 0f) / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("${formatPercent(snapshot.percent)} battery") }, leadingIcon = {
                    Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null)
                })
                AssistChip(onClick = {}, label = { Text(sourceLabel(snapshot.plugType)) })
                AssistChip(onClick = {}, label = { Text(statusLabel(snapshot.status, isEffectivelyCharging(snapshot))) })
            }
            Text(
                text = estimateHeadline(snapshot, estimatedTimeMs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            percentRateHeadline(snapshot, rollingAveragePowerW)?.let { rateHeadline ->
                Text(
                    text = rateHeadline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Observed window: min ${formatPower(minObservedPowerW)} • max ${formatPower(maxObservedPowerW)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Estimate uses the last minute average: ${formatPower(rollingAveragePowerW)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SamplingSelector(selected: Long, onSelected: (Long) -> Unit) {
    val options = listOf(50L, 500L, 1000L, 2000L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Refresh cadence", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        NerdCard("Charge current now", formatCurrent(snapshot.currentNowA), Icons.Rounded.Bolt)
        NerdCard("Charge current average", formatCurrent(snapshot.currentAverageA), Icons.Rounded.Memory)
        NerdCard("Battery temperature", formatTemperature(snapshot.temperatureC), Icons.Rounded.Thermostat)
        NerdCard("Voltage", formatVoltage(snapshot.voltageV), Icons.Rounded.BatteryChargingFull)
    }
}

@Composable
private fun NerdCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Icon(icon, contentDescription = null)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun detailRows(snapshot: BatterySnapshot): List<Pair<String, String>> {
    return listOf(
        "Battery level (intent)" to nullable(snapshot.level?.toString()),
        "Battery scale" to nullable(snapshot.scale?.toString()),
        "Battery percent (derived)" to formatPercent(snapshot.percent),
        "BatteryManager capacity" to nullable(snapshot.capacityPercent?.let { "$it %" }),
        "Status" to statusLabel(snapshot.status, isEffectivelyCharging(snapshot)),
        "Health" to healthLabel(snapshot.health),
        "Plug type" to sourceLabel(snapshot.plugType),
        "Current now (raw reported)" to nullable(snapshot.currentNowUa?.let { "$it raw" }),
        "Current average (raw reported)" to nullable(snapshot.currentAverageUa?.let { "$it raw" }),
        "Detected current scale" to nullable(snapshot.detectedCurrentScale?.name?.replace('_', ' ')),
        "Current now" to formatCurrent(snapshot.currentNowA),
        "Current average" to formatCurrent(snapshot.currentAverageA),
        "Voltage" to nullable(snapshot.voltageMv?.let { "$it mV" }),
        "Voltage (derived)" to formatVoltage(snapshot.voltageV),
        "Net power" to formatPower(snapshot.netPowerW),
        "Charge counter" to nullable(snapshot.chargeCounterUah?.let { "$it µAh" }),
        "Stored energy (derived)" to nullable(snapshot.storedEnergyMwh?.let { formatEnergyMwh(it) }),
        "Estimated full energy" to nullable(snapshot.estimatedFullEnergyMwh?.let { formatEnergyMwh(it) }),
        "Energy remaining to full" to nullable(snapshot.energyToFullMwh?.let { formatEnergyMwh(it) }),
        "Charge time remaining (system API)" to nullable(snapshot.chargeTimeRemainingMs?.let { formatDuration(it) }),
        "Temperature" to nullable(snapshot.temperatureDeciC?.let { "$it deci-°C" }),
        "Temperature (derived)" to formatTemperature(snapshot.temperatureC),
        "Technology" to nullable(snapshot.technology),
        "Battery present" to nullable(snapshot.present?.toString())
    )
}

private fun estimateMiniLabel(snapshot: BatterySnapshot): String =
    if (isEffectivelyCharging(snapshot)) "To full" else "Remaining"

private fun estimateHeadline(snapshot: BatterySnapshot, estimatedTimeMs: Long?): String = when {
    isEffectivelyCharging(snapshot) -> "Estimated time to full: ${formatEstimateDuration(estimatedTimeMs)}"
    else -> "Estimated battery life left: ${formatEstimateDuration(estimatedTimeMs)}"
}


private fun percentRateHeadline(snapshot: BatterySnapshot, rollingAveragePowerW: Float?): String? {
    val percentPerHour = snapshot.percentPerHourForPower(rollingAveragePowerW) ?: return null
    val absRate = kotlin.math.abs(percentPerHour)
    return if (percentPerHour >= 0f) {
        "Charging at ${formatPercentRate(absRate)}"
    } else {
        "Draining at ${formatPercentRate(absRate)}"
    }
}

private fun formatPercentRate(percentPerHour: Float): String =
    String.format(Locale.US, "%.1f%%/hr", percentPerHour)


private fun formatEstimateDuration(durationMs: Long?): String =
    durationMs?.let { formatDuration(it) } ?: "Estimating…"

private fun formatEnergyMwh(valueMwh: Float): String =
    String.format(Locale.US, "%.0f mWh", valueMwh)



private fun recordingSummaryText(
    totalEnergyMwh: Float,
    samples: Int,
    isLive: Boolean,
    startRemainingEnergyMwh: Float?,
    startFullEnergyMwh: Float?,
): String {
    val action = when {
        totalEnergyMwh < 0f -> "Used"
        totalEnergyMwh > 0f -> "Gained"
        else -> "Net"
    }
    val prefix = if (isLive) "Live total" else "Session total"
    val percentLine = recordingUsagePercentLine(totalEnergyMwh, startRemainingEnergyMwh, startFullEnergyMwh)
    return buildString {
        append("$prefix: $action ${formatAbsEnergyCompact(totalEnergyMwh)} • $samples samples")
        if (percentLine != null) {
            append("\n")
            append(percentLine)
        }
    }
}

private fun recordingUsagePercentLine(record: PowerUsageRecord): String? =
    recordingUsagePercentLine(
        totalEnergyMwh = record.totalEnergyMwh,
        startRemainingEnergyMwh = record.startRemainingEnergyMwh,
        startFullEnergyMwh = record.startFullEnergyMwh,
    )

private fun recordingUsagePercentLine(
    totalEnergyMwh: Float,
    startRemainingEnergyMwh: Float?,
    startFullEnergyMwh: Float?,
): String? {
    val usedMwh = (-totalEnergyMwh).coerceAtLeast(0f)
    if (usedMwh <= 0f) return null

    val ofFullPercent = startFullEnergyMwh
        ?.takeIf { it > 0f }
        ?.let { (usedMwh / it) * 100f }
    val ofRemainingPercent = startRemainingEnergyMwh
        ?.takeIf { it > 0f }
        ?.let { (usedMwh / it) * 100f }

    if (ofFullPercent == null && ofRemainingPercent == null) return null

    return buildString {
        append("Used ")
        append(formatThreeDecimalPercent(ofFullPercent))
        append(" of full")
        if (ofRemainingPercent != null) {
            append(" • ")
            append(formatThreeDecimalPercent(ofRemainingPercent))
            append(" of remaining at start")
        }
    }
}

private fun formatThreeDecimalPercent(value: Float?): String =
    value?.let { String.format(Locale.US, "%.3f%%", it) } ?: "—"

private fun formatRecordingHeader(record: PowerUsageRecord): String = formatRecordingHeader(record.startedAt)

private fun formatRecordingHeader(startedAt: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.US)
        .withZone(ZoneId.systemDefault())
    return formatter.format(startedAt)
}

private fun recordDurationMs(record: PowerUsageRecord): Long =
    java.time.Duration.between(record.startedAt, record.endedAt).toMillis().coerceAtLeast(0L)

private fun formatRecordingElapsed(activeRecording: ActivePowerUsageRecording): String {
    val first = activeRecording.startedAt
    val last = activeRecording.samples.lastOrNull()?.capturedAt ?: first
    return formatDurationShort(java.time.Duration.between(first, last).toMillis())
}

private fun formatDurationShort(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatAbsEnergyCompact(valueMwh: Float): String {
    val absValue = kotlin.math.abs(valueMwh)
    return if (absValue >= 1000f) {
        String.format(Locale.US, "%.2f Wh", absValue / 1000f)
    } else {
        String.format(Locale.US, "%.0f mWh", absValue)
    }
}

private fun formatSignedEnergyCompact(valueMwh: Float): String {
    val sign = when {
        valueMwh < 0f -> "−"
        valueMwh > 0f -> "+"
        else -> ""
    }
    val absValue = kotlin.math.abs(valueMwh)
    return if (absValue >= 1000f) {
        String.format(Locale.US, "%s%.2f Wh", sign, absValue / 1000f)
    } else {
        String.format(Locale.US, "%s%.0f mWh", sign, absValue)
    }
}

private fun benchmarkQualityLabel(benchmark: ChargerBenchmarkRecord): String {
    val avg30 = benchmark.average30sPowerW
    val stdDev = benchmark.stabilityStdDevW
    return when {
        avg30 >= 12f && stdDev < 0.6f -> "Excellent"
        avg30 >= 8f && stdDev < 1.0f -> "Good"
        avg30 >= 4f -> "Fair"
        else -> "Weak"
    }
}

private fun formatBenchmarkTaper(timeToTaperMs: Long?): String =
    timeToTaperMs?.let { formatDurationShort(it) } ?: "No taper"

private fun formatStdDev(stdDevW: Float): String =
    String.format(Locale.US, "±%.2f W", stdDevW)

private fun formatBenchmarkElapsed(activeBenchmark: ActiveChargerBenchmarkSession): String {
    val first = activeBenchmark.startedAt
    val last = activeBenchmark.samples.lastOrNull()?.capturedAt ?: first
    return formatDurationShort(Duration.between(first, last).toMillis())
}

private fun ActiveChargerBenchmarkSession.toPreviewRecord(): ChargerBenchmarkRecord {
    val previewSamples = if (samples.isEmpty()) emptyList() else samples
    val peakPowerW = previewSamples.maxOfOrNull { it.powerW } ?: 0f
    val endAt = previewSamples.lastOrNull()?.capturedAt ?: startedAt
    val average10sPowerW = previewSamples.filter { !it.capturedAt.isBefore(endAt.minusSeconds(10)) }.map { it.powerW }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
    val average30sPowerW = previewSamples.filter { !it.capturedAt.isBefore(endAt.minusSeconds(30)) }.map { it.powerW }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
    val mean = previewSamples.map { it.powerW }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
    val stability = if (previewSamples.size < 2) 0f else kotlin.math.sqrt(previewSamples.map { (it.powerW - mean) * (it.powerW - mean) }.average().toFloat())
    val threshold = peakPowerW * 0.9f
    val timeToTaper = if (peakPowerW <= 0f) null else previewSamples.firstOrNull { it.powerW < threshold }?.let { Duration.between(startedAt, it.capturedAt).toMillis() }
    return ChargerBenchmarkRecord(
        id = startedAt.toEpochMilli(),
        name = name,
        startedAt = startedAt,
        endedAt = endAt,
        samples = previewSamples,
        peakPowerW = peakPowerW,
        average10sPowerW = average10sPowerW,
        average30sPowerW = average30sPowerW,
        stabilityStdDevW = stability,
        timeToTaperMs = timeToTaper,
        startPercent = startPercent,
        endPercent = null,
        startTemperatureC = startTemperatureC,
        endTemperatureC = null,
        sourceLabel = sourceLabel,
    )
}

private fun nullable(value: String?): String = value ?: "Unsupported / unavailable"

private fun formatPercent(percent: Float?): String =
    percent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"

private fun formatCurrent(valueA: Float?): String =
    valueA?.let {
        val absValue = kotlin.math.abs(it)
        if (absValue < 1f) {
            String.format(Locale.US, "%.0f mA", it * 1000f)
        } else {
            String.format(Locale.US, "%.3f A", it)
        }
    } ?: "Unsupported / unavailable"

private fun formatVoltage(valueV: Float?): String =
    valueV?.let { String.format(Locale.US, "%.3f V", it) } ?: "Unsupported / unavailable"

private fun formatTemperature(valueC: Float?): String =
    valueC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Unsupported / unavailable"

private fun formatPower(powerW: Float?): String =
    powerW?.let { String.format(Locale.US, "%.2f W", it) } ?: "Unsupported / unavailable"

private fun formatPipPower(powerW: Float?): String {
    val value = powerW ?: return "—"
    val absValue = kotlin.math.abs(value)
    return if (absValue < 1f) {
        String.format(Locale.US, "%.0f mW", value * 1000f)
    } else {
        String.format(Locale.US, "%.1f W", value)
    }
}

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

private fun isEffectivelyCharging(snapshot: BatterySnapshot): Boolean = when {
    snapshot.status == BatteryManager.BATTERY_STATUS_CHARGING -> true
    snapshot.status == BatteryManager.BATTERY_STATUS_FULL && (snapshot.plugType ?: 0) != 0 -> true
    (snapshot.plugType ?: 0) != 0 && (snapshot.netPowerW ?: 0f) > 0f -> true
    else -> false
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

private fun pseudoRandom01(seed: Int): Float {
    var x = seed * 1103515245 + 12345
    x = x xor (x ushr 16)
    return ((x and 0x7fffffff) / 2147483647f).coerceIn(0f, 1f)
}

private fun averagePowerSince(samples: List<WirelessAlignmentSample>, since: java.time.Instant): Float? =
    samples.filter { !it.capturedAt.isBefore(since) }
        .map { it.powerW }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()

private fun stabilityLabel(samples: List<WirelessAlignmentSample>, now: java.time.Instant): String {
    val recent = samples.filter { !it.capturedAt.isBefore(now.minusSeconds(5)) }.map { it.powerW }
    if (recent.size < 3) return "—"
    val mean = recent.average().toFloat()
    val variance = recent.map { (it - mean) * (it - mean) }.average().toFloat()
    val stdDev = kotlin.math.sqrt(variance)
    return when {
        stdDev < 0.12f -> "Very stable"
        stdDev < 0.3f -> "Stable"
        stdDev < 0.7f -> "Moderate"
        else -> "Fluctuating"
    }
}

private fun trendLabel(samples: List<WirelessAlignmentSample>, now: java.time.Instant): String {
    val recent = samples.filter { !it.capturedAt.isBefore(now.minusMillis(1200)) }
    if (recent.size < 4) return "→ Stable"
    val split = recent.size / 2
    val older = recent.take(split).map { it.powerW }.average().toFloat()
    val newer = recent.drop(split).map { it.powerW }.average().toFloat()
    val delta = newer - older
    return when {
        delta > 0.15f -> "↑ Better"
        delta < -0.15f -> "↓ Worse"
        else -> "→ Stable"
    }
}

private fun formatPrecisePower(powerW: Float?): String =
    powerW?.let { String.format(Locale.US, "%.3f W", it) } ?: "—"

private fun formatTemperatureDelta(deltaC: Float?): String =
    deltaC?.let { String.format(Locale.US, "%+.1f °C", it) } ?: "—"

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}


private fun currentEstimateMs(uiState: BatteryUiState): Long? =
    if (uiState.snapshot?.let(::isEffectivelyCharging) == true) uiState.estimatedTimeToFullMs else uiState.estimatedTimeRemainingMs
