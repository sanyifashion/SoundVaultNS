package com.example.soundvault.ui.playlists

import android.app.Application
import androidx.lifecycle.*
import com.example.soundvault.data.*
import kotlinx.coroutines.launch

class PlaylistDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistDao: PlaylistDao = AppDatabase.getDatabase(application).playlistDao()
    private val musicRepository = MusicRepository(application)
    
    private val _playlistId = MutableLiveData<Long>()
    
    val songsInPlaylist: LiveData<List<Music>> = _playlistId.switchMap { id ->
        playlistDao.getSongsForPlaylist(id).asLiveData().map { songIds ->
            val allMusic = musicRepository.getAllAudio()
            allMusic.filter { it.id in songIds }
        }
    }

    fun setPlaylistId(id: Long) {
        if (_playlistId.value != id) {
            _playlistId.value = id
        }
    }

    fun getAllSongs(): List<Music> {
        return musicRepository.getAllAudio()
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch {
        playlistDao.addSongToPlaylist(PlaylistSong(playlistId, songId))
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
}
