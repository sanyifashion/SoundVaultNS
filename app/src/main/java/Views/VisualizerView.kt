package Views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Theme {
        LIQUID, MOUNTAINS, RYTHM_BARS, HYBRID
    }

    private var currentTheme: Theme = Theme.MOUNTAINS

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    // Particle system: Now more localized and "jumping"
    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var color: Int,
        var life: Float, // 1.0 to 0.0
        val gravity: Float = 1200f
    )
    private val particles = mutableListOf<Particle>()
    private val random = Random(System.currentTimeMillis())

    // Falling peaks for Rythm Bars
    private var peakY = FloatArray(numBins)
    private var peakVelocity = FloatArray(numBins)
    private val gravityValue = 1500f // pixels/s^2

    private val layerColors = intArrayOf(
        Color.argb(180, 103, 58, 183),  // Deep Purple
        Color.argb(150, 33, 150, 243),  // Blue
        Color.argb(130, 0, 188, 212)    // Cyan
    )

    private val mountainColor = Color.argb(180, 103, 58, 183) // Purple
    private val barColor = Color.argb(200, 255, 64, 129) // Pink Accent

    fun setTheme(theme: Theme) {
        currentTheme = theme
        invalidate()
    }

    // Helper to toggle theme externally
    fun nextTheme() {
        currentTheme = when (currentTheme) {
            Theme.MOUNTAINS -> Theme.LIQUID
            Theme.LIQUID -> Theme.RYTHM_BARS
            Theme.RYTHM_BARS -> Theme.HYBRID
            Theme.HYBRID -> Theme.MOUNTAINS
        }
        invalidate()
    }

    fun updateVisualizer(waveform: ByteArray?, fft: ByteArray?) {
        if (waveform != null) {
            var sum = 0f
            for (i in waveform.indices) {
                val amplitude = (waveform[i].toInt() and 0xFF) - 128
                sum += (amplitude * amplitude).toFloat()
            }
            val rms = sqrt(sum / waveform.size)
            val targetIntensity = (rms / 55f).coerceIn(0f, 1.2f)
            
            // Beat detection: Trigger localized "jumps"
            if (targetIntensity > intensity + 0.12f) {
                beatPulse = 1.0f 
                // Spawn particles at random peaks when a beat occurs
                triggerLocalizedParticles(targetIntensity)
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

    private fun triggerLocalizedParticles(intensity: Float) {
        // Find a few "active" bins to spawn particles from
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h * 0.65f
        
        for (j in 0 until 5) {
            val binIdx = random.nextInt(numBins)
            val mag = smoothedMagnitudes[binIdx]
            if (mag > 5f) {
                val x = (binIdx.toFloat() / numBins) * w
                val barHeight = mag * 1.8f * (intensity + 0.2f)
                val spawnY = centerY - barHeight
                
                spawnParticlesAt(x, spawnY, intensity)
            }
        }
    }

    private fun spawnParticlesAt(x: Float, y: Float, intensity: Float) {
        val count = (5 * intensity).toInt().coerceAtLeast(2)
        for (i in 0 until count) {
            val color = layerColors[random.nextInt(layerColors.size)]
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (random.nextFloat() - 0.5f) * 150f, // More constrained horizontal
                    vy = -random.nextFloat() * 400f * intensity - 50f, // Upward "jump"
                    size = random.nextFloat() * 8f + 4f,
                    color = color,
                    life = 1.0f
                )
            )
        }
    }

    private fun updatePhysics(dt: Float) {
        // Particles physics: gravity + localized fade
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += p.gravity * dt 
            p.life -= dt * 1.2f // Fade out
            if (p.life <= 0) it.remove()
        }

        // Falling peaks physics
        val h = height.toFloat()
        val centerY = h * 0.65f
        for (i in 0 until numBins) {
            val barHeight = smoothedMagnitudes[i] * 1.8f * (intensity + 0.2f)
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
                if (particles.isEmpty()) return 
            }
            animationPhase += dt * 0.4f
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h * 0.65f

        // Draw based on the active theme
        when (currentTheme) {
            Theme.MOUNTAINS -> {
                drawMountains(canvas, w, h, centerY) // Background Purple Mountain
                // Draw light blue waves in front
                drawFrequencyLayer(canvas, w, h, centerY, 1, 8..24)
                drawFrequencyLayer(canvas, w, h, centerY, 2, 24..63)
            }
            Theme.LIQUID -> drawLiquid(canvas, w, h, centerY)
            Theme.RYTHM_BARS -> drawRythmBars(canvas, w, h, centerY)
            Theme.HYBRID -> {
                drawMountains(canvas, w, h, centerY) // Background silhouette
                drawLiquid(canvas, w, h, centerY)    // Mid-ground waves
                drawRythmBars(canvas, w, h, centerY, true) // Foreground peaks only
            }
        }

        drawParticles(canvas)
        invalidate()
    }

    private fun drawLiquid(canvas: Canvas, w: Float, h: Float, centerY: Float) {
        drawFrequencyLayer(canvas, w, h, centerY, 0, 0..8)
        drawFrequencyLayer(canvas, w, h, centerY, 1, 8..24)
        drawFrequencyLayer(canvas, w, h, centerY, 2, 24..63)
    }

    private fun drawMountains(canvas: Canvas, w: Float, h: Float, centerY: Float) {
        val path = Path()
        val barWidth = w / numBins
        mountainPaint.color = mountainColor
        
        path.moveTo(0f, h)
        path.lineTo(0f, centerY)
        
        for (i in 0 until numBins) {
            val x = i * barWidth
            val mag = smoothedMagnitudes[i]
            val barHeight = mag * 2.2f * (intensity + 0.1f)
            path.lineTo(x + barWidth/2, centerY - barHeight)
        }
        
        path.lineTo(w, centerY)
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, mountainPaint)
    }

    private fun drawRythmBars(canvas: Canvas, w: Float, h: Float, centerY: Float, peaksOnly: Boolean = false) {
        val barWidth = w / numBins
        val spacing = 2f
        
        for (i in 0 until numBins) {
            val x = i * barWidth
            val mag = smoothedMagnitudes[i]
            val barHeight = mag * 1.8f * (intensity + 0.1f)
            
            if (!peaksOnly) {
                barPaint.color = barColor
                barPaint.alpha = (180 * (intensity + 0.2f)).toInt().coerceIn(0, 255)
                canvas.drawRect(x, centerY - barHeight, x + barWidth - spacing, centerY, barPaint)
            }
            
            // Draw falling peak "mountain cap"
            barPaint.color = Color.rgb(255, 64, 129) // Bold pink
            barPaint.alpha = 255
            canvas.drawRect(x, peakY[i], x + barWidth - spacing, peakY[i] + 4f, barPaint)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = (p.life * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.size * p.life, particlePaint)
        }
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
        val segmentWidth = w / segments
        val phaseOffset = index * 2.2f
        val speedMult = 0.5f + index * 0.6f
        val reactiveFactor = intensity + beatPulse * 0.25f
        val heightScale = (1.0f - index * 0.2f) * (1.0f + beatPulse * 0.4f)

        wavePaint.color = layerColors[index % layerColors.size]
        path.moveTo(0f, h)
        path.lineTo(0f, centerY)

        for (i in 0..segments) {
            val x = i * segmentWidth
            val binNormalizedIdx = (i.toFloat() / segments * (binRange.last - binRange.first)).toInt()
            val binIdx = (binRange.first + binNormalizedIdx).coerceIn(0, numBins - 1)
            val binMag = smoothedMagnitudes[binIdx]
            val sine1 = sin(x * (0.006f + index * 0.004f) + animationPhase * speedMult + phaseOffset)
            val sine2 = cos(x * 0.015f - animationPhase * 0.4f)
            val baseWaveHeight = (15f + 150f * reactiveFactor) * heightScale
            val frequencyImpact = binMag * (1.8f + beatPulse * 1.5f) * reactiveFactor
            val y = centerY - (sine1 * baseWaveHeight) - (sine2 * 20f * reactiveFactor) - frequencyImpact
            path.lineTo(x, y)
        }

        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, wavePaint)
    }
}
