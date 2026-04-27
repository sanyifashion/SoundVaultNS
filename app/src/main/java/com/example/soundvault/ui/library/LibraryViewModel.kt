package com.example.soundvault.ui.library

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundvault.data.AppDatabase
import com.example.soundvault.data.ExcludedSong
import com.example.soundvault.data.Music
import com.example.soundvault.data.MusicRepository
import com.example.soundvault.data.PlaylistSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Album(val name: String, val artist: String, val songs: List<Music>)
data class Artist(val name: String, val songs: List<Music>)

class LibraryViewModel(private val repository: MusicRepository, private val context: Context) : ViewModel() {

    enum class LibraryTab { SONGS, ALBUMS, ARTISTS }
    
    private val _currentTab = MutableLiveData(LibraryTab.SONGS)
    val currentTab: LiveData<LibraryTab> = _currentTab

    private val _music = MutableLiveData<List<Music>>()
    val music: LiveData<List<Music>> = _music

    private val _albums = MutableLiveData<List<Album>>()
    val albums: LiveData<List<Album>> = _albums

    private val _artists = MutableLiveData<List<Artist>>()
    val artists: LiveData<List<Artist>> = _artists

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val db = AppDatabase.getDatabase(context)

    init {
        refreshMusic()
    }

    fun setTab(tab: LibraryTab) {
        _currentTab.value = tab
    }

    fun refreshMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            val musicList = withContext(Dispatchers.IO) {
                val allSongs = repository.getAllAudio()
                val excludedIds = db.excludedSongDao().getAllExcludedIds()
                allSongs.filter { it.id !in excludedIds }
            }
            _music.postValue(musicList)
            
            // Group by Album
            val albumsList = musicList.groupBy { it.album }.map { (name, songs) ->
                Album(name, songs.firstOrNull()?.artist ?: "Unknown", songs)
            }.sortedBy { it.name }
            _albums.postValue(albumsList)

            // Group by Artist
            val artistsList = musicList.groupBy { it.artist }.map { (name, songs) ->
                Artist(name, songs)
            }.sortedBy { it.name }
            _artists.postValue(artistsList)

            _isLoading.value = false
        }
    }

    fun addSongsToPlaylist(songs: List<Music>, playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                db.playlistDao().addSongToPlaylist(PlaylistSong(playlistId, song.id))
            }
        }
    }

    fun excludeSongs(songs: List<Music>) {
        viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                db.excludedSongDao().excludeSong(ExcludedSong(song.id))
            }
            refreshMusic()
        }
    }
}

class LibraryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(MusicRepository(context), context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
