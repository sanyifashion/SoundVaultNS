package com.example.soundvault.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
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
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val title = cursor.getString(1)
                val artist = cursor.getString(2)
                val album = cursor.getString(3)
                val duration = cursor.getLong(4)
                val path = cursor.getString(5)
                val albumId = cursor.getLong(6)
                val artUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    albumId
                )

                val music = Music(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    path = path,
                    artUri = artUri
                )
                if (File(music.path).exists()) {
                    audioList.add(music)
                }
            }
            cursor.close()
        }
        return audioList
    }
}