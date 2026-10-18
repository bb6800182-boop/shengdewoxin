package com.c520.shengdewoxin

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** 系統內建 TTS，整個程式共用一個；助理服務啟動時就先暖機，念第一句不用等引擎連線。只在主執行緒呼叫。 */
object Speaker {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var seq = 0
    private val done = HashMap<String, () -> Unit>()
    private var pending: Pair<String, (() -> Unit)?>? = null

    fun warmUp(c: Context) {
        if (tts != null || failed) return
        tts = TextToSpeech(c.applicationContext) { status -> main.post { onInit(status) } }
    }

    private fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            // 沒有 TTS 引擎也不能卡住流程：直接當作念完
            failed = true
            tts = null
            pending?.second?.invoke()
            pending = null
            return
        }
        val r = t.setLanguage(Locale.TAIWAN)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.CHINESE)
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
        ready = true
        pending?.let { (text, cb) ->
            pending = null
            say(text, cb)
        }
    }

    /** 念出文字，念完（或念不出來）時呼叫 onDone。 */
    fun speak(c: Context, text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        warmUp(c)
        when {
            failed -> onDone?.invoke()
            !ready -> pending = text to onDone
            else -> say(text, onDone)
        }
    }

    private fun say(text: String, onDone: (() -> Unit)?) {
        val id = "u${++seq}"
        if (onDone != null) done[id] = onDone
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
    }

    fun stop() {
        pending = null
        done.clear()
        tts?.stop()
    }

    private fun finish(id: String?) {
        main.post { id?.let { done.remove(it)?.invoke() } }
    }
}
