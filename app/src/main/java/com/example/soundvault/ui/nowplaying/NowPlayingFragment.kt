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

    private lateinit var binding: FragmentNowPlayingBinding
    private lateinit var handler: Handler

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        handler = Handler(Looper.getMainLooper())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).musicService?.currentMusic?.observe(viewLifecycleOwner) {
            updateUI(it)
        }

        (activity as MainActivity).musicService?.isPlaying?.observe(viewLifecycleOwner) {
            updatePlayPauseButton(it)
        }

        binding.playPause.setOnClickListener {
            (activity as MainActivity).musicService?.let {
                if (it.isPlaying.value == true) {
                    it.pause()
                } else {
                    it.resume()
                }
            }
        }

        binding.next.setOnClickListener {
            (activity as MainActivity).musicService?.next()
        }

        binding.previous.setOnClickListener {
            (activity as MainActivity).musicService?.previous()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    (activity as MainActivity).musicService?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        updateSeekBar()
    }

    private fun updateUI(music: Music?) {
        music?.let {
            binding.title.text = it.title
            binding.artist.text = it.artist
            Glide.with(this)
                .load(it.artUri)
                .placeholder(R.mipmap.ic_launcher)
                .into(binding.albumArt)
            binding.seekBar.max = it.duration.toInt()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            binding.playPause.setImageResource(R.drawable.ic_pause)
        } else {
            binding.playPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateSeekBar() {
        (activity as MainActivity).musicService?.getCurrentPosition()?.let {
            binding.seekBar.progress = it
        }
        handler.postDelayed({ updateSeekBar() }, 1000)
    }
}