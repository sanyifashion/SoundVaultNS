package com.example.soundvault.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soundvault.MainActivity
import com.example.soundvault.databinding.FragmentLibraryBinding

class FilteredLibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LibraryViewModel
    private lateinit var musicAdapter: MusicAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide TabLayout as we are in a filtered view
        binding.tabLayout.visibility = View.GONE

        val title = arguments?.getString("title") ?: "Library"
        val type = arguments?.getString("type") ?: ""
        
        val viewModelFactory = LibraryViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, viewModelFactory)[LibraryViewModel::class.java]

        musicAdapter = MusicAdapter(emptyList()) { position ->
            val songs = musicAdapter.musicList
            val mainActivity = activity as? MainActivity
            mainActivity?.musicService?.let { service ->
                service.setMusicList(ArrayList(songs))
                service.play(position)
            }
        }

        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = musicAdapter

        viewModel.music.observe(viewLifecycleOwner) { allMusic ->
            val filteredList = when (type) {
                "album" -> allMusic.filter { it.album == title }
                "artist" -> allMusic.filter { it.artist == title }
                else -> allMusic
            }
            musicAdapter.updateMusic(filteredList)
        }

        (activity as? MainActivity)?.musicService?.currentMusic?.observe(viewLifecycleOwner) { currentMusic ->
            val songs = musicAdapter.musicList
            if (currentMusic != null) {
                val index = songs.indexOfFirst { it.id == currentMusic.id }
                musicAdapter.setCurrentPlayingPosition(index)
            } else {
                musicAdapter.setCurrentPlayingPosition(-1)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
