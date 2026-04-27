package com.example.soundvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "excluded_songs")
data class ExcludedSong(@PrimaryKey val songId: Long)

@Database(entities = [Playlist::class, PlaylistSong::class, EqualizerPreset::class, SongPreset::class, ExcludedSong::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun equalizerDao(): EqualizerDao
    
    @androidx.room.Dao
    interface ExcludedSongDao {
        @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
        suspend fun excludeSong(excludedSong: ExcludedSong)
        
        @androidx.room.Query("SELECT songId FROM excluded_songs")
        suspend fun getAllExcludedIds(): List<Long>
    }
    
    abstract fun excludedSongDao(): ExcludedSongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "soundvault_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
