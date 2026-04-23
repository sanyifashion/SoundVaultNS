package com.example.soundvault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EqualizerDao {
    @Query("SELECT * FROM equalizer_presets")
    fun getAllPresets(): Flow<List<EqualizerPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqualizerPreset): Long

    @Delete
    suspend fun deletePreset(preset: EqualizerPreset)

    @Query("SELECT * FROM equalizer_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): EqualizerPreset?

    @Query("SELECT * FROM equalizer_presets WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): EqualizerPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongPreset(songPreset: SongPreset)

    @Query("DELETE FROM song_presets WHERE songId = :songId")
    suspend fun deleteSongPreset(songId: Long)

    @Query("SELECT * FROM equalizer_presets WHERE id = (SELECT presetId FROM song_presets WHERE songId = :songId)")
    suspend fun getPresetForSong(songId: Long): EqualizerPreset?
}
