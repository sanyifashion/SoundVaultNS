package com.example.soundvault.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    private lateinit var binding: FragmentLibraryBinding
    private lateinit var viewModel: LibraryViewModel
    private lateinit var musicAdapter: MusicAdapter
    private var isRefreshing = false

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
        setupMenu()
        requestPermissions()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.library_menu, menu)
                val refreshItem = menu.findItem(R.id.action_refresh)
                refreshItem?.isEnabled = !isRefreshing
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        viewModel.refreshMusic()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val permissionsToRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsToRequest.toTypedArray(),
                13
            )
        } else {
            initializeView()
        }
    }

    fun updateShuffleState(shuffleEnabled: Boolean) {
        if (::musicAdapter.isInitialized) {
            musicAdapter.updateShuffleState(shuffleEnabled)
        }
    }

    private fun initializeView() {
        val viewModelFactory = LibraryViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, viewModelFactory)[LibraryViewModel::class.java]

        musicAdapter = MusicAdapter(emptyList()) { position: Int ->
            val mainActivity = activity as? MainActivity
            val musicList = viewModel.music.value
            if (mainActivity != null && musicList != null) {
                mainActivity.musicService?.let { service ->
                    service.setMusicList(ArrayList(musicList))
                    service.play(position)
                }
            }
        }
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = musicAdapter

        viewModel.music.observe(viewLifecycleOwner) {
            musicAdapter.updateMusic(it)
            (activity as? MainActivity)?.musicService?.setMusicList(ArrayList(it))
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            requireActivity().invalidateOptionsMenu()
        }

        (activity as? MainActivity)?.musicService?.currentMusic?.observe(viewLifecycleOwner) { currentMusic ->
            val musicList = viewModel.music.value
            if (musicList != null && currentMusic != null) {
                val index = musicList.indexOfFirst { it.id == currentMusic.id }
                musicAdapter.setCurrentPlayingPosition(index)
            } else {
                musicAdapter.setCurrentPlayingPosition(-1)
            }
        }
        
        val currentShuffleState = (activity as? MainActivity)?.musicService?.isShuffleEnabled ?: false
        musicAdapter.updateShuffleState(currentShuffleState)
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 13) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeView()
            } else {
                // If at least storage/audio permission is granted, we can still show the library
                // even if RECORD_AUDIO for visualizer is denied.
                val storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                } else {
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
                
                if (storagePermissionGranted) {
                    initializeView()
                } else {
                    // Handle permission denied
                }
            }
        }
    }
}
