package com.example.soundvault.ui.nowplaying

import Views.VisualizerManager
import Views.VisualizerView
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.Music
import com.example.soundvault.databinding.FragmentNowPlayingBinding

class NowPlayingFragment : Fragment() {
    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!
    private lateinit var handler: Handler
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            val mainActivity = activity as? MainActivity
            if (_binding != null && isAdded && mainActivity != null) {
                mainActivity.musicService?.getCurrentPosition()?.let {
                    binding.seekBar.progress = it
                }
                handler.postDelayed(this, 1000)
            }
        }
    }

    // Visualizer components
    private lateinit var visualizerView: VisualizerView
    private lateinit var visualizerManager: VisualizerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        handler = Handler(Looper.getMainLooper())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize visualizer
        visualizerView = binding.visualizerView
        visualizerManager = VisualizerManager(visualizerView)

        val mainActivity = activity as? MainActivity

        // Load saved settings
        val sharedPrefs = requireContext().getSharedPreferences("SoundVaultPrefs", Context.MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("VisualizerTheme", VisualizerView.Theme.MOUNTAINS.name)
        visualizerView.setThemeByName(savedTheme ?: VisualizerView.Theme.MOUNTAINS.name)

        // Cycle through themes on click (if music is playing and feature is enabled)
        visualizerView.setOnClickListener {
            val isPlaying = mainActivity?.musicService?.isPlaying?.value ?: false
            val allowTap = sharedPrefs.getBoolean("AllowVisualizerTap", true)
            
            if (allowTap && isPlaying) {
                val newTheme = visualizerView.nextTheme()
                sharedPrefs.edit().putString("VisualizerTheme", newTheme).apply()
            }
        }

        binding.shuffle.isChecked = mainActivity?.musicService?.isShuffleEnabled ?: false

        // Set up shuffle callback
        binding.shuffle.addOnCheckedChangeListener { _, isChecked ->
            mainActivity?.musicService?.isShuffleEnabled = isChecked
            // Update library fragment if needed
            (activity as? MainActivity)?.libraryFragment?.updateShuffleState(isChecked)
        }

        mainActivity?.musicService?.currentMusic?.observe(viewLifecycleOwner) {
            updateUI(it)
            // Setup visualizer when music changes
            it?.let { music ->
                mainActivity.musicService?.let { service ->
                    visualizerManager.setupVisualizer(requireContext(), service.getAudioSessionId())
                }
            }
        }

        mainActivity?.musicService?.isPlaying?.observe(viewLifecycleOwner) {
            updatePlayPauseButton(it)
        }

        binding.playPause.setOnClickListener {
            (activity as? MainActivity)?.musicService?.let {
                if (it.isPlaying.value == true) {
                    it.pause()
                } else {
                    it.resume()
                }
            }
        }

        binding.stop.setOnClickListener {
            (activity as? MainActivity)?.musicService?.let {
                it.stop()
            }
        }

        binding.next.setOnClickListener {
            (activity as? MainActivity)?.musicService?.let {
                it.next()
            }
        }

        binding.previous.setOnClickListener {
            (activity as? MainActivity)?.musicService?.let {
                it.previous()
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    (activity as? MainActivity)?.musicService?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateSeekBarRunnable)
        
        // Refresh theme in case it was changed in settings
        val sharedPrefs = requireContext().getSharedPreferences("SoundVaultPrefs", Context.MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("VisualizerTheme", VisualizerView.Theme.MOUNTAINS.name)
        visualizerView.setThemeByName(savedTheme ?: VisualizerView.Theme.MOUNTAINS.name)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    private fun updateUI(music: Music?) {
        if (_binding == null) return
        if (music != null) {
            binding.title.text = music.title
            binding.artist.text = music.artist
            binding.seekBar.max = music.duration.toInt()
        } else {
            binding.title.text = ""
            binding.artist.text = ""
            binding.seekBar.progress = 0
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (_binding == null) return
        if (isPlaying) {
            binding.playPause.setImageResource(R.drawable.ic_pause)
        } else {
            binding.playPause.setImageResource(R.drawable.ic_play)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        visualizerManager.release()
        _binding = null
    }
}
