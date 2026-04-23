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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.AppDatabase
import com.example.soundvault.data.EqualizerPreset
import com.example.soundvault.data.Music
import com.example.soundvault.databinding.FragmentNowPlayingBinding
import com.example.soundvault.services.MusicService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NowPlayingFragment : Fragment() {
    private var _binding: FragmentNowPlayingBinding? = null
    // Safely access binding only when view exists
    private val binding get() = _binding!!
    
    private lateinit var handler: Handler
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            val mainActivity = activity as? MainActivity
            val b = _binding
            if (b != null && isAdded && mainActivity != null) {
                mainActivity.musicService?.getCurrentPosition()?.let {
                    b.seekBar.progress = it
                }
                handler.postDelayed(this, 1000)
            }
        }
    }

    private lateinit var visualizerView: VisualizerView
    private lateinit var visualizerManager: VisualizerManager
    private var presetsList = listOf<EqualizerPreset>()
    private var isFirstSelection = true

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

        visualizerView = binding.visualizerView
        visualizerManager = VisualizerManager(visualizerView)

        val mainActivity = activity as? MainActivity

        val sharedPrefs = requireContext().getSharedPreferences("SoundVaultPrefs", Context.MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("VisualizerTheme", VisualizerView.Theme.MOUNTAINS.name)
        visualizerView.setThemeByName(savedTheme ?: VisualizerView.Theme.MOUNTAINS.name)

        visualizerView.setOnClickListener {
            val isPlaying = mainActivity?.musicService?.isPlaying?.value ?: false
            val allowTap = sharedPrefs.getBoolean("AllowVisualizerTap", true)
            
            if (allowTap && isPlaying) {
                val newTheme = visualizerView.nextTheme()
                sharedPrefs.edit().putString("VisualizerTheme", newTheme).apply()
            }
        }

        binding.shuffle.isChecked = mainActivity?.musicService?.isShuffleEnabled ?: false
        binding.shuffle.addOnCheckedChangeListener { _, isChecked ->
            mainActivity?.musicService?.isShuffleEnabled = isChecked
            (activity as? MainActivity)?.libraryFragment?.updateShuffleState(isChecked)
        }

        setupEqSpinner()

        mainActivity?.musicService?.currentMusic?.observe(viewLifecycleOwner) { music ->
            updateUI(music)
            if (music != null) {
                mainActivity.musicService?.let { service ->
                    visualizerManager.setupVisualizer(requireContext(), service.getAudioSessionId())
                    updateSpinnerSelectionForSong(music.id)
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
            (activity as? MainActivity)?.musicService?.stop()
        }

        binding.next.setOnClickListener {
            (activity as? MainActivity)?.musicService?.next()
        }

        binding.previous.setOnClickListener {
            (activity as? MainActivity)?.musicService?.previous()
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

    private fun setupEqSpinner() {
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            db.equalizerDao().getAllPresets().collectLatest { presets ->
                val b = _binding ?: return@collectLatest
                presetsList = listOf(MusicService.DEFAULT_PRESET) + presets
                val presetNames = presetsList.map { it.name }
                
                val adapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, presetNames)
                adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
                b.nowPlayingEqSpinner.adapter = adapter
                
                (activity as? MainActivity)?.musicService?.currentMusic?.value?.let { music ->
                    updateSpinnerSelectionForSong(music.id)
                }
            }
        }

        binding.nowPlayingEqSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isFirstSelection) {
                    isFirstSelection = false
                    return
                }
                if (position in presetsList.indices) {
                    val selectedPreset = presetsList[position]
                    (activity as? MainActivity)?.musicService?.applyPreset(selectedPreset)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSpinnerSelectionForSong(songId: Long) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val preset = db.equalizerDao().getPresetForSong(songId)
            val index = if (preset != null) {
                presetsList.indexOfFirst { it.id == preset.id }.coerceAtLeast(0)
            } else {
                0 // Default
            }
            
            val b = _binding ?: return@launch
            if (b.nowPlayingEqSpinner.adapter != null && 
                index in 0 until b.nowPlayingEqSpinner.count &&
                b.nowPlayingEqSpinner.selectedItemPosition != index) {
                isFirstSelection = true
                b.nowPlayingEqSpinner.setSelection(index)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateSeekBarRunnable)
        val sharedPrefs = requireContext().getSharedPreferences("SoundVaultPrefs", Context.MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("VisualizerTheme", VisualizerView.Theme.MOUNTAINS.name)
        visualizerView.setThemeByName(savedTheme ?: VisualizerView.Theme.MOUNTAINS.name)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    private fun updateUI(music: Music?) {
        val b = _binding ?: return
        b.title.text = music?.title ?: ""
        b.artist.text = music?.artist ?: ""
        if (music == null) b.seekBar.progress = 0
        b.seekBar.max = music?.duration?.toInt() ?: 0
        b.eqContainer.visibility = if (music != null) View.VISIBLE else View.GONE
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val b = _binding ?: return
        if (isPlaying) {
            b.playPause.setImageResource(R.drawable.ic_pause)
        } else {
            b.playPause.setImageResource(R.drawable.ic_play)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        visualizerManager.release()
        _binding = null
    }
}
