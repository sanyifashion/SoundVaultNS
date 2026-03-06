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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.Music
import kotlin.random.Random

class MusicService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private var musicList: ArrayList<Music> = ArrayList()
    private var currentPosition: Int = -1
    var isShuffleEnabled = false
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
            val songToPlay = musicList[position]
            currentPosition = position
            
            Log.d("MusicService", "Playing song at pos $position: ${songToPlay.title}")

            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                
                mediaPlayer = MediaPlayer.create(this, songToPlay.contentUri)
                
                if (mediaPlayer != null) {
                    mediaPlayer?.apply {
                        setOnCompletionListener(this@MusicService)
                        setOnErrorListener(this@MusicService)
                        start()
                        this@MusicService.isPlaying.postValue(true)
                        this@MusicService.currentMusic.postValue(songToPlay)
                        showNotification()
                    }
                } else {
                    Log.e("MusicService", "Failed to create MediaPlayer for Uri: ${songToPlay.contentUri}")
                    playWithPath(songToPlay)
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Error during play()", e)
                isPlaying.postValue(false)
            }
        }
    }

    private fun playWithPath(song: Music) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(song.path)
                prepare()
                start()
                setOnCompletionListener(this@MusicService)
                setOnErrorListener(this@MusicService)
            }
            isPlaying.postValue(true)
            currentMusic.postValue(song)
            showNotification()
        } catch (e: Exception) {
            Log.e("MusicService", "Error playing with path fallback", e)
            isPlaying.postValue(false)
        }
    }

    private fun showNotification() {
        if (currentPosition < 0 || currentPosition >= musicList.size) return
        
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
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPlaying.postValue(false)
            showNotification()
        }
    }

    fun resume() {
        if (mediaPlayer != null && isPlaying.value == false) {
            mediaPlayer?.start()
            isPlaying.postValue(true)
            showNotification()
        } else if (mediaPlayer == null && currentPosition >= 0 && currentPosition < musicList.size) {
            play(currentPosition)
        }
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
        if (musicList.isNotEmpty()) {
            val newPosition = if (isShuffleEnabled) {
                var randomPos = Random.nextInt(musicList.size)
                if (musicList.size > 1 && randomPos == currentPosition) {
                    randomPos = (randomPos + 1) % musicList.size
                }
                randomPos
            } else {
                if (currentPosition < musicList.size - 1) currentPosition + 1 else 0
            }
            play(newPosition)
        }
    }

    fun previous() {
        if (musicList.isNotEmpty()) {
            val newPosition = if (isShuffleEnabled) {
                Random.nextInt(musicList.size)
            } else {
                if (currentPosition > 0) currentPosition - 1 else musicList.size - 1
            }
            play(newPosition)
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        next()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e("MusicService", "MediaPlayer error: $what, $extra")
        isPlaying.postValue(false)
        return true
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getAudioSessionId(): Int {
        return mediaPlayer?.audioSessionId ?: 0
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }

    companion object {
        const val ACTION_PLAY = "PLAY"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_PREVIOUS = "PREVIOUS"
    }
}
