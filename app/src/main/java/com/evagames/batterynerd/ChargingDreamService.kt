package com.evagames.batterynerd

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.evagames.batterynerd.data.BatteryRepository
import com.evagames.batterynerd.data.BatterySnapshot
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class ChargingDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: BatteryRepository
    private lateinit var dreamView: ChargingDreamView

    private val ticker = object : Runnable {
        override fun run() {
            val snapshot = repository.readSnapshot()
            dreamView.updateSnapshot(snapshot)
            handler.postDelayed(this, 500L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        repository = BatteryRepository(applicationContext)
        dreamView = ChargingDreamView(this)
        setContentView(dreamView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(ticker)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(ticker)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }
}

private class ChargingDreamView(context: Context) : FrameLayout(context) {

    private val batteryView = DreamBatteryView(context)
    private val wattsView = TextView(context)
    private val statusView = TextView(context)
    private val estimateView = TextView(context)
    private val percentView = TextView(context)

    init {
        setBackgroundColor(Color.BLACK)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setPadding(dp(24))
        }

        val batteryParams = LinearLayout.LayoutParams(dp(240), dp(300)).apply {
            topMargin = dp(36)
            bottomMargin = dp(20)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(batteryView, batteryParams)

        wattsView.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(wattsView, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        statusView.apply {
            setTextColor(0xFFD7D3F3.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
        }
        content.addView(statusView, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        estimateView.apply {
            setTextColor(0xFFE6E1FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        content.addView(estimateView, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })

        percentView.apply {
            setTextColor(0xFFBBB7D3.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        content.addView(percentView, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })

        addView(content)
    }

    fun updateSnapshot(snapshot: BatterySnapshot) {
        batteryView.updateSnapshot(snapshot)
        wattsView.text = formatCompactPower(snapshot.netPowerW)
        statusView.text = if (snapshot.isCharging) "Charging" else "On battery"
        estimateView.text = when {
            snapshot.isCharging -> snapshot.estimatedTimeToFullMs?.let { "${formatDuration(it)} until full" } ?: "Estimating time to full…"
            else -> snapshot.estimatedTimeRemainingMs?.let { "${formatDuration(it)} left" } ?: "Estimating runtime…"
        }
        percentView.text = snapshot.percent?.let { String.format(Locale.US, "%.1f%%", it) } ?: ""
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class DreamBatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var snapshot: BatterySnapshot? = null
    private var phase = 0f
    private val frameHandler = Handler(Looper.getMainLooper())

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(9f)
        color = 0xFFD0CFD9.toInt()
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x66FFFFFF
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dropletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xA0C9D4FF.toInt()
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
    }

    private val animator = object : Runnable {
        override fun run() {
            val powerFactor = ((snapshot?.netPowerW ?: 0f).coerceAtLeast(0f)).coerceAtMost(15f) / 15f
            phase += 0.008f + powerFactor * 0.004f
            if (phase > 1f) phase -= 1f
            invalidate()
            frameHandler.postDelayed(this, 16L)
        }
    }

    init {
        frameHandler.post(animator)
    }

    fun updateSnapshot(snapshot: BatterySnapshot) {
        this.snapshot = snapshot
        invalidate()
    }

    override fun onDetachedFromWindow() {
        frameHandler.removeCallbacks(animator)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = snapshot ?: return
        canvas.drawPaint(bgPaint)

        val w = width.toFloat()
        val h = height.toFloat()
        val tankLeft = w * 0.2f
        val tankTop = h * 0.14f
        val tankWidth = w * 0.6f
        val tankHeight = h * 0.72f
        val radius = dp(30f)
        val innerPad = dp(10f)
        val capWidth = tankWidth * 0.35f
        val capHeight = h * 0.08f

        val capRect = RectF(w / 2f - capWidth / 2f, tankTop - capHeight * 0.45f, w / 2f + capWidth / 2f, tankTop + capHeight * 0.25f)
        canvas.drawRoundRect(capRect, capHeight / 2f, capHeight / 2f, capPaint)

        val outerRect = RectF(tankLeft, tankTop, tankLeft + tankWidth, tankTop + tankHeight)
        val innerRect = RectF(outerRect.left + innerPad, outerRect.top + innerPad, outerRect.right - innerPad, outerRect.bottom - innerPad)

        val level = ((s.percent ?: 0f) / 100f).coerceIn(0f, 1f)
        val liquidTop = innerRect.bottom - innerRect.height() * level

        val liquidPath = Path().apply {
            moveTo(innerRect.left, innerRect.bottom)
            lineTo(innerRect.left, liquidTop)
            var x = innerRect.left
            val chargePower = max((s.netPowerW ?: 0f), 0f).coerceAtMost(15f)
            val amplitude = dp(4f) + chargePower * dp(0.35f)
            val waveLength = innerRect.width() / (1.65f + chargePower * 0.02f)
            while (x <= innerRect.right + 6f) {
                val progress = ((x - innerRect.left) / waveLength) + phase
                val y = liquidTop + sin(progress * 2f * PI).toFloat() * amplitude
                lineTo(x, y)
                x += 8f
            }
            lineTo(innerRect.right, innerRect.bottom)
            close()
        }

        val gradient = LinearGradient(
            0f,
            liquidTop,
            0f,
            innerRect.bottom,
            intArrayOf(0xFFC3D2FF.toInt(), 0xFFBBC8F0.toInt(), 0xFFD8B7D8.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        liquidPaint.shader = gradient

        val clipPath = Path().apply { addRoundRect(innerRect, radius - innerPad, radius - innerPad, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawPath(liquidPath, liquidPaint)
        canvas.restore()
        drawDroplets(canvas, innerRect, liquidTop, s)

        canvas.drawRoundRect(outerRect, radius, radius, borderPaint)
        drawBolt(canvas, outerRect)
    }

    private fun drawDroplets(canvas: Canvas, innerRect: RectF, liquidTop: Float, snapshot: BatterySnapshot) {
        val power = snapshot.netPowerW ?: 0f
        val positive = power.coerceAtLeast(0f)
        val negative = (-power).coerceAtLeast(0f)
        val count = when {
            snapshot.isCharging && positive > 0f -> positive.toInt().coerceIn(1, 18)
            !snapshot.isCharging && negative > 0f -> negative.toInt().coerceIn(1, 18)
            else -> 0
        }
        if (count <= 0) return

        val laneCount = count.coerceAtMost(25)
        val laneInset = 0.12f
        val laneWidthFraction = 1f - laneInset * 2f

        repeat(count) { index ->
            val lane = (pseudoRandom01(index * 53 + 17) * laneCount).toInt().coerceIn(0, laneCount - 1)
            val laneFraction = if (laneCount == 1) {
                0.5f
            } else {
                laneInset + (lane / (laneCount - 1f)) * laneWidthFraction
            }
            val laneJitter = (pseudoRandom01(index * 97 + 29) - 0.5f) * (laneWidthFraction / laneCount) * 0.7f
            val x = innerRect.left + innerRect.width() * (laneFraction + laneJitter).coerceIn(0.1f, 0.9f)

            val localPhase = (phase + index * 0.097f + pseudoRandom01(index * 41 + 5) * 0.33f) % 1f
            val rowOffset = pseudoRandom01(index * 19 + 7) * dp(42f)
            val radius = dp(2.8f) + if (snapshot.isCharging) (1f - localPhase) * dp(2.6f) else localPhase * dp(2.6f)
            val y = if (snapshot.isCharging) {
                val startY = innerRect.top - dp(34f) - rowOffset
                val travel = (liquidTop - startY - dp(8f)).coerceAtLeast(dp(24f))
                startY + localPhase * travel
            } else {
                val startY = (liquidTop + dp(10f) + rowOffset * 0.4f).coerceAtMost(innerRect.bottom - dp(8f))
                val endY = innerRect.top - dp(34f) - rowOffset
                val travel = (startY - endY).coerceAtLeast(dp(24f))
                startY - localPhase * travel
            }
            dropletPaint.alpha = if (snapshot.isCharging) {
                (90 + (1f - localPhase) * 120).toInt().coerceIn(0, 255)
            } else {
                (90 + localPhase * 120).toInt().coerceIn(0, 255)
            }
            canvas.drawCircle(x, y, radius, dropletPaint)
        }
    }

    private fun drawBolt(canvas: Canvas, outerRect: RectF) {
        val cx = outerRect.centerX()
        val cy = outerRect.centerY()
        val p = Path().apply {
            moveTo(cx - dp(14f), cy - dp(34f))
            lineTo(cx + dp(3f), cy - dp(34f))
            lineTo(cx - dp(4f), cy)
            lineTo(cx + dp(16f), cy)
            lineTo(cx - dp(10f), cy + dp(38f))
            lineTo(cx - dp(3f), cy + dp(8f))
            lineTo(cx - dp(20f), cy + dp(8f))
            close()
        }
        canvas.drawPath(p, boltPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private fun pseudoRandom01(seed: Int): Float {
    var x = seed * 1103515245 + 12345
    x = x xor (x ushr 16)
    return ((x and 0x7fffffff) / 2147483647f).coerceIn(0f, 1f)
}

private fun formatCompactPower(powerW: Float?): String {
    val value = powerW ?: return "—"
    val abs = kotlin.math.abs(value)
    return if (abs < 1f) {
        String.format(Locale.US, "%.0f mW", value * 1000f)
    } else {
        String.format(Locale.US, "%.1f W", value)
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, minutes)
    } else {
        String.format(Locale.US, "%dm", minutes)
    }
}
