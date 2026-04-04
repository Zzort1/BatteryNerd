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
    val maxObservedPowerMw: Float? = null,
    val minObservedPowerMw: Float? = null
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

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
                val currentMax = _uiState.value.maxObservedPowerMw
                val currentMin = _uiState.value.minObservedPowerMw
                val power = snapshot.netPowerMw
                _uiState.value = _uiState.value.copy(
                    snapshot = snapshot,
                    maxObservedPowerMw = when {
                        power == null -> currentMax
                        currentMax == null -> power
                        else -> maxOf(currentMax, power)
                    },
                    minObservedPowerMw = when {
                        power == null -> currentMin
                        currentMin == null -> power
                        else -> minOf(currentMin, power)
                    }
                )
            }
        }
    }
}
