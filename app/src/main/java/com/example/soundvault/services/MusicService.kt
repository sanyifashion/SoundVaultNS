package com.example.soundvault.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.Music

class MusicService : Service(), MediaPlayer.OnCompletionListener {

    private var mediaPlayer: MediaPlayer? = null
    private var musicList: ArrayList<Music> = ArrayList()
    private var currentPosition: Int = -1

    val currentMusic = MutableLiveData<Music?>()
    val isPlaying = MutableLiveData<Boolean>()

    private val binder = MusicServiceBinder()

    inner class MusicServiceBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let {
            when (it) {
                ACTION_PLAY -> resume()
                ACTION_PAUSE -> pause()
                ACTION_NEXT -> next()
                ACTION_PREVIOUS -> previous()
            }
        }
        return START_STICKY
    }

    fun play(position: Int) {
        if (position >= 0 && position < musicList.size) {
            currentPosition = position
            currentMusic.postValue(musicList[currentPosition])
            mediaPlayer?.reset()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(musicList[currentPosition].path)
                prepare()
                start()
                setOnCompletionListener(this@MusicService)
            }
            isPlaying.postValue(true)
            showNotification()
        }
    }

    private fun showNotification() {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying.value == true) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseAction = if (isPlaying.value == true) ACTION_PAUSE else ACTION_PLAY
        val playPauseTitle = if (isPlaying.value == true) "Pause" else "Play"

        val notification = NotificationCompat.Builder(this, "MUSIC_PLAYER_CHANNEL")
            .setContentTitle(musicList[currentPosition].title)
            .setContentText(musicList[currentPosition].artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_previous, "Previous", getPendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseIcon, playPauseTitle, getPendingIntent(playPauseAction))
            .addAction(R.drawable.ic_next, "Next", getPendingIntent(ACTION_NEXT))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .build()

        startForeground(1, notification)
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MUSIC_PLAYER_CHANNEL",
                "Music Player",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun setMusicList(list: ArrayList<Music>) {
        musicList = list
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying.postValue(false)
        showNotification()
    }

    fun resume() {
        mediaPlayer?.start()
        isPlaying.postValue(true)
        showNotification()
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentMusic.postValue(null)
        isPlaying.postValue(false)
        stopForeground(true)
    }

    fun next() {
        if (currentPosition < musicList.size - 1) {
            play(currentPosition + 1)
        } else {
            play(0) // Loop to the beginning
        }
    }

    fun previous() {
        if (currentPosition > 0) {
            play(currentPosition - 1)
        } else {
            play(musicList.size - 1) // Loop to the end
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        next()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    companion object {
        const val ACTION_PLAY = "PLAY"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_PREVIOUS = "PREVIOUS"
    }
}