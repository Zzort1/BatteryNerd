package au.com.evagames.batterynerd.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import au.com.evagames.batterynerd.data.BatterySnapshot
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun BatteryDreamTank(
    snapshot: BatterySnapshot,
    modifier: Modifier = Modifier,
    tankHeight: Dp = 280.dp,
    accentColor: Color,
    accentBottomColor: Color,
    outlineColor: Color,
    capColor: Color,
    boltColor: Color,
) {
    val percent = ((snapshot.percent ?: 0f) / 100f).coerceIn(0f, 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "battery_dream_visual")
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
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "droplet_offset"
    )

    Canvas(modifier = modifier.height(tankHeight)) {
        val strokeWidth = 10.dp.toPx()
        val innerPadding = strokeWidth * 0.7f
        val capWidth = size.width * 0.22f
        val capHeight = size.height * 0.07f
        val tankLeft = size.width * 0.18f
        val tankTop = size.height * 0.12f
        val tankWidth = size.width * 0.64f
        val tankHeightPx = size.height * 0.72f
        val radius = 28.dp.toPx()
        val innerLeft = tankLeft + innerPadding
        val innerTop = tankTop + innerPadding
        val innerWidth = tankWidth - innerPadding * 2f
        val innerHeight = tankHeightPx - innerPadding * 2f
        val liquidTop = innerTop + innerHeight * (1f - percent)
        val powerW = snapshot.netPowerW ?: 0f
        val positivePowerW = powerW.coerceAtLeast(0f)
        val negativePowerW = (-powerW).coerceAtLeast(0f)
        val energyEnteringBattery = positivePowerW > 0.05f || (snapshot.isCharging && snapshot.plugType != null && snapshot.plugType != 0)
        val energyLeavingBattery = negativePowerW > 0.05f && !energyEnteringBattery
        val chargingDropletCount = if (energyEnteringBattery) positivePowerW.toInt().coerceIn(1, 18) else 0
        val dischargingDropletCount = if (energyLeavingBattery) negativePowerW.toInt().coerceIn(1, 18) else 0

        drawRoundRect(
            color = capColor,
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
            colors = listOf(accentColor.copy(alpha = 0.78f), accentColor, accentBottomColor.copy(alpha = 0.92f)),
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
            val laneCount = chargingDropletCount.coerceAtMost(25)
            val laneInset = 0.12f
            val laneWidthFraction = 1f - laneInset * 2f
            repeat(chargingDropletCount) { index ->
                val lane = (pseudoRandom01(index * 53 + 17) * laneCount).toInt().coerceIn(0, laneCount - 1)
                val laneFraction = if (laneCount == 1) 0.5f else laneInset + (lane / (laneCount - 1f)) * laneWidthFraction
                val laneJitter = (pseudoRandom01(index * 97 + 29) - 0.5f) * (laneWidthFraction / laneCount) * 0.7f
                val laneX = innerLeft + innerWidth * (laneFraction + laneJitter).coerceIn(0.1f, 0.9f)
                val phase = (dropletOffset + index * 0.097f + pseudoRandom01(index * 41 + 5) * 0.33f) % 1f
                val rowOffset = pseudoRandom01(index * 19 + 7) * 42.dp.toPx()
                val startY = innerTop - 34.dp.toPx() - rowOffset
                val travel = (liquidTop - startY - 8.dp.toPx()).coerceAtLeast(24.dp.toPx())
                val y = startY + phase * travel
                val radiusPx = 2.8.dp.toPx() + (1f - phase) * 2.6.dp.toPx()
                drawCircle(
                    color = accentColor.copy(alpha = 0.28f + (1f - phase) * 0.48f),
                    radius = radiusPx,
                    center = Offset(laneX, y)
                )
            }
        }

        if (dischargingDropletCount > 0) {
            val laneCount = dischargingDropletCount.coerceAtMost(25)
            val laneInset = 0.12f
            val laneWidthFraction = 1f - laneInset * 2f
            repeat(dischargingDropletCount) { index ->
                val lane = (pseudoRandom01(index * 53 + 17) * laneCount).toInt().coerceIn(0, laneCount - 1)
                val laneFraction = if (laneCount == 1) 0.5f else laneInset + (lane / (laneCount - 1f)) * laneWidthFraction
                val laneJitter = (pseudoRandom01(index * 97 + 29) - 0.5f) * (laneWidthFraction / laneCount) * 0.7f
                val laneX = innerLeft + innerWidth * (laneFraction + laneJitter).coerceIn(0.1f, 0.9f)
                val phase = (dropletOffset + index * 0.097f + pseudoRandom01(index * 41 + 5) * 0.33f) % 1f
                val rowOffset = pseudoRandom01(index * 19 + 7) * 42.dp.toPx()
                val startY = (liquidTop + 10.dp.toPx() + rowOffset * 0.4f).coerceAtMost(innerTop + innerHeight - 8.dp.toPx())
                val endY = innerTop - 34.dp.toPx() - rowOffset
                val travel = (startY - endY).coerceAtLeast(24.dp.toPx())
                val y = startY - phase * travel
                val radiusPx = 2.8.dp.toPx() + phase * 2.6.dp.toPx()
                drawCircle(
                    color = accentColor.copy(alpha = 0.24f + phase * 0.52f),
                    radius = radiusPx,
                    center = Offset(laneX, y)
                )
            }
        }

        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(tankLeft, tankTop),
            size = Size(tankWidth, tankHeightPx),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokeWidth)
        )

        val boltCenter = Offset(size.width / 2f, tankTop + tankHeightPx / 2.2f)
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
        drawPath(path = boltPath, color = boltColor.copy(alpha = if (energyEnteringBattery) 0.92f else 0.35f))
    }
}

private fun pseudoRandom01(seed: Int): Float {
    var x = seed * 1103515245 + 12345
    x = x xor (x ushr 16)
    return ((x and 0x7fffffff) / 2147483647f).coerceIn(0f, 1f)
}
