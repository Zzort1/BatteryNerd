package com.evagames.batterynerd.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

class BatteryRepository(private val context: Context) {

    private val batteryManager: BatteryManager =
        context.getSystemService(BatteryManager::class.java)

    fun observeBattery(sampleIntervalMs: Long): Flow<BatterySnapshot> = flow {
        while (true) {
            emit(readSnapshot())
            delay(sampleIntervalMs)
        }
    }

    fun readSnapshot(): BatterySnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)?.takeIf { it > 0 }
        val percent = if (level != null && scale != null) level * 100f / scale else null
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
        val temperatureDeciC = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val present = batteryIntent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)

        return BatterySnapshot(
            capturedAt = Instant.now(),
            level = level,
            scale = scale,
            percent = percent,
            status = status,
            health = health,
            plugType = plugged,
            isCharging = batteryManager.isCharging,
            currentNowUa = readIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            currentAverageUa = readIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            chargeCounterUah = readIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            capacityPercent = readIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            energyCounterNwh = readLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER),
            chargeTimeRemainingMs = readChargeTimeRemaining(),
            voltageMv = voltageMv,
            temperatureDeciC = temperatureDeciC,
            technology = technology,
            present = present
        )
    }

    private fun readChargeTimeRemaining(): Long? {
        return batteryManager.computeChargeTimeRemaining().takeIf { it >= 0L }
    }

    private fun readIntProperty(id: Int): Int? {
        val value = batteryManager.getIntProperty(id)
        return value.takeUnless { it == Int.MIN_VALUE }
    }

    private fun readLongProperty(id: Int): Long? {
        val value = batteryManager.getLongProperty(id)
        return value.takeUnless { it == Long.MIN_VALUE }
    }

    private fun readIntPropertyIfSupported(id: Int): Int? = try {
        readIntProperty(id)
    } catch (_: Throwable) {
        null
    }

    private fun readLongPropertyIfSupported(id: Int): Long? = try {
        readLongProperty(id)
    } catch (_: Throwable) {
        null
    }
}
