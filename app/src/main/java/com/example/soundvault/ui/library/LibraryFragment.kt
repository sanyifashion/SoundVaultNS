package com.example.soundvault.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.Music
import com.example.soundvault.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    private lateinit var binding: FragmentLibraryBinding
    private lateinit var viewModel: LibraryViewModel
    private lateinit var musicAdapter: MusicAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                    13
                )
            } else {
                initializeView()
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    13
                )
            } else {
                initializeView()
            }
        }
    }

    private fun initializeView() {
        musicAdapter = MusicAdapter(emptyList()) { position ->
            (activity as MainActivity).musicService?.play(position)
        }
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = musicAdapter

        val viewModelFactory = LibraryViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, viewModelFactory)[LibraryViewModel::class.java]

        viewModel.music.observe(viewLifecycleOwner) {
            musicAdapter.updateMusic(it)
            (activity as MainActivity).musicService?.setMusicList(it as ArrayList<Music>)
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 13) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeView()
            } else {
                // Handle permission denied
            }
        }
    }
}