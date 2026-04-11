package au.com.evagames.batterynerd.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import au.com.evagames.batterynerd.data.BatteryRepository
import au.com.evagames.batterynerd.data.BatterySnapshot
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

data class ChargerBenchmarkRecord(
    val id: Long,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val samples: List<PowerUsageSample>,
    val peakPowerW: Float,
    val average10sPowerW: Float,
    val average30sPowerW: Float,
    val stabilityStdDevW: Float,
    val timeToTaperMs: Long?,
    val startPercent: Float?,
    val endPercent: Float?,
    val startTemperatureC: Float?,
    val endTemperatureC: Float?,
    val sourceLabel: String,
)

data class ActiveChargerBenchmarkSession(
    val name: String,
    val startedAt: Instant,
    val startPercent: Float? = null,
    val startTemperatureC: Float? = null,
    val sourceLabel: String = "Unknown",
    val samples: List<PowerUsageSample> = emptyList(),
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
    val benchmarkDraftName: String = "",
    val activeBenchmark: ActiveChargerBenchmarkSession? = null,
    val benchmarks: List<ChargerBenchmarkRecord> = emptyList(),
    val selectedBenchmarkId: Long? = null,
) {
    val selectedRecording: PowerUsageRecord?
        get() = recordings.firstOrNull { it.id == selectedRecordingId }

    val selectedBenchmark: ChargerBenchmarkRecord?
        get() = benchmarks.firstOrNull { it.id == selectedBenchmarkId }
}

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application.applicationContext)
    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private val historyWindowMs = 60_000L
    private val estimateWindowMs = 5_000L
    private val maxRecordingMs = 5 * 60_000L
    private val recordingSampleIntervalMs = 1_000L
    private val wirelessRapidIntervalMs = 100L
    private val maxWirelessSamples = 3_000
    private val maxSavedItems = 50
    private val benchmarkDurationMs = 60_000L
    private var preWirelessSampleIntervalMs: Long? = null

    private fun historyCapacityFor(intervalMs: Long): Int = ((historyWindowMs / intervalMs) + 10L).toInt().coerceAtLeast(60)

    init {
        val loadedRecordings = loadRecordings()
        val loadedBenchmarks = loadBenchmarks()
        _uiState.value = _uiState.value.copy(
            recordings = loadedRecordings,
            selectedRecordingId = loadedRecordings.firstOrNull()?.id,
            benchmarks = loadedBenchmarks,
            selectedBenchmarkId = loadedBenchmarks.firstOrNull()?.id,
        )
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

    fun deleteRecording(recordId: Long) {
        val updated = _uiState.value.recordings.filterNot { it.id == recordId }
        saveRecordings(updated)
        _uiState.value = _uiState.value.copy(
            recordings = updated,
            selectedRecordingId = _uiState.value.selectedRecordingId
                ?.takeIf { id -> updated.any { it.id == id } }
                ?: updated.firstOrNull()?.id,
        )
    }

    fun clearRecordings() {
        saveRecordings(emptyList())
        _uiState.value = _uiState.value.copy(
            recordings = emptyList(),
            selectedRecordingId = null,
        )
    }

    fun setBenchmarkDraftName(name: String) {
        _uiState.value = _uiState.value.copy(benchmarkDraftName = name)
    }

    fun startChargerBenchmark() {
        val snapshot = _uiState.value.snapshot ?: return
        val draftName = _uiState.value.benchmarkDraftName.trim().ifEmpty {
            "Benchmark ${Instant.now()}"
        }
        _uiState.value = _uiState.value.copy(
            activeBenchmark = ActiveChargerBenchmarkSession(
                name = draftName,
                startedAt = snapshot.capturedAt,
                startPercent = snapshot.percent,
                startTemperatureC = snapshot.temperatureC,
                sourceLabel = batterySourceLabel(snapshot),
                samples = listOf(PowerUsageSample(snapshot.capturedAt, snapshot.netPowerW ?: 0f)),
            ),
            selectedBenchmarkId = null,
        )
    }

    fun stopChargerBenchmark() {
        val state = _uiState.value
        val active = state.activeBenchmark ?: return
        val snapshot = state.snapshot ?: return
        finalizeBenchmark(active, snapshot)
    }

    fun selectBenchmark(benchmarkId: Long) {
        _uiState.value = _uiState.value.copy(selectedBenchmarkId = benchmarkId)
    }


    fun deleteBenchmark(benchmarkId: Long) {
        val updated = _uiState.value.benchmarks.filterNot { it.id == benchmarkId }
        saveBenchmarks(updated)
        _uiState.value = _uiState.value.copy(
            benchmarks = updated,
            selectedBenchmarkId = _uiState.value.selectedBenchmarkId
                ?.takeIf { id -> updated.any { it.id == id } }
                ?: updated.firstOrNull()?.id,
        )
    }

    fun clearBenchmarks() {
        saveBenchmarks(emptyList())
        _uiState.value = _uiState.value.copy(
            benchmarks = emptyList(),
            selectedBenchmarkId = null,
        )
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
                val windowStart = snapshot.capturedAt.minusMillis(estimateWindowMs)
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
                        nextRecordings = (listOf(finalized) + currentState.recordings).take(maxSavedItems)
                        nextSelectedRecordingId = finalized.id
                        nextActiveRecording = null
                        saveRecordings(nextRecordings)
                    }
                }

                var nextActiveBenchmark = currentState.activeBenchmark?.let { updateActiveBenchmark(it, snapshot) }
                var nextBenchmarks = currentState.benchmarks
                var nextSelectedBenchmarkId = currentState.selectedBenchmarkId
                if (nextActiveBenchmark != null) {
                    val elapsedMs = Duration.between(nextActiveBenchmark.startedAt, snapshot.capturedAt).toMillis()
                    if (elapsedMs >= benchmarkDurationMs) {
                        val finalizedBenchmark = buildBenchmarkRecord(nextActiveBenchmark, snapshot)
                        nextBenchmarks = (listOf(finalizedBenchmark) + currentState.benchmarks).take(maxSavedItems)
                        nextSelectedBenchmarkId = finalizedBenchmark.id
                        nextActiveBenchmark = null
                        saveBenchmarks(nextBenchmarks)
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
                    activeBenchmark = nextActiveBenchmark,
                    benchmarks = nextBenchmarks,
                    selectedBenchmarkId = nextSelectedBenchmarkId,
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

    private fun updateActiveBenchmark(
        active: ActiveChargerBenchmarkSession,
        snapshot: BatterySnapshot,
    ): ActiveChargerBenchmarkSession {
        val powerW = snapshot.netPowerW ?: 0f
        val sample = PowerUsageSample(snapshot.capturedAt, powerW)
        return active.copy(samples = active.samples + sample)
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
        val updated = (listOf(finalized) + _uiState.value.recordings).take(maxSavedItems)
        saveRecordings(updated)
        _uiState.value = _uiState.value.copy(
            activeRecording = null,
            recordings = updated,
            selectedRecordingId = finalized.id,
        )
    }

    private fun finalizeBenchmark(
        active: ActiveChargerBenchmarkSession,
        snapshot: BatterySnapshot,
    ) {
        val finalized = buildBenchmarkRecord(active, snapshot)
        val updated = (listOf(finalized) + _uiState.value.benchmarks).take(maxSavedItems)
        saveBenchmarks(updated)
        _uiState.value = _uiState.value.copy(
            activeBenchmark = null,
            benchmarks = updated,
            selectedBenchmarkId = finalized.id,
            benchmarkDraftName = active.name,
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

    private fun buildBenchmarkRecord(
        active: ActiveChargerBenchmarkSession,
        snapshot: BatterySnapshot,
    ): ChargerBenchmarkRecord {
        val rawSamples = if (active.samples.isEmpty()) {
            listOf(PowerUsageSample(snapshot.capturedAt, snapshot.netPowerW ?: 0f))
        } else {
            active.samples
        }
        val peakPowerW = rawSamples.maxOfOrNull { it.powerW } ?: 0f
        val average10sPowerW = averageRecentPower(rawSamples, seconds = 10, endAt = rawSamples.last().capturedAt) ?: 0f
        val average30sPowerW = averageRecentPower(rawSamples, seconds = 30, endAt = rawSamples.last().capturedAt) ?: 0f
        val stabilityStdDevW = standardDeviation(rawSamples.map { it.powerW })
        val timeToTaperMs = computeTimeToTaperMs(rawSamples, peakPowerW)
        val samples = downsampleSamples(rawSamples, maxPoints = 300)
        return ChargerBenchmarkRecord(
            id = active.startedAt.toEpochMilli(),
            name = active.name,
            startedAt = active.startedAt,
            endedAt = samples.last().capturedAt,
            samples = samples,
            peakPowerW = peakPowerW,
            average10sPowerW = average10sPowerW,
            average30sPowerW = average30sPowerW,
            stabilityStdDevW = stabilityStdDevW,
            timeToTaperMs = timeToTaperMs,
            startPercent = active.startPercent,
            endPercent = snapshot.percent,
            startTemperatureC = active.startTemperatureC,
            endTemperatureC = snapshot.temperatureC,
            sourceLabel = active.sourceLabel,
        )
    }

    private fun loadRecordings(): List<PowerUsageRecord> {
        val raw = prefs.getString(KEY_RECORDINGS_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(array.getJSONObject(i).toPowerUsageRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRecordings(records: List<PowerUsageRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RECORDINGS_JSON, array.toString()).apply()
    }

    private fun loadBenchmarks(): List<ChargerBenchmarkRecord> {
        val raw = prefs.getString(KEY_BENCHMARKS_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(array.getJSONObject(i).toBenchmarkRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveBenchmarks(records: List<ChargerBenchmarkRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_BENCHMARKS_JSON, array.toString()).apply()
    }

    private fun PowerUsageRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("startedAt", startedAt.toEpochMilli())
        put("endedAt", endedAt.toEpochMilli())
        put("samples", JSONArray().apply { samples.forEach { put(it.toJson()) } })
        put("totalEnergyMwh", totalEnergyMwh.toDouble())
        putNullable("startRemainingEnergyMwh", startRemainingEnergyMwh)
        putNullable("startFullEnergyMwh", startFullEnergyMwh)
        put("autoStopped", autoStopped)
    }

    private fun JSONObject.toPowerUsageRecord(): PowerUsageRecord = PowerUsageRecord(
        id = getLong("id"),
        startedAt = Instant.ofEpochMilli(getLong("startedAt")),
        endedAt = Instant.ofEpochMilli(getLong("endedAt")),
        samples = getJSONArray("samples").toPowerUsageSamples(),
        totalEnergyMwh = getDouble("totalEnergyMwh").toFloat(),
        startRemainingEnergyMwh = optFloatNullable("startRemainingEnergyMwh"),
        startFullEnergyMwh = optFloatNullable("startFullEnergyMwh"),
        autoStopped = optBoolean("autoStopped", false),
    )

    private fun ChargerBenchmarkRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("startedAt", startedAt.toEpochMilli())
        put("endedAt", endedAt.toEpochMilli())
        put("samples", JSONArray().apply { samples.forEach { put(it.toJson()) } })
        put("peakPowerW", peakPowerW.toDouble())
        put("average10sPowerW", average10sPowerW.toDouble())
        put("average30sPowerW", average30sPowerW.toDouble())
        put("stabilityStdDevW", stabilityStdDevW.toDouble())
        putNullable("timeToTaperMs", timeToTaperMs?.toFloat())
        putNullable("startPercent", startPercent)
        putNullable("endPercent", endPercent)
        putNullable("startTemperatureC", startTemperatureC)
        putNullable("endTemperatureC", endTemperatureC)
        put("sourceLabel", sourceLabel)
    }

    private fun JSONObject.toBenchmarkRecord(): ChargerBenchmarkRecord = ChargerBenchmarkRecord(
        id = getLong("id"),
        name = optString("name", "Benchmark"),
        startedAt = Instant.ofEpochMilli(getLong("startedAt")),
        endedAt = Instant.ofEpochMilli(getLong("endedAt")),
        samples = getJSONArray("samples").toPowerUsageSamples(),
        peakPowerW = optDouble("peakPowerW", 0.0).toFloat(),
        average10sPowerW = optDouble("average10sPowerW", 0.0).toFloat(),
        average30sPowerW = optDouble("average30sPowerW", 0.0).toFloat(),
        stabilityStdDevW = optDouble("stabilityStdDevW", 0.0).toFloat(),
        timeToTaperMs = optFloatNullable("timeToTaperMs")?.toLong(),
        startPercent = optFloatNullable("startPercent"),
        endPercent = optFloatNullable("endPercent"),
        startTemperatureC = optFloatNullable("startTemperatureC"),
        endTemperatureC = optFloatNullable("endTemperatureC"),
        sourceLabel = optString("sourceLabel", "Unknown"),
    )

    private fun PowerUsageSample.toJson(): JSONObject = JSONObject().apply {
        put("capturedAt", capturedAt.toEpochMilli())
        put("powerW", powerW.toDouble())
    }

    private fun JSONArray.toPowerUsageSamples(): List<PowerUsageSample> = buildList {
        for (i in 0 until length()) {
            val sample = getJSONObject(i)
            add(
                PowerUsageSample(
                    capturedAt = Instant.ofEpochMilli(sample.getLong("capturedAt")),
                    powerW = sample.getDouble("powerW").toFloat(),
                )
            )
        }
    }

    private fun JSONObject.putNullable(key: String, value: Float?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value.toDouble())
    }

    private fun JSONObject.optFloatNullable(key: String): Float? =
        if (isNull(key)) null else optDouble(key).toFloat()

    companion object {
        private const val PREFS_NAME = "battery_nerd_storage"
        private const val KEY_RECORDINGS_JSON = "recordings_json"
        private const val KEY_BENCHMARKS_JSON = "benchmarks_json"

        private fun averageRecentPower(samples: List<PowerUsageSample>, seconds: Long, endAt: Instant): Float? {
            val since = endAt.minusSeconds(seconds)
            return samples.filter { !it.capturedAt.isBefore(since) }
                .map { it.powerW }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
        }

        private fun standardDeviation(values: List<Float>): Float {
            if (values.size < 2) return 0f
            val mean = values.average().toFloat()
            val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
            return sqrt(variance)
        }

        private fun computeTimeToTaperMs(samples: List<PowerUsageSample>, peakPowerW: Float): Long? {
            if (samples.size < 4 || peakPowerW <= 0f) return null
            val threshold = peakPowerW * 0.9f
            val peakTime = samples.maxByOrNull { it.powerW }?.capturedAt ?: return null
            val afterPeak = samples.filter { !it.capturedAt.isBefore(peakTime) }
            if (afterPeak.size < 3) return null
            for (index in afterPeak.indices) {
                val window = afterPeak.drop(index).take(3)
                if (window.size == 3) {
                    val average = window.map { it.powerW }.average().toFloat()
                    if (average < threshold) {
                        return Duration.between(samples.first().capturedAt, window.first().capturedAt).toMillis()
                    }
                }
            }
            return null
        }

        private fun downsampleSamples(samples: List<PowerUsageSample>, maxPoints: Int): List<PowerUsageSample> {
            if (samples.size <= maxPoints) return samples
            val step = samples.size.toFloat() / maxPoints.toFloat()
            return buildList {
                var index = 0f
                while (index < samples.size) {
                    add(samples[index.toInt()])
                    index += step
                }
                if (lastOrNull() != samples.last()) add(samples.last())
            }.take(maxPoints)
        }

        private fun batterySourceLabel(snapshot: BatterySnapshot): String = when (snapshot.plugType) {
            android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            android.os.BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            else -> "On battery"
        }
    }
}
