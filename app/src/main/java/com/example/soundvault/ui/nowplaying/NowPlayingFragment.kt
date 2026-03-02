package com.example.soundvault.ui.nowplaying

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
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

        val mainActivity = activity as? MainActivity
        mainActivity?.musicService?.currentMusic?.observe(viewLifecycleOwner) {
            updateUI(it)
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

    override fun onResume() {
        super.onResume()
        handler.post(updateSeekBarRunnable)
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
            Glide.with(this)
                .load(music.artUri)
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .into(binding.albumArt)
            binding.seekBar.max = music.duration.toInt()
        } else {
            binding.title.text = ""
            binding.artist.text = ""
            binding.albumArt.setImageResource(R.mipmap.ic_launcher)
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
        _binding = null
    }
}