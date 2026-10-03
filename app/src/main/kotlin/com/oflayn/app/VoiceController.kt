package com.oflayn.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.oflayn.domain.voice.VoiceError
import java.util.Locale

/** Thin Android adapter for STT/TTS. All state logic lives in the tested [com.oflayn.domain.voice.VoiceStateMachine]. */
class VoiceController(private val ctx: Context, private val cb: Callbacks) {
    interface Callbacks {
        fun onFinalText(text: String)
        fun onError(error: VoiceError)
        fun onSpeakDone()
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun sttAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun startListening(localeTag: String, preferOffline: Boolean) {
        stopSpeaking() // never record while TTS plays: prevents audio feedback
        if (!sttAvailable()) return cb.onError(VoiceError.NO_MICROPHONE)
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                cb.onFinalText(text)
            }
            override fun onError(error: Int) {
                cb.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION_DENIED
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.UNCLEAR_SPEECH
                        12, 13 -> VoiceError.OFFLINE_STT_UNAVAILABLE // LANGUAGE_NOT_SUPPORTED / LANGUAGE_UNAVAILABLE
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            if (preferOffline) VoiceError.OFFLINE_STT_UNAVAILABLE else VoiceError.NETWORK
                        else -> VoiceError.STT_FAILURE
                    },
                )
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        try { r.startListening(intent) } catch (e: Exception) { cb.onError(VoiceError.STT_FAILURE) }
    }

    fun stopListening() { runCatching { recognizer?.stopListening() } }

    fun speak(text: String, localeTag: String) {
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(ctx) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) speak(text, localeTag) else cb.onError(VoiceError.TTS_FAILURE)
            }.also { attach(it) }
            return
        }
        if (!ttsReady) return cb.onError(VoiceError.TTS_FAILURE)
        val res = engine.setLanguage(Locale.forLanguageTag(localeTag))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) return cb.onError(VoiceError.TTS_FAILURE)
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "oflayn-reply") == TextToSpeech.ERROR) cb.onError(VoiceError.TTS_FAILURE)
    }

    private fun attach(t: TextToSpeech) {
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { cb.onSpeakDone() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { cb.onError(VoiceError.TTS_FAILURE) } }
        })
    }

    fun stopSpeaking() { runCatching { tts?.stop() } }

    fun release() {
        runCatching { recognizer?.destroy() }
        runCatching { tts?.stop(); tts?.shutdown() }
        recognizer = null; tts = null; ttsReady = false
    }
}
