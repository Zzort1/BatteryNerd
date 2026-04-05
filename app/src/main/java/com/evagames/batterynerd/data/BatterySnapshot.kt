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

            // Energy is stored here in mWh, while power is displayed/scaled in W.
            // Convert mWh -> Wh before deriving hours, otherwise the estimate is 1000x too large.
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

            // Energy is stored in mWh, so convert to Wh before dividing by watts.
            val remainingWh = remainingMwh / 1000f
            return ((remainingWh / powerW) * 3600_000f).toLong().takeIf { it > 0L }
        }
}
