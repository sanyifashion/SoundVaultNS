package com.example.soundvault.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

class MusicRepository(private val context: Context) {

    fun getAllAudio(): ArrayList<Music> {
        val audioList = ArrayList<Music>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val albumArtMap = getAlbumsWithArt()

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown"
                    val artist = it.getString(artistColumn) ?: "Unknown"
                    val album = it.getString(albumColumn) ?: "Unknown"
                    val duration = it.getLong(durationColumn)
                    val path = it.getString(dataColumn)
                    val albumId = it.getLong(albumIdColumn)
                    
                    // Only provide artUri if we have reason to believe it exists
                    val artUri = if (albumArtMap.contains(albumId)) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        )
                    } else {
                        null
                    }
                    
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    if (path != null && File(path).exists()) {
                        audioList.add(Music(id, title, artist, album, duration, path, artUri, contentUri))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error querying MediaStore", e)
        }
        return audioList
    }

    /**
     * Helper to find which albums actually have art to avoid Glide log spam for missing art.
     */
    private fun getAlbumsWithArt(): Set<Long> {
        val albumsWithArt = mutableSetOf<Long>()
        try {
            // We query the Albums table. Even if ALBUM_ART column is null on newer Androids,
            // we can still use this as a hint or just rely on the fact that if it's in the albums table,
            // we might have art. However, to be sure and avoid the specific warning, 
            // we really want to know if there's a file.
            val projection = arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART)
            context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Albums._ID)
                val artCol = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val artPath = if (artCol != -1) cursor.getString(artCol) else null
                    if (!artPath.isNullOrEmpty() && File(artPath).exists()) {
                        albumsWithArt.add(id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "Could not query album art", e)
        }
        return albumsWithArt
    }
}