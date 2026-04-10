package au.com.evagames.batterynerd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import au.com.evagames.batterynerd.data.BatteryRepository
import au.com.evagames.batterynerd.data.BatterySnapshot
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PowerUsageSample(
    val capturedAt: Instant,
    val powerW: Float,
)

data class PowerUsageRecord(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant,
    val samples: List<PowerUsageSample>,
    val totalEnergyMwh: Float,
    val startRemainingEnergyMwh: Float?,
    val startFullEnergyMwh: Float?,
    val autoStopped: Boolean,
)

data class ActivePowerUsageRecording(
    val startedAt: Instant,
    val samples: List<PowerUsageSample> = emptyList(),
    val totalEnergyMwh: Float = 0f,
    val startRemainingEnergyMwh: Float? = null,
    val startFullEnergyMwh: Float? = null,
)

data class WirelessAlignmentSample(
    val capturedAt: Instant,
    val powerW: Float,
    val temperatureC: Float?,
    val percent: Float?,
)

data class ActiveWirelessAlignmentSession(
    val startedAt: Instant,
    val startTemperatureC: Float? = null,
    val samples: List<WirelessAlignmentSample> = emptyList(),
    val bestInstantPowerW: Float = Float.NEGATIVE_INFINITY,
    val bestSustained5sPowerW: Float = Float.NEGATIVE_INFINITY,
)

data class BatteryUiState(
    val snapshot: BatterySnapshot? = null,
    val sampleIntervalMs: Long = 500L,
    val maxObservedPowerW: Float? = null,
    val minObservedPowerW: Float? = null,
    val history: List<BatterySnapshot> = emptyList(),
    val rollingAveragePowerW: Float? = null,
    val estimatedTimeRemainingMs: Long? = null,
    val estimatedTimeToFullMs: Long? = null,
    val activeRecording: ActivePowerUsageRecording? = null,
    val recordings: List<PowerUsageRecord> = emptyList(),
    val selectedRecordingId: Long? = null,
    val activeWirelessSession: ActiveWirelessAlignmentSession? = null,
) {
    val selectedRecording: PowerUsageRecord?
        get() = recordings.firstOrNull { it.id == selectedRecordingId }
}

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private val rollingWindowMs = 60_000L
    private val maxRecordingMs = 5 * 60_000L
    private val recordingSampleIntervalMs = 1_000L
    private val wirelessRapidIntervalMs = 100L
    private val maxWirelessSamples = 3_000
    private var preWirelessSampleIntervalMs: Long? = null

    private fun historyCapacityFor(intervalMs: Long): Int = ((rollingWindowMs / intervalMs) + 10L).toInt().coerceAtLeast(60)

    init {
        startObserving()
    }

    fun setSampleInterval(intervalMs: Long) {
        _uiState.value = _uiState.value.copy(sampleIntervalMs = intervalMs)
        startObserving()
    }

    fun startPowerUsageRecording() {
        val snapshot = _uiState.value.snapshot ?: return
        _uiState.value = _uiState.value.copy(
            activeRecording = ActivePowerUsageRecording(
                startedAt = snapshot.capturedAt,
                startRemainingEnergyMwh = snapshot.storedEnergyMwh,
                startFullEnergyMwh = snapshot.estimatedFullEnergyMwh,
            ),
            selectedRecordingId = null,
        )
    }

    fun stopPowerUsageRecording() {
        val state = _uiState.value
        val active = state.activeRecording ?: return
        val snapshot = state.snapshot ?: return
        finalizeRecording(active, snapshot, autoStopped = false)
    }

    fun selectRecording(recordId: Long) {
        _uiState.value = _uiState.value.copy(selectedRecordingId = recordId)
    }

    fun startWirelessAlignment() {
        val snapshot = _uiState.value.snapshot ?: return
        if (preWirelessSampleIntervalMs == null) {
            preWirelessSampleIntervalMs = _uiState.value.sampleIntervalMs
        }
        if (_uiState.value.sampleIntervalMs != wirelessRapidIntervalMs) {
            _uiState.value = _uiState.value.copy(sampleIntervalMs = wirelessRapidIntervalMs)
            startObserving()
        }
        _uiState.value = _uiState.value.copy(
            activeWirelessSession = ActiveWirelessAlignmentSession(
                startedAt = snapshot.capturedAt,
                startTemperatureC = snapshot.temperatureC,
                samples = listOf(
                    WirelessAlignmentSample(
                        capturedAt = snapshot.capturedAt,
                        powerW = snapshot.netPowerW ?: 0f,
                        temperatureC = snapshot.temperatureC,
                        percent = snapshot.percent,
                    )
                ),
                bestInstantPowerW = snapshot.netPowerW ?: Float.NEGATIVE_INFINITY,
                bestSustained5sPowerW = snapshot.netPowerW ?: Float.NEGATIVE_INFINITY,
            )
        )
    }

    fun stopWirelessAlignment() {
        _uiState.value = _uiState.value.copy(activeWirelessSession = null)
        preWirelessSampleIntervalMs?.let { previous ->
            preWirelessSampleIntervalMs = null
            if (_uiState.value.sampleIntervalMs != previous) {
                _uiState.value = _uiState.value.copy(sampleIntervalMs = previous)
                startObserving()
            }
        }
    }

    fun resetWirelessAlignment() {
        val snapshot = _uiState.value.snapshot ?: return
        _uiState.value = _uiState.value.copy(
            activeWirelessSession = ActiveWirelessAlignmentSession(
                startedAt = snapshot.capturedAt,
                startTemperatureC = snapshot.temperatureC,
                samples = listOf(
                    WirelessAlignmentSample(
                        capturedAt = snapshot.capturedAt,
                        powerW = snapshot.netPowerW ?: 0f,
                        temperatureC = snapshot.temperatureC,
                        percent = snapshot.percent,
                    )
                ),
                bestInstantPowerW = snapshot.netPowerW ?: Float.NEGATIVE_INFINITY,
                bestSustained5sPowerW = snapshot.netPowerW ?: Float.NEGATIVE_INFINITY,
            )
        )
    }

    private fun startObserving() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            repository.observeBattery(_uiState.value.sampleIntervalMs).collect { snapshot ->
                val currentState = _uiState.value
                val currentMax = currentState.maxObservedPowerW
                val currentMin = currentState.minObservedPowerW
                val power = snapshot.netPowerW
                val historyCapacity = historyCapacityFor(currentState.sampleIntervalMs)
                val updatedHistory = (currentState.history + snapshot).takeLast(historyCapacity)
                val windowStart = snapshot.capturedAt.minusMillis(rollingWindowMs)
                val rollingSamples = updatedHistory.filter { !it.capturedAt.isBefore(windowStart) }
                val rollingAveragePowerW = rollingSamples
                    .mapNotNull { it.netPowerW }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat()
                val estimatedTimeRemainingMs = snapshot.estimatedTimeRemainingMsForPower(rollingAveragePowerW)
                val estimatedTimeToFullMs = snapshot.estimatedTimeToFullMsForPower(rollingAveragePowerW)

                var nextActiveRecording = currentState.activeRecording?.let { updateActiveRecording(it, snapshot) }
                var nextRecordings = currentState.recordings
                var nextSelectedRecordingId = currentState.selectedRecordingId

                if (nextActiveRecording != null) {
                    val elapsedMs = Duration.between(nextActiveRecording.startedAt, snapshot.capturedAt).toMillis()
                    if (elapsedMs >= maxRecordingMs) {
                        val finalized = buildRecord(nextActiveRecording, snapshot, autoStopped = true)
                        nextRecordings = (listOf(finalized) + currentState.recordings).take(10)
                        nextSelectedRecordingId = finalized.id
                        nextActiveRecording = null
                    }
                }

                val nextWirelessSession = currentState.activeWirelessSession?.let { updateWirelessSession(it, snapshot) }

                _uiState.value = currentState.copy(
                    snapshot = snapshot,
                    history = updatedHistory,
                    maxObservedPowerW = when {
                        power == null -> currentMax
                        currentMax == null -> power
                        else -> maxOf(currentMax, power)
                    },
                    minObservedPowerW = when {
                        power == null -> currentMin
                        currentMin == null -> power
                        else -> minOf(currentMin, power)
                    },
                    rollingAveragePowerW = rollingAveragePowerW,
                    estimatedTimeRemainingMs = estimatedTimeRemainingMs,
                    estimatedTimeToFullMs = estimatedTimeToFullMs,
                    activeRecording = nextActiveRecording,
                    recordings = nextRecordings,
                    selectedRecordingId = nextSelectedRecordingId,
                    activeWirelessSession = nextWirelessSession,
                )
            }
        }
    }

    private fun updateActiveRecording(
        active: ActivePowerUsageRecording,
        snapshot: BatterySnapshot,
    ): ActivePowerUsageRecording {
        val powerW = snapshot.netPowerW ?: 0f
        val lastSample = active.samples.lastOrNull()
        return if (lastSample == null) {
            active.copy(samples = listOf(PowerUsageSample(snapshot.capturedAt, powerW)))
        } else {
            val elapsedMs = Duration.between(lastSample.capturedAt, snapshot.capturedAt).toMillis()
            if (elapsedMs < recordingSampleIntervalMs) {
                active
            } else {
                val deltaSeconds = elapsedMs / 1000f
                val additionalEnergyMwh = lastSample.powerW * deltaSeconds / 3.6f
                active.copy(
                    samples = active.samples + PowerUsageSample(snapshot.capturedAt, powerW),
                    totalEnergyMwh = active.totalEnergyMwh + additionalEnergyMwh,
                )
            }
        }
    }

    private fun updateWirelessSession(
        active: ActiveWirelessAlignmentSession,
        snapshot: BatterySnapshot,
    ): ActiveWirelessAlignmentSession {
        val sample = WirelessAlignmentSample(
            capturedAt = snapshot.capturedAt,
            powerW = snapshot.netPowerW ?: 0f,
            temperatureC = snapshot.temperatureC,
            percent = snapshot.percent,
        )
        val updatedSamples = (active.samples + sample).takeLast(maxWirelessSamples)
        val fiveSecondCutoff = snapshot.capturedAt.minusSeconds(5)
        val fiveSecondAverage = updatedSamples
            .filter { !it.capturedAt.isBefore(fiveSecondCutoff) }
            .map { it.powerW }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat() ?: sample.powerW

        return active.copy(
            samples = updatedSamples,
            bestInstantPowerW = maxOf(active.bestInstantPowerW, sample.powerW),
            bestSustained5sPowerW = maxOf(active.bestSustained5sPowerW, fiveSecondAverage),
        )
    }

    private fun finalizeRecording(
        active: ActivePowerUsageRecording,
        snapshot: BatterySnapshot,
        autoStopped: Boolean,
    ) {
        val finalized = buildRecord(active, snapshot, autoStopped)
        _uiState.value = _uiState.value.copy(
            activeRecording = null,
            recordings = (listOf(finalized) + _uiState.value.recordings).take(10),
            selectedRecordingId = finalized.id,
        )
    }

    private fun buildRecord(
        active: ActivePowerUsageRecording,
        snapshot: BatterySnapshot,
        autoStopped: Boolean,
    ): PowerUsageRecord {
        val samples = when {
            active.samples.isEmpty() -> listOf(PowerUsageSample(snapshot.capturedAt, snapshot.netPowerW ?: 0f))
            else -> active.samples
        }
        return PowerUsageRecord(
            id = active.startedAt.toEpochMilli(),
            startedAt = active.startedAt,
            endedAt = samples.last().capturedAt,
            samples = samples,
            totalEnergyMwh = active.totalEnergyMwh,
            startRemainingEnergyMwh = active.startRemainingEnergyMwh,
            startFullEnergyMwh = active.startFullEnergyMwh,
            autoStopped = autoStopped,
        )
    }
}
