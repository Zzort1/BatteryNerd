package au.com.evagames.batterynerd.data

import java.time.Instant
import kotlin.math.abs

private const val MICROAMPS_PER_AMP = 1_000_000f
private const val MILLIAMPS_PER_AMP = 1_000f

enum class BatteryCurrentScale {
    MICRO_AMPS,
    MILLI_AMPS,
}

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

    val detectedCurrentScale: BatteryCurrentScale?
        get() = detectCurrentScale(rawCurrent = currentNowUa, voltageMv = voltageMv, isCharging = isCharging)

    val voltageV: Float?
        get() = voltageMv?.div(1000f)

    /**
     * Raw device-reported current converted to amps using the detected per-device scale.
     */
    val currentNowA: Float?
        get() = scaleCurrentToAmps(rawCurrent = currentNowUa, currentScale = detectedCurrentScale)

    val currentAverageA: Float?
        get() = scaleCurrentToAmps(
            rawCurrent = currentAverageUa,
            currentScale = detectCurrentScale(rawCurrent = currentAverageUa, voltageMv = voltageMv, isCharging = isCharging)
        )

    /**
     * Approximate net power at the battery boundary, derived from the detected current scale.
     */
    val netPowerW: Float?
        get() {
            val currentA = currentNowA ?: return null
            val volts = voltageV ?: return null
            return currentA * volts
        }

    val storedEnergyMwh: Float?
        get() = energyCounterNwh?.div(1_000_000f)
            ?: if (chargeCounterUah != null && voltageMv != null) {
                (chargeCounterUah.toLong() * voltageMv.toLong()) / 1_000_000f
            } else {
                null
            }

    val estimatedFullEnergyMwh: Float?
        get() {
            val energy = storedEnergyMwh ?: return null
            val pct = (percent ?: capacityPercent?.toFloat()) ?: return null
            if (pct <= 0f) return null
            return energy / (pct / 100f)
        }

    val energyToFullMwh: Float?
        get() {
            val full = estimatedFullEnergyMwh ?: return null
            val current = storedEnergyMwh ?: return null
            return (full - current).coerceAtLeast(0f)
        }

    val estimatedTimeRemainingMs: Long?
        get() {
            val energyMwh = storedEnergyMwh ?: return null
            val powerW = netPowerW ?: return null
            val dischargePowerW = -powerW
            if (isCharging || dischargePowerW <= 0f) return null

            val energyWh = energyMwh / 1000f
            return ((energyWh / dischargePowerW) * 3600_000f).toLong().takeIf { it > 0L }
        }

    fun estimatedTimeRemainingMsForPower(powerW: Float?): Long? {
        val energyMwh = storedEnergyMwh ?: return null
        val smoothedPowerW = powerW ?: return null
        val dischargePowerW = -smoothedPowerW
        if (isCharging || dischargePowerW <= 0f) return null

        val energyWh = energyMwh / 1000f
        return ((energyWh / dischargePowerW) * 3600_000f).toLong().takeIf { it > 0L }
    }

    fun estimatedTimeToFullMsForPower(powerW: Float?): Long? {
        chargeTimeRemainingMs?.takeIf { it > 0L }?.let { return it }
        val remainingMwh = energyToFullMwh ?: return null
        val smoothedPowerW = powerW ?: return null
        if (!isCharging || smoothedPowerW <= 0f || remainingMwh <= 0f) return null

        val remainingWh = remainingMwh / 1000f
        return ((remainingWh / smoothedPowerW) * 3600_000f).toLong().takeIf { it > 0L }
    }

    val estimatedTimeToFullMs: Long?
        get() {
            chargeTimeRemainingMs?.takeIf { it > 0L }?.let { return it }
            val remainingMwh = energyToFullMwh ?: return null
            val powerW = netPowerW ?: return null
            if (!isCharging || powerW <= 0f || remainingMwh <= 0f) return null

            val remainingWh = remainingMwh / 1000f
            return ((remainingWh / powerW) * 3600_000f).toLong().takeIf { it > 0L }
        }
}

private fun scaleCurrentToAmps(rawCurrent: Int?, currentScale: BatteryCurrentScale?): Float? {
    if (rawCurrent == null || currentScale == null) return null
    return when (currentScale) {
        BatteryCurrentScale.MICRO_AMPS -> rawCurrent / MICROAMPS_PER_AMP
        BatteryCurrentScale.MILLI_AMPS -> rawCurrent / MILLIAMPS_PER_AMP
    }
}

private fun detectCurrentScale(
    rawCurrent: Int?,
    voltageMv: Int?,
    isCharging: Boolean,
): BatteryCurrentScale? {
    if (rawCurrent == null) return null

    val absRaw = abs(rawCurrent)
    if (voltageMv == null || voltageMv <= 0) {
        return if (absRaw >= 10_000) BatteryCurrentScale.MICRO_AMPS else BatteryCurrentScale.MILLI_AMPS
    }

    val volts = voltageMv / 1000f
    val microPowerW = abs((rawCurrent / MICROAMPS_PER_AMP) * volts)
    val milliPowerW = abs((rawCurrent / MILLIAMPS_PER_AMP) * volts)

    val microScore = plausibilityScore(microPowerW = microPowerW, isCharging = isCharging, rawMagnitude = absRaw)
    val milliScore = plausibilityScore(microPowerW = milliPowerW, isCharging = isCharging, rawMagnitude = absRaw / 1000)

    return when {
        milliScore > microScore -> BatteryCurrentScale.MILLI_AMPS
        microScore > milliScore -> BatteryCurrentScale.MICRO_AMPS
        absRaw >= 10_000 -> BatteryCurrentScale.MICRO_AMPS
        else -> BatteryCurrentScale.MILLI_AMPS
    }
}

private fun plausibilityScore(microPowerW: Float, isCharging: Boolean, rawMagnitude: Int): Int {
    if (microPowerW.isNaN() || microPowerW.isInfinite()) return 0
    if (microPowerW > 120f) return 0
    if (microPowerW > 80f) return 1
    if (microPowerW > 45f) return 2

    var score = 0
    if (rawMagnitude >= 50) score += 1

    score += when {
        isCharging && microPowerW in 0.3f..35f -> 6
        !isCharging && microPowerW in 0.1f..20f -> 6
        microPowerW in 0.05f..40f -> 4
        microPowerW in 0.005f..60f -> 2
        else -> 0
    }

    if (microPowerW < 0.01f && rawMagnitude < 1_000) {
        score -= 1
    }

    return score
}
