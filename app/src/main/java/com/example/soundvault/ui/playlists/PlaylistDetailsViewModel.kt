package com.example.soundvault.ui.playlists

import android.app.Application
import androidx.lifecycle.*
import com.example.soundvault.data.*
import kotlinx.coroutines.launch

class PlaylistDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistDao: PlaylistDao = AppDatabase.getDatabase(application).playlistDao()
    private val musicRepository = MusicRepository(application)

    fun getSongsInPlaylist(playlistId: Long): LiveData<List<Music>> {
        return playlistDao.getSongsForPlaylist(playlistId).asLiveData().map { songIds ->
            val allMusic = musicRepository.getAllAudio()
            allMusic.filter { it.id in songIds }
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
