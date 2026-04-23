package com.example.soundvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equalizer_presets")
data class EqualizerPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bass: Int,   // Represented as gain in millibels or simple 0-100 scale
    val mid: Int,
    val treble: Int
)

@Entity(tableName = "song_presets")
data class SongPreset(
    @PrimaryKey val songId: Long,
    val presetId: Long
)
