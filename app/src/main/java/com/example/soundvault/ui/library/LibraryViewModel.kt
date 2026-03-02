package com.example.soundvault.ui.library

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundvault.data.Music
import com.example.soundvault.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _music = MutableLiveData<List<Music>>()
    val music: LiveData<List<Music>> = _music

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        refreshMusic()
    }

    fun refreshMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            val musicList = withContext(Dispatchers.IO) {
                repository.getAllAudio()
            }
            _music.value = musicList
            _isLoading.value = false
        }
    }
}

class LibraryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(MusicRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}