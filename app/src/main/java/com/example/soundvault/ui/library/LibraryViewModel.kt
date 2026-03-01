package com.example.soundvault.ui.library

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.soundvault.data.Music
import com.example.soundvault.data.MusicRepository

class LibraryViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _music = MutableLiveData<List<Music>>().apply {
        value = repository.getAllAudio()
    }
    val music: LiveData<List<Music>> = _music
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