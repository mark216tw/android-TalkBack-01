package com.example.nianchulai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class ReaderStatus { LOADING, READY, PLAYING, PAUSED, ERROR }

internal class Reader(context: Context) {
    var status by mutableStateOf(ReaderStatus.LOADING)
        private set
    var message by mutableStateOf("正在啟動離線語音…")
        private set
    var currentText by mutableStateOf("")
        private set
    var currentSentenceIndex by mutableStateOf(0)
        private set
    var sentenceCount by mutableStateOf(0)
        private set
    var onChanged: (() -> Unit)? = null
    var readingText: String = ""
        private set
    val sentencePosition: Int get() = currentSentenceIndex
    val readingSpeed: Float get() = speed
    var readingMode: ReadingMode = ReadingMode.CHINESE
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var chineseVoice: Voice? = null
    private var englishVoice: Voice? = null
    private var segments = emptyList<ReadingSegment>()
    private var sentences = emptyList<ReadingSentence>()
    private var position = 0
    private var generation = 0
    private var speed = 1f
    private var stopAfterSentence = false
    private var closed = false

    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            handler.post {
                if (!closed) {
                    if (result == TextToSpeech.SUCCESS) initializeVoices()
                    else fail("無法啟動文字轉語音。請檢查手機的語音引擎設定。")
                }
            }
        }
    }

    private fun initializeVoices() {
        val voices = engine?.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        chineseVoice = voices.filter { it.locale.language == "zh" }
            .sortedWith(compareByDescending<Voice> { it.locale.country == "TW" }
                .thenByDescending { it.locale.country == "HK" })
            .firstOrNull()
        englishVoice = voices.filter { it.locale.language == "en" }
            .sortedByDescending { it.locale.country == "US" }
            .firstOrNull()
        if (chineseVoice == null) {
            fail("找不到中文的離線語音。請到系統文字轉語音設定下載語音資料，然後重新開啟 App。")
        } else {
            status = ReaderStatus.READY
            message = "已準備好離線朗讀"
        }
    }

    fun start(article: String, readingSpeed: Float, fromSentence: Int = 0,
              mode: ReadingMode = ReadingMode.CHINESE) {
        if (status == ReaderStatus.LOADING || status == ReaderStatus.ERROR) return
        stop(notify = false)
        sentences = sentencesForReading(article)
        segments = splitForReading(article, mode)
        if (segments.isEmpty()) {
            message = "請先輸入文章。"
            return
        }
        if (segments.any { it.language == Language.ENGLISH } && englishVoice == null) {
            message = "找不到英文的離線語音。請到系統文字轉語音設定下載英文語音資料，然後重新開啟 App。"
            onChanged?.invoke()
            return
        }
        speed = readingSpeed
        readingMode = mode
        readingText = article
        sentenceCount = sentences.size
        val target = fromSentence.coerceIn(0, sentenceCount - 1)
        position = segments.indexOfFirst { it.sentenceIndex >= target }.coerceAtLeast(0)
        status = ReaderStatus.PLAYING
        speakNext()
    }

    fun jumpBySentence(direction: Int) {
        if (status != ReaderStatus.PLAYING && status != ReaderStatus.PAUSED) return
        jumpToSentence((currentSentenceIndex + direction).coerceIn(0, sentenceCount - 1))
    }

    fun jumpToSentence(target: Int) {
        if (status != ReaderStatus.PLAYING && status != ReaderStatus.PAUSED) return
        val next = segments.indexOfFirst { it.sentenceIndex == target }
        if (next < 0) return
        generation++
        engine?.stop()
        position = next
        currentSentenceIndex = target
        currentText = sentences[target].text
        if (status == ReaderStatus.PLAYING) speakNext() else onChanged?.invoke()
    }

    fun finishCurrentSentenceThenStop() {
        if (status == ReaderStatus.PLAYING) stopAfterSentence = true
    }

    fun pause() {
        if (status != ReaderStatus.PLAYING) return
        status = ReaderStatus.PAUSED
        generation++
        engine?.stop()
        message = "已暫停；續讀時會重唸目前片段。"
        onChanged?.invoke()
    }

    fun resume(readingSpeed: Float) {
        if (status != ReaderStatus.PAUSED) return
        speed = readingSpeed
        status = ReaderStatus.PLAYING
        speakNext()
    }

    fun stop(notify: Boolean = true) {
        generation++
        engine?.stop()
        segments = emptyList()
        sentences = emptyList()
        stopAfterSentence = false
        position = 0
        sentenceCount = 0
        currentText = ""
        if (status == ReaderStatus.PLAYING || status == ReaderStatus.PAUSED) {
            status = ReaderStatus.READY
            message = "已停止朗讀。"
        }
        if (notify) onChanged?.invoke()
    }

    private fun speakNext() {
        if (status != ReaderStatus.PLAYING) return
        if (position >= segments.size) {
            status = ReaderStatus.READY
            currentText = ""
            message = "朗讀完成。"
            onChanged?.invoke()
            return
        }
        val segment = segments[position]
        val voice = when (segment.language) {
            Language.CHINESE -> chineseVoice
            Language.ENGLISH -> englishVoice
        }
        val tts = engine ?: return fail("語音引擎無法使用。")
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            return fail("無法使用離線語音，請檢查系統語音資料。")
        }
        tts.setSpeechRate(speed)
        currentSentenceIndex = segment.sentenceIndex
        currentText = sentences[currentSentenceIndex].text
        message = "正在朗讀第 ${currentSentenceIndex + 1}／${sentenceCount} 句"
        onChanged?.invoke()
        val utteranceId = "${generation}_${position}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) {
                handler.post {
                    if (!closed && status == ReaderStatus.PLAYING && id == "${generation}_${position}") {
                        position++
                        if (finishAfterSegment(stopAfterSentence, currentSentenceIndex,
                                segments.getOrNull(position)?.sentenceIndex)) {
                            currentSentenceIndex = if (position < segments.size) segments[position].sentenceIndex
                                else sentenceCount
                            status = ReaderStatus.READY
                            currentText = ""
                            message = "睡眠計時結束。"
                            stopAfterSentence = false
                            onChanged?.invoke()
                        } else speakNext()
                    }
                }
            }
            @Deprecated("Called by older TTS engines")
            override fun onError(id: String) = onSpeechError(id)
            override fun onError(id: String, errorCode: Int) = onSpeechError(id)
        })
        if (tts.speak(prepareSpeech(segment.text, segment.language), TextToSpeech.QUEUE_FLUSH,
                null, utteranceId) != TextToSpeech.SUCCESS) {
            fail("朗讀失敗，請確認離線語音資料已安裝。")
        }
    }

    private fun onSpeechError(id: String) {
        handler.post {
            if (!closed && status == ReaderStatus.PLAYING && id == "${generation}_${position}") {
                fail("朗讀失敗，請確認離線語音資料已安裝。")
            }
        }
    }

    private fun fail(reason: String) {
        generation++
        engine?.stop()
        status = ReaderStatus.ERROR
        currentText = ""
        message = reason
        onChanged?.invoke()
    }

    fun close() {
        closed = true
        generation++
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
