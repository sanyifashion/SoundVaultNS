package Views

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class VisualizerManager(private val visualizerView: VisualizerView) {
    private var visualizer: Visualizer? = null

    fun setupVisualizer(context: Context, audioSessionId: Int) {
        // Release existing visualizer if it exists
        release()

        if (audioSessionId != 0) {
            // Check for RECORD_AUDIO permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w("VisualizerManager", "RECORD_AUDIO permission not granted. Cannot initialize Visualizer.")
                return
            }

            try {
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int
                        ) {
                            visualizerView.updateVisualizer(waveform)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int
                        ) {
                            // You could use FFT data for frequency-based visualization
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                    
                    enabled = true
                }
            } catch (e: Exception) {
                Log.e("VisualizerManager", "Error initializing Visualizer", e)
                visualizer = null
            }
        }
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.e("VisualizerManager", "Error releasing Visualizer", e)
        }
        visualizer = null
    }
}
