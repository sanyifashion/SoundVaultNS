package Views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Theme {
        MOUNTAINS, RYTHM_BARS, ORBIT
    }

    private var currentTheme: Theme = Theme.MOUNTAINS

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val numBins = 64
    private var smoothedMagnitudes = FloatArray(numBins)
    private var intensity = 0f
    private var beatPulse = 0f
    private var animationPhase = 0f
    private var lastUpdateTime = System.currentTimeMillis()

    private var peakY = FloatArray(numBins)
    private var peakVelocity = FloatArray(numBins)
    private val gravityValue = 1500f

    private val layerColors = intArrayOf(
        Color.argb(180, 103, 58, 183),
        Color.argb(150, 33, 150, 243),
        Color.argb(130, 0, 188, 212)
    )

    private val mountainColor = Color.argb(180, 103, 58, 183)
    private val barColor = Color.argb(200, 255, 64, 129)

    fun setTheme(theme: Theme) {
        currentTheme = theme
        resetPhysics()
        invalidate()
    }

    fun setThemeByName(themeName: String) {
        try {
            currentTheme = Theme.valueOf(themeName)
            resetPhysics()
            invalidate()
        } catch (e: Exception) {
            currentTheme = Theme.MOUNTAINS
        }
    }

    fun getCurrentThemeName(): String = currentTheme.name

    fun nextTheme(): String {
        currentTheme = when (currentTheme) {
            Theme.MOUNTAINS -> Theme.RYTHM_BARS
            Theme.RYTHM_BARS -> Theme.ORBIT
            Theme.ORBIT -> Theme.MOUNTAINS
        }
        resetPhysics()
        invalidate()
        return currentTheme.name
    }

    private fun resetPhysics() {
        val h = height.toFloat()
        if (h <= 0) return
        val centerY = if (currentTheme == Theme.RYTHM_BARS) h / 2f else h * 0.65f
        for (i in 0 until numBins) {
            peakY[i] = centerY
            peakVelocity[i] = 0f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetPhysics()
    }

    fun updateVisualizer(waveform: ByteArray?, fft: ByteArray?) {
        if (waveform != null) {
            var sum = 0f
            for (i in waveform.indices) {
                val amplitude = (waveform[i].toInt() and 0xFF) - 128
                sum += (amplitude * amplitude).toFloat()
            }
            val rms = sqrt(sum / waveform.size)
            val targetIntensity = (rms / 45f).coerceIn(0f, 1.5f)
            
            if (targetIntensity > intensity + 0.1f) {
                beatPulse = 1.0f 
            }
            
            intensity = intensity * 0.6f + targetIntensity * 0.4f
        }

        if (fft != null) {
            for (i in 0 until numBins) {
                val r = fft[i * 2].toInt()
                val j = fft[i * 2 + 1].toInt()
                val magnitude = sqrt((r * r + j * j).toDouble()).toFloat()
                smoothedMagnitudes[i] = smoothedMagnitudes[i] * 0.7f + magnitude * 0.3f
            }
        }
        invalidate()
    }

    private fun updatePhysics(dt: Float) {
        val h = height.toFloat()
        if (h <= 0) return
        val centerY = if (currentTheme == Theme.RYTHM_BARS) h / 2f else h * 0.65f
        
        for (i in 0 until numBins) {
            val barHeight = smoothedMagnitudes[i] * 5.0f * (intensity + 0.2f)
            val targetY = centerY - barHeight
            
            if (targetY < peakY[i]) {
                peakY[i] = targetY
                peakVelocity[i] = 0f
            } else {
                peakVelocity[i] += gravityValue * dt
                peakY[i] += peakVelocity[i] * dt
                if (peakY[i] > centerY) {
                    peakY[i] = centerY
                    peakVelocity[i] = 0f
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val now = System.currentTimeMillis()
        val dt = (now - lastUpdateTime) / 1000f
        lastUpdateTime = now

        updatePhysics(dt)

        beatPulse *= 0.92f
        if (beatPulse < 0.01f) beatPulse = 0f

        if (intensity > 0.005f) {
            animationPhase += dt * (1.0f + (intensity + beatPulse * 0.8f) * 4.5f)
        } else {
            intensity *= 0.85f
            if (intensity < 0.001f) {
                intensity = 0f
            }
            animationPhase += dt * 0.4f
        }

        val w = width.toFloat()
        val h = height.toFloat()

        when (currentTheme) {
            Theme.MOUNTAINS -> {
                val centerY = h * 0.65f
                drawMountains(canvas, w, h, centerY)
                drawFrequencyLayer(canvas, w, h, centerY, 1, 8..24)
                drawFrequencyLayer(canvas, w, h, centerY, 2, 24..63)
            }
            Theme.RYTHM_BARS -> drawRythmBars(canvas, w, h)
            Theme.ORBIT -> drawOrbit(canvas, w, h)
        }

        invalidate()
    }

    private fun drawMountains(canvas: Canvas, w: Float, h: Float, centerY: Float) {
        val path = Path()
        mountainPaint.color = mountainColor
        mountainPaint.style = Paint.Style.FILL
        path.moveTo(-10f, h + 10f)
        path.lineTo(-10f, centerY)
        for (i in 0 until numBins) {
            val x = (i.toFloat() / (numBins - 1)) * (w + 20f) - 10f
            val mag = smoothedMagnitudes[i]
            val barHeight = mag * 4.5f * (intensity + 0.1f)
            path.lineTo(x, centerY - barHeight)
        }
        path.lineTo(w + 10f, centerY)
        path.lineTo(w + 10f, h + 10f)
        path.close()
        canvas.drawPath(path, mountainPaint)
    }

    private fun drawRythmBars(canvas: Canvas, w: Float, h: Float) {
        val centerY = h / 2f
        val barWidth = w / numBins
        val spacing = 2f
        
        barPaint.style = Paint.Style.FILL
        
        for (i in 0 until numBins) {
            val x = i * barWidth
            val mag = smoothedMagnitudes[i]
            val barHeight = mag * 5.0f * (intensity + 0.1f)
            
            if (barHeight > 2f) {
                barPaint.color = barColor
                barPaint.alpha = (180 * (intensity + 0.2f)).toInt().coerceIn(0, 255)
                
                // Mirror bars
                canvas.drawRect(x, centerY - barHeight, x + barWidth - spacing, centerY, barPaint)
                canvas.drawRect(x, centerY, x + barWidth - spacing, centerY + barHeight, barPaint)
            }
            
            // Draw peaks if they are sufficiently away from the center
            if (peakY[i] < centerY - 4f) {
                barPaint.color = Color.rgb(255, 64, 129)
                barPaint.alpha = 255
                
                // Top Peak (above the bar)
                canvas.drawRect(x, peakY[i], x + barWidth - spacing, peakY[i] + 4f, barPaint)
                
                // Bottom Peak (mirrored)
                val mirroredPeakY = centerY + (centerY - peakY[i])
                canvas.drawRect(x, mirroredPeakY - 4f, x + barWidth - spacing, mirroredPeakY, barPaint)
            }
        }
    }

    private fun drawOrbit(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val baseRadius = min(w, h) * 0.2f
        
        canvas.save()
        canvas.translate(cx, cy)
        
        // Internal pulsating core with multiple colors
        for (j in 0 until 3) {
            barPaint.style = Paint.Style.FILL
            barPaint.color = layerColors[j]
            val pulseRadius = baseRadius * (0.4f + j * 0.2f) * (1f + beatPulse * 0.2f)
            val alpha = (100 * (1f - j * 0.3f) * intensity).toInt().coerceIn(0, 255)
            barPaint.alpha = alpha
            canvas.drawCircle(0f, 0f, pulseRadius, barPaint)
        }
        
        for (i in 0 until numBins) {
            val angle = (i.toFloat() / numBins) * 2 * PI.toFloat() + animationPhase * 0.15f
            val mag = smoothedMagnitudes[i]
            val reaction = mag * 6.0f * (intensity + 0.1f)
            
            barPaint.color = layerColors[i % layerColors.size]
            barPaint.strokeWidth = 4f
            barPaint.style = Paint.Style.STROKE
            
            val x1 = cos(angle) * baseRadius
            val y1 = sin(angle) * baseRadius
            val x2 = cos(angle) * (baseRadius + reaction)
            val y2 = sin(angle) * (baseRadius + reaction)
            
            canvas.drawLine(x1, y1, x2, y2, barPaint)
            
            // Floating "energy" nodes instead of fast orbits
            if (i % 8 == 0) {
                val driftAngle = angle + sin(animationPhase * 0.5f + i) * 0.2f
                val dist = baseRadius + reaction + 40f * (1f + beatPulse)
                val ex = cos(driftAngle) * dist
                val ey = sin(driftAngle) * dist
                barPaint.style = Paint.Style.FILL
                barPaint.alpha = (200 * intensity).toInt().coerceIn(0, 255)
                canvas.drawCircle(ex, ey, 4f + 8f * beatPulse, barPaint)
            }
        }
        
        // Main core ring
        barPaint.style = Paint.Style.STROKE
        barPaint.strokeWidth = 8f
        barPaint.color = Color.argb(255, 103, 58, 183)
        canvas.drawCircle(0f, 0f, baseRadius + 10f * beatPulse, barPaint)
        
        canvas.restore()
    }

    private fun drawFrequencyLayer(
        canvas: Canvas, 
        w: Float, 
        h: Float, 
        centerY: Float, 
        index: Int,
        binRange: IntRange
    ) {
        val path = Path()
        val segments = 60
        val segmentWidth = (w + 20f) / segments
        val phaseOffset = index * 2.2f
        val speedMult = 0.5f + index * 0.6f
        val reactiveFactor = intensity + beatPulse * 0.25f
        val heightScale = (1.0f - index * 0.2f) * (1.0f + beatPulse * 0.4f)
        
        wavePaint.color = layerColors[index % layerColors.size]
        wavePaint.style = Paint.Style.FILL

        path.moveTo(-10f, h + 10f)
        path.lineTo(-10f, centerY)
        for (i in 0..segments) {
            val x = i * segmentWidth - 10f
            val binNormalizedIdx = (i.toFloat() / segments * (binRange.last - binRange.first)).toInt()
            val binIdx = (binRange.first + binNormalizedIdx).coerceIn(0, numBins - 1)
            val binMag = smoothedMagnitudes[binIdx]
            val sine1 = sin(x * (0.006f + index * 0.004f) + animationPhase * speedMult + phaseOffset)
            val sine2 = cos(x * 0.015f - animationPhase * 0.4f)
            val baseWaveHeight = (15f + 250f * reactiveFactor) * heightScale
            val frequencyImpact = binMag * (3.5f + beatPulse * 2.0f) * reactiveFactor
            val y = centerY - (sine1 * baseWaveHeight) - (sine2 * 20f * reactiveFactor) - frequencyImpact
            path.lineTo(x, y)
        }
        path.lineTo(w + 10f, h + 10f)
        path.close()
        canvas.drawPath(path, wavePaint)
    }
}
