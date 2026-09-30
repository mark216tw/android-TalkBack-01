package com.example.nianchulai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal object ReadingSession {
    private var instance: Reader? = null
    fun reader(context: Context): Reader = instance ?: Reader(context.applicationContext).also { instance = it }
    var playlistArticleId by mutableStateOf<String?>(null)
    var articleId by mutableStateOf<String?>(null)
    var currentTitle by mutableStateOf("文章朗讀")
    var timerEndsAt by mutableLongStateOf(0L)
    var timerFinishing by mutableStateOf(false)
    var timerMinutes by mutableIntStateOf(0)
    var settingsError by mutableStateOf<String?>(null)
}

class ReadingService : Service() {
    private lateinit var reader: Reader
    private lateinit var store: ArticleStore
    private val handler = Handler(Looper.getMainLooper())
    private var timerTask: Runnable? = null
    private var queueIds = emptyList<String>()
    private var queueIndex = -1

    override fun onCreate() {
        super.onCreate()
        reader = ReadingSession.reader(this)
        store = ArticleStore(this)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "文章朗讀", NotificationManager.IMPORTANCE_LOW)
        )
        reader.onChanged = ::updatePlayback
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> {
                if (reader.status == ReaderStatus.PLAYING || reader.status == ReaderStatus.PAUSED) {
                    saveProgress(reader.sentencePosition)
                }
                // Android requires the foreground notification before starting playback.
                showNotification()
                // The draft is already stored locally. Avoid sending long imported articles
                // through Android's size-limited Binder transaction for service intents.
                val text = intent.getStringExtra(TEXT) ?: store.draft
                queueIds = emptyList()
                queueIndex = -1
                ReadingSession.playlistArticleId = null
                val item = savedArticleForReading(intent.getStringExtra(ARTICLE_ID), text, store.articles())
                ReadingSession.articleId = item?.id
                ReadingSession.currentTitle = item?.title ?: "文章朗讀"
                val mode = intent.getStringExtra(MODE)?.let {
                    runCatching { ReadingMode.valueOf(it) }.getOrNull()
                } ?: ReadingMode.CHINESE
                reader.start(text, intent.getFloatExtra(SPEED, 1f), intent.getIntExtra(POSITION, 0), mode)
                if (reader.status != ReaderStatus.PLAYING) updatePlayback()
            }
            START_PLAYLIST -> {
                if (reader.status == ReaderStatus.PLAYING || reader.status == ReaderStatus.PAUSED) {
                    saveProgress(reader.sentencePosition)
                }
                showNotification()
                queueIds = store.playlistIds()
                val resume = intent.getBooleanExtra(RESUME_PLAYLIST, false)
                val progress = if (resume) store.playlistProgress() else null
                val requestedId = intent.getStringExtra(PLAYLIST_ARTICLE_ID)
                val index = playlistStartIndex(queueIds, requestedId ?: progress?.articleId)
                val mode = progress?.mode ?: store.mode
                val speed = progress?.speed ?: store.speed
                playQueueFrom(index, progress?.sentence ?: 0, speed, mode)
                if (reader.status != ReaderStatus.PLAYING) updatePlayback()
            }
            PAUSE -> reader.pause()
            RESUME -> reader.resume(reader.readingSpeed)
            NEXT -> reader.jumpBySentence(1)
            PREVIOUS -> reader.jumpBySentence(-1)
            SEEK -> reader.jumpToSentence(intent.getIntExtra(POSITION, 0))
            STOP -> {
                saveProgress(reader.sentencePosition)
                cancelTimer()
                reader.stop()
            }
            SET_TIMER -> {
                val minutes = intent.getIntExtra(MINUTES, 0)
                if (minutes in listOf(15, 30, 60) &&
                    (reader.status == ReaderStatus.PLAYING || reader.status == ReaderStatus.PAUSED)) {
                    cancelTimer()
                    ReadingSession.timerMinutes = minutes
                    val duration = minutes * 60_000L
                    ReadingSession.timerEndsAt = SystemClock.elapsedRealtime() + duration
                    timerTask = Runnable {
                        ReadingSession.timerEndsAt = 0
                        ReadingSession.timerFinishing = true
                        if (reader.status == ReaderStatus.PLAYING) reader.finishCurrentSentenceThenStop()
                        else {
                            saveProgress(reader.sentencePosition)
                            reader.stop()
                        }
                    }.also { handler.postDelayed(it, duration) }
                    showNotification()
                }
            }
            CANCEL_TIMER -> {
                cancelTimer()
                if (reader.status == ReaderStatus.PLAYING || reader.status == ReaderStatus.PAUSED) showNotification()
            }
            UPDATE_SETTINGS -> {
                if (reader.status == ReaderStatus.PLAYING || reader.status == ReaderStatus.PAUSED) {
                    val speed = intent.getFloatExtra(SPEED, reader.readingSpeed).coerceIn(0.5f, 1.5f)
                    val mode = intent.getStringExtra(MODE)?.let {
                        runCatching { ReadingMode.valueOf(it) }.getOrNull()
                    } ?: reader.readingMode
                    ReadingSession.settingsError = reader.updateSettings(speed, mode)
                    if (ReadingSession.settingsError == null) {
                        store.speed = reader.readingSpeed
                        store.mode = reader.readingMode
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun playQueueFrom(index: Int, sentence: Int, speed: Float, mode: ReadingMode) {
        val articles = store.articles().associateBy { it.id }
        val next = nextPlayableIndex(queueIds, index, articles.values.toList())
        if (next == null) {
            queueIndex = -1
            queueIds = emptyList()
            ReadingSession.playlistArticleId = null
            ReadingSession.articleId = null
            ReadingSession.currentTitle = "文章朗讀"
            store.clearPlaylistProgress()
            reader.stop(notify = false)
            stopPlaybackService()
            return
        }
        queueIndex = next
        val item = articles.getValue(queueIds[next])
        ReadingSession.playlistArticleId = item.id
        ReadingSession.articleId = item.id
        ReadingSession.currentTitle = item.title
        val startSentence = if (next == index) sentence else 0
        store.setPlaylistProgress(PlaylistProgress(item.id, startSentence, speed, mode))
        reader.start(item.text, speed, startSentence, mode)
    }

    private fun saveProgress(sentence: Int) {
        val id = ReadingSession.playlistArticleId
        if (id != null) store.setPlaylistProgress(PlaylistProgress(id, sentence, reader.readingSpeed, reader.readingMode))
        else if (reader.readingText.isNotBlank()) {
            store.setBookmark(reader.readingText, sentence, reader.readingSpeed, reader.readingMode, ReadingSession.articleId)
        }
    }

    private fun cancelTimer() {
        reader.cancelSentenceStop()
        timerTask?.let(handler::removeCallbacks)
        timerTask = null
        ReadingSession.timerEndsAt = 0L
        ReadingSession.timerFinishing = false
        ReadingSession.timerMinutes = 0
    }

    private fun stopPlaybackService() {
        cancelTimer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updatePlayback() {
        when (reader.status) {
            ReaderStatus.PLAYING, ReaderStatus.PAUSED -> {
                saveProgress(reader.sentencePosition)
                showNotification()
            }
            else -> {
                when (reader.message) {
                    "朗讀完成。" -> {
                        if (queueIndex >= 0) {
                            playQueueFrom(queueIndex + 1, 0, reader.readingSpeed, reader.readingMode)
                            return
                        }
                        store.clearBookmark()
                    }
                    "睡眠計時結束。" -> {
                        if (queueIndex >= 0) {
                            if (reader.sentencePosition >= reader.sentenceCount) {
                                val next = nextPlayableIndex(queueIds, queueIndex + 1, store.articles())
                                if (next != null) {
                                    store.setPlaylistProgress(PlaylistProgress(queueIds[next], 0,
                                        reader.readingSpeed, reader.readingMode))
                                } else store.clearPlaylistProgress()
                            } else saveProgress(reader.sentencePosition)
                        } else if (reader.sentencePosition < reader.sentenceCount) {
                            saveProgress(reader.sentencePosition)
                        } else store.clearBookmark()
                    }
                }
                queueIds = emptyList()
                queueIndex = -1
                ReadingSession.playlistArticleId = null
                ReadingSession.articleId = null
                ReadingSession.currentTitle = "文章朗讀"
                stopPlaybackService()
            }
        }
    }

    private fun showNotification() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val paused = reader.status == ReaderStatus.PAUSED
        val toggle = action(if (paused) RESUME else PAUSE, 1)
        val stop = action(STOP, 2)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("${ReadingSession.currentTitle} · " +
                if (paused) "已暫停" else if (ReadingSession.timerFinishing) "本句後停止" else reader.message)
            .setContentIntent(open)
            .setOngoing(!paused)
            .addAction(Notification.Action.Builder(null, if (paused) "續讀" else "暫停", toggle).build())
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun action(name: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, ReadingService::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override fun onDestroy() {
        cancelTimer()
        reader.onChanged = null
        if (reader.status == ReaderStatus.PLAYING) reader.pause()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val START = "com.example.nianchulai.START"
        const val PAUSE = "com.example.nianchulai.PAUSE"
        const val RESUME = "com.example.nianchulai.RESUME"
        const val NEXT = "com.example.nianchulai.NEXT"
        const val PREVIOUS = "com.example.nianchulai.PREVIOUS"
        const val SEEK = "com.example.nianchulai.SEEK"
        const val STOP = "com.example.nianchulai.STOP"
        const val START_PLAYLIST = "com.example.nianchulai.START_PLAYLIST"
        const val SET_TIMER = "com.example.nianchulai.SET_TIMER"
        const val CANCEL_TIMER = "com.example.nianchulai.CANCEL_TIMER"
        const val UPDATE_SETTINGS = "com.example.nianchulai.UPDATE_SETTINGS"
        const val RESUME_PLAYLIST = "resume_playlist"
        const val PLAYLIST_ARTICLE_ID = "playlist_article_id"
        const val ARTICLE_ID = "article_id"
        const val MINUTES = "minutes"
        const val TEXT = "text"
        const val SPEED = "speed"
        const val POSITION = "position"
        const val MODE = "mode"
        private const val CHANNEL = "reading"
        private const val NOTIFICATION_ID = 1001
    }
}
