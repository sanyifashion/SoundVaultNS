package com.example.soundvault.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.AppDatabase
import com.example.soundvault.data.EqualizerPreset
import com.example.soundvault.data.Music
import com.example.soundvault.data.SongPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

class MusicService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var musicList: ArrayList<Music> = ArrayList()
    private var currentPosition: Int = -1
    var isShuffleEnabled = false
    val currentMusic = MutableLiveData<Music?>()
    val isPlaying = MutableLiveData<Boolean>()

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val binder = MusicServiceBinder()

    inner class MusicServiceBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SoundVault").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resume()
                }

                override fun onPause() {
                    pause()
                }

                override fun onSkipToNext() {
                    next()
                }

                override fun onSkipToPrevious() {
                    previous()
                }

                override fun onStop() {
                    stop()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                }
            })
            isActive = true
        }
    }

    fun getSessionToken(): MediaSessionCompat.Token = mediaSession.sessionToken

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        intent?.action?.let {
            when (it) {
                ACTION_PLAY -> resume()
                ACTION_PAUSE -> pause()
                ACTION_NEXT -> next()
                ACTION_PREVIOUS -> previous()
                ACTION_STOP -> stop()
            }
        }
        return START_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resume()
                    resumeOnFocusGain = false
                }
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                resumeOnFocusGain = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying.value == true) {
                    pause()
                    resumeOnFocusGain = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
        }
    }

    fun play(position: Int) {
        if (!requestAudioFocus()) return

        if (position >= 0 && position < musicList.size) {
            val songToPlay = musicList[position]
            currentPosition = position
            
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                equalizer?.release()
                mediaPlayer = null
                equalizer = null
                
                mediaPlayer = MediaPlayer.create(this, songToPlay.contentUri)
                
                if (mediaPlayer != null) {
                    setupEqualizer(mediaPlayer!!.audioSessionId)
                    loadPresetForSong(songToPlay.id)
                    
                    mediaPlayer?.apply {
                        setOnCompletionListener(this@MusicService)
                        setOnErrorListener(this@MusicService)
                        start()
                        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                        updateMetadata(songToPlay)
                        this@MusicService.isPlaying.postValue(true)
                        this@MusicService.currentMusic.postValue(songToPlay)
                        showNotification()
                    }
                } else {
                    playWithPath(songToPlay)
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Error during play()", e)
                isPlaying.postValue(false)
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            }
        }
    }

    private fun playWithPath(song: Music) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(song.path)
                prepare()
                setupEqualizer(audioSessionId)
                loadPresetForSong(song.id)
                start()
                setOnCompletionListener(this@MusicService)
                setOnErrorListener(this@MusicService)
            }
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            updateMetadata(song)
            isPlaying.postValue(true)
            currentMusic.postValue(song)
            showNotification()
        } catch (e: Exception) {
            isPlaying.postValue(false)
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, getCurrentPosition().toLong(), 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMetadata(song: Music) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error setting up equalizer", e)
        }
    }

    fun applyPreset(preset: EqualizerPreset, saveForSong: Boolean = true) {
        equalizer?.let { eq ->
            val numBands = eq.numberOfBands
            if (numBands >= 3) {
                eq.setBandLevel(0, preset.bass.toShort())
                eq.setBandLevel((numBands / 2).toShort(), preset.mid.toShort())
                eq.setBandLevel((numBands - 1).toShort(), preset.treble.toShort())
            }
            
            if (saveForSong) {
                currentMusic.value?.let { music ->
                    serviceScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(this@MusicService)
                        if (preset.id == -1L) {
                            db.equalizerDao().deleteSongPreset(music.id)
                        } else {
                            db.equalizerDao().insertSongPreset(SongPreset(music.id, preset.id))
                        }
                    }
                }
            }
        }
    }

    private fun loadPresetForSong(songId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MusicService)
            val preset = db.equalizerDao().getPresetForSong(songId)
            preset?.let {
                launch(Dispatchers.Main) {
                    applyPreset(it, saveForSong = false)
                }
            } ?: run {
                launch(Dispatchers.Main) {
                    applyPreset(DEFAULT_PRESET, saveForSong = false)
                }
            }
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
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_music_note))
            .setContentIntent(pendingIntent)
            .setDeleteIntent(getPendingIntent(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying.value == true)
            .addAction(R.drawable.ic_previous, "Previous", getPendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseIcon, playPauseTitle, getPendingIntent(playPauseAction))
            .addAction(R.drawable.ic_next, "Next", getPendingIntent(ACTION_NEXT))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession.sessionToken))
            .build()

        if (isPlaying.value == true) {
            startForeground(1, notification)
        } else {
            stopForeground(false)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1, notification)
        }
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
                NotificationManager.IMPORTANCE_LOW
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
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            isPlaying.postValue(false)
            showNotification()
        }
    }

    fun resume() {
        if (!requestAudioFocus()) return
        
        if (mediaPlayer != null && isPlaying.value == false) {
            mediaPlayer?.start()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            isPlaying.postValue(true)
            showNotification()
        } else if (mediaPlayer == null && currentPosition >= 0 && currentPosition < musicList.size) {
            play(currentPosition)
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        equalizer?.release()
        mediaPlayer = null
        equalizer = null
        abandonAudioFocus()
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        currentMusic.postValue(null)
        isPlaying.postValue(false)
        stopForeground(true)
        stopSelf()
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
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        isPlaying.postValue(false)
        return true
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState(if (isPlaying.value == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getAudioSessionId(): Int {
        return mediaPlayer?.audioSessionId ?: 0
    }

    fun getEqualizerBands(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 0
    }

    fun getBandLevelRange(): IntArray {
        return equalizer?.bandLevelRange?.map { it.toInt() }?.toIntArray() ?: intArrayOf(-1500, 1500)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        equalizer?.release()
        mediaSession.release()
        abandonAudioFocus()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_PLAY = "PLAY"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_PREVIOUS = "PREVIOUS"
        const val ACTION_STOP = "STOP"
        
        val DEFAULT_PRESET = EqualizerPreset(id = -1, name = "Default", bass = 0, mid = 0, treble = 0)
    }
}
