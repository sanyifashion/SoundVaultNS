package com.example.soundvault.ui.playlists

import java.io.File

data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    val isMusicFile: Boolean = false,
    var isSelected: Boolean = false
)
