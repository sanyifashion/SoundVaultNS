package Views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val path = Path()
    private var data = FloatArray(1024)
    private var maxAmplitude = 0f

    fun updateVisualizer(waveform: ByteArray) {
        maxAmplitude = 0f
        data.fill(0f)

        // Convert byte array to float array and find max amplitude
        for (i in waveform.indices) {
            val value = waveform[i] / 128f
            data[i] = value
            if (Math.abs(value) > maxAmplitude) {
                maxAmplitude = Math.abs(value)
            }
        }

        // Normalize data
        if (maxAmplitude > 0) {
            for (i in data.indices) {
                data[i] = data[i] / maxAmplitude * 0.8f // Scale down a bit
            }
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawColor(Color.BLACK)

        // Calculate width per point
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2
        val pointWidth = width / data.size

        path.rewind()

        // Draw waveform
        for (i in data.indices) {
            val x = i * pointWidth
            val y = centerY + data[i] * height / 2

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, wavePaint)
    }
}