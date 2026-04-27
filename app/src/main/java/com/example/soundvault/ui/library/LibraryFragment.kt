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
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.AppDatabase
import com.example.soundvault.databinding.FragmentLibraryBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: LibraryViewModel
    private lateinit var musicAdapter: MusicAdapter
    private var isRefreshing = false

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
        
        val viewModelFactory = LibraryViewModelFactory(requireContext())
        viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[LibraryViewModel::class.java]

        setupTabs()
        setupMenu()
        requestPermissions()
        initializeView()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val newTab = when (tab?.position) {
                    0 -> LibraryViewModel.LibraryTab.SONGS
                    1 -> LibraryViewModel.LibraryTab.ALBUMS
                    2 -> LibraryViewModel.LibraryTab.ARTISTS
                    else -> LibraryViewModel.LibraryTab.SONGS
                }
                if (viewModel.currentTab.value != newTab) {
                    viewModel.setTab(newTab)
                }
                // Exit multi-select when switching tabs for simplicity
                if (::musicAdapter.isInitialized && musicAdapter.isMultiSelectMode) {
                    exitMultiSelectMode()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        viewModel.currentTab.observe(viewLifecycleOwner) { tab ->
            val position = when (tab) {
                LibraryViewModel.LibraryTab.SONGS -> 0
                LibraryViewModel.LibraryTab.ALBUMS -> 1
                LibraryViewModel.LibraryTab.ARTISTS -> 2
                else -> 0
            }
            if (binding.tabLayout.selectedTabPosition != position) {
                binding.tabLayout.getTabAt(position)?.select()
            }
            updateListForTab(tab)
        }
    }

    private fun updateListForTab(tab: LibraryViewModel.LibraryTab, query: String? = null) {
        when (tab) {
            LibraryViewModel.LibraryTab.SONGS -> showSongs(query)
            LibraryViewModel.LibraryTab.ALBUMS -> showAlbums(query)
            LibraryViewModel.LibraryTab.ARTISTS -> showArtists(query)
        }
    }

    private fun showSongs(query: String? = null) {
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = musicAdapter
        val songs = viewModel.music.value ?: emptyList()
        val filtered = if (query.isNullOrBlank()) songs else {
            songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
        }
        musicAdapter.updateMusic(filtered)
    }

    private fun showAlbums(query: String? = null) {
        binding.list.layoutManager = GridLayoutManager(context, 2)
        val albums = viewModel.albums.value ?: emptyList()
        val filtered = if (query.isNullOrBlank()) albums else {
            albums.filter { it.name.contains(query, true) || it.artist.contains(query, true) }
        }
        val adapter = GenericLibraryAdapter(
            titles = filtered.map { it.name },
            subtitles = filtered.map { it.artist },
            imageUris = filtered.map { it.songs.firstOrNull()?.artUri },
            layoutId = R.layout.item_album_grid,
            onItemClicked = { pos: Int ->
                val album = filtered[pos]
                openCollectionDetail(album.name, "album")
            }
        )
        binding.list.adapter = adapter
    }

    private fun showArtists(query: String? = null) {
        binding.list.layoutManager = LinearLayoutManager(context)
        val artists = viewModel.artists.value ?: emptyList()
        val filtered = if (query.isNullOrBlank()) artists else {
            artists.filter { it.name.contains(query, true) }
        }
        val adapter = GenericLibraryAdapter(
            titles = filtered.map { it.name },
            subtitles = filtered.map { "${it.songs.size} Songs" },
            imageUris = artists.map { it.songs.firstOrNull()?.artUri },
            onItemClicked = { pos: Int ->
                val artist = filtered[pos]
                openCollectionDetail(artist.name, "artist")
            }
        )
        binding.list.adapter = adapter
    }

    private fun openCollectionDetail(name: String, type: String) {
        val bundle = Bundle().apply {
            putString("title", name)
            putString("type", type)
        }
        findNavController().navigate(R.id.action_nav_library_to_filteredLibraryFragment, bundle)
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.library_menu, menu)
                
                val isMultiSelect = ::musicAdapter.isInitialized && musicAdapter.isMultiSelectMode
                
                menu.findItem(R.id.action_search).isVisible = !isMultiSelect
                menu.findItem(R.id.action_refresh).isVisible = !isMultiSelect
                
                // Add multi-select items dynamically or from a separate menu
                if (isMultiSelect) {
                    menu.add(Menu.NONE, R.id.action_add_to_playlist, Menu.NONE, "Add to Playlist")
                        .setIcon(android.R.drawable.ic_menu_add)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    
                    menu.add(Menu.NONE, R.id.action_exclude, Menu.NONE, "Remove from Library")
                        .setIcon(android.R.drawable.ic_menu_delete)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                        
                    menu.add(Menu.NONE, R.id.action_cancel, Menu.NONE, "Cancel")
                        .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                } else {
                    val searchItem = menu.findItem(R.id.action_search)
                    val searchView = searchItem.actionView as SearchView
                    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean = false
                        override fun onQueryTextChange(newText: String?): Boolean {
                            viewModel.currentTab.value?.let { updateListForTab(it, newText) }
                            return true
                        }
                    })
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        viewModel.refreshMusic()
                        true
                    }
                    R.id.action_add_to_playlist -> {
                        showPlaylistSelectionDialog()
                        true
                    }
                    R.id.action_exclude -> {
                        showExcludeConfirmationDialog()
                        true
                    }
                    R.id.action_cancel -> {
                        exitMultiSelectMode()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showPlaylistSelectionDialog() {
        val selectedSongs = musicAdapter.selectedItems.toList()
        if (selectedSongs.isEmpty()) return

        CoroutineScope(Dispatchers.Main).launch {
            val db = AppDatabase.getDatabase(requireContext())
            val playlists = db.playlistDao().getAllPlaylists().first()
            val playlistNames = playlists.map { it.name }

            if (playlists.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("No Playlists")
                    .setMessage("You haven't created any playlists yet.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, playlistNames)
            AlertDialog.Builder(requireContext())
                .setTitle("Add to Playlist")
                .setAdapter(adapter) { _, which ->
                    val playlistId = playlists[which].id
                    viewModel.addSongsToPlaylist(selectedSongs, playlistId)
                    exitMultiSelectMode()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showExcludeConfirmationDialog() {
        val selectedSongs = musicAdapter.selectedItems.toList()
        if (selectedSongs.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("Remove from Library")
            .setMessage("This will hide ${selectedSongs.size} songs from the library. The files will NOT be deleted from storage. Proceed?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.excludeSongs(selectedSongs)
                exitMultiSelectMode()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exitMultiSelectMode() {
        musicAdapter.isMultiSelectMode = false
        requireActivity().invalidateOptionsMenu()
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
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), 13)
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
        musicAdapter = MusicAdapter(emptyList()) { position: Int ->
            val musicList = musicAdapter.musicList
            if (musicList.isNotEmpty()) {
                val mainActivity = activity as? MainActivity
                mainActivity?.musicService?.let { service ->
                    service.setMusicList(ArrayList(musicList))
                    service.play(position)
                }
            }
        }
        
        musicAdapter.onLongClick = {
            requireActivity().invalidateOptionsMenu()
        }
        
        viewModel.currentTab.value?.let { updateListForTab(it) }

        viewModel.music.observe(viewLifecycleOwner) {
            viewModel.currentTab.value?.let { updateListForTab(it) }
            (activity as? MainActivity)?.musicService?.setMusicList(ArrayList(it))
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            requireActivity().invalidateOptionsMenu()
        }

        (activity as? MainActivity)?.musicService?.currentMusic?.observe(viewLifecycleOwner) { currentMusic ->
            if (viewModel.currentTab.value == LibraryViewModel.LibraryTab.SONGS) {
                val musicList = musicAdapter.musicList
                if (musicList.isNotEmpty() && currentMusic != null) {
                    val index = musicList.indexOfFirst { it.id == currentMusic.id }
                    musicAdapter.setCurrentPlayingPosition(index)
                } else {
                    musicAdapter.setCurrentPlayingPosition(-1)
                }
            }
        }
        
        val currentShuffleState = (activity as? MainActivity)?.musicService?.isShuffleEnabled ?: false
        musicAdapter.updateShuffleState(currentShuffleState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
