package com.example.soundvault.ui.playlists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.soundvault.data.AppDatabase
import com.example.soundvault.data.Playlist
import com.example.soundvault.data.PlaylistDao
import kotlinx.coroutines.launch

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistDao: PlaylistDao = AppDatabase.getDatabase(application).playlistDao()
    val allPlaylists: LiveData<List<Playlist>> = playlistDao.getAllPlaylists().asLiveData()

    fun insert(playlist: Playlist) = viewModelScope.launch {
        playlistDao.insertPlaylist(playlist)
    }

    fun delete(playlist: Playlist) = viewModelScope.launch {
        playlistDao.deletePlaylist(playlist)
    }
}
