package com.example.soundvault.data

import android.net.Uri

data class Music(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val artUri: Uri?,
    val contentUri: Uri
)