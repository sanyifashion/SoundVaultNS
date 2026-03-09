package com.example.soundvault.ui.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.databinding.FragmentPlaylistDetailsBinding
import com.example.soundvault.ui.library.MusicAdapter

class PlaylistDetailsFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PlaylistDetailsViewModel
    private lateinit var adapter: MusicAdapter
    private var playlistId: Long = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlistId = arguments?.getLong("playlistId") ?: -1
        
        viewModel = ViewModelProvider(this)[PlaylistDetailsViewModel::class.java]
        viewModel.setPlaylistId(playlistId)

        adapter = MusicAdapter(emptyList()) { position ->
            val mainActivity = activity as? MainActivity
            val songs = adapter.musicList
            if (mainActivity != null && songs.isNotEmpty()) {
                val service = mainActivity.musicService
                if (service != null) {
                    service.setMusicList(ArrayList(songs))
                    service.play(position)
                }
            }
        }

        binding.playlistSongsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.playlistSongsRecyclerView.adapter = adapter

        viewModel.songsInPlaylist.observe(viewLifecycleOwner) { songs ->
            adapter.updateMusic(songs)
        }

        // Add observer for current playing song to highlight it in the playlist too
        (activity as? MainActivity)?.musicService?.currentMusic?.observe(viewLifecycleOwner) { currentMusic ->
            val songs = adapter.musicList
            if (currentMusic != null) {
                val index = songs.indexOfFirst { it.id == currentMusic.id }
                adapter.setCurrentPlayingPosition(index)
            } else {
                adapter.setCurrentPlayingPosition(-1)
            }
        }

        binding.addSongsFab.setOnClickListener {
            val bundle = Bundle().apply {
                putLong("playlistId", playlistId)
            }
            findNavController().navigate(R.id.action_playlistDetailsFragment_to_fileBrowserFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
