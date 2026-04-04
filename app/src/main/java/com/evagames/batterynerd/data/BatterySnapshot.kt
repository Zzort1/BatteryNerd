package com.evagames.batterynerd.data

import java.time.Instant

data class BatterySnapshot(
    val capturedAt: Instant,
    val level: Int?,
    val scale: Int?,
    val percent: Float?,
    val status: Int?,
    val health: Int?,
    val plugType: Int?,
    val isCharging: Boolean,
    val currentNowUa: Int?,
    val currentAverageUa: Int?,
    val chargeCounterUah: Int?,
    val capacityPercent: Int?,
    val energyCounterNwh: Long?,
    val chargeTimeRemainingMs: Long?,
    val voltageMv: Int?,
    val temperatureDeciC: Int?,
    val technology: String?,
    val present: Boolean?
) {
    val temperatureC: Float?
        get() = temperatureDeciC?.div(10f)

    /**
     * Many devices appear to expose current values scaled as though they were µA,
     * but the observed magnitudes line up more closely with amps after dividing by 1000.
     */
    val currentNowA: Float?
        get() = currentNowUa?.div(1000f)

    val currentAverageA: Float?
        get() = currentAverageUa?.div(1000f)

    val voltageV: Float?
        get() = voltageMv?.div(1000f)

    /**
     * Approximate net power at the battery boundary, not charger coil output.
     */
    /**
     * Display-scaled battery power. Numerically this is the same calculation as before,
     * but surfaced as watts to match the observed device behaviour.
     */
    val netPowerW: Float?
        get() = if (currentNowUa != null && voltageMv != null) {
            (currentNowUa.toLong() * voltageMv.toLong()) / 1_000_000f
        } else {
            null
        }

    val storedEnergyMwh: Float?
        get() = energyCounterNwh?.div(1_000_000f)
}
