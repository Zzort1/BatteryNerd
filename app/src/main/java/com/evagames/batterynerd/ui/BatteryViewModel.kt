package com.evagames.batterynerd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evagames.batterynerd.data.BatteryRepository
import com.evagames.batterynerd.data.BatterySnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatteryUiState(
    val snapshot: BatterySnapshot? = null,
    val sampleIntervalMs: Long = 500L,
    val maxObservedPowerW: Float? = null,
    val minObservedPowerW: Float? = null,
    val history: List<BatterySnapshot> = emptyList(),
    val rollingAveragePowerW: Float? = null,
    val estimatedTimeRemainingMs: Long? = null,
    val estimatedTimeToFullMs: Long? = null
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private val rollingWindowMs = 60_000L

    private fun historyCapacityFor(intervalMs: Long): Int = ((rollingWindowMs / intervalMs) + 10L).toInt().coerceAtLeast(60)

    init {
        startObserving()
    }

    fun setSampleInterval(intervalMs: Long) {
        _uiState.value = _uiState.value.copy(sampleIntervalMs = intervalMs)
        startObserving()
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
                    estimatedTimeToFullMs = estimatedTimeToFullMs
                )
            }
        }
    }
}
