package com.jing.sakura.compose.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class SpeechToTextParser(
    val context: Context
) : RecognitionListener {

    private val _state = MutableStateFlow(SpeechToTextState())

    val state: StateFlow<SpeechToTextState>
        get() = _state

    private val _speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    fun startListening() {
        _state.update { SpeechToTextState() }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.update { it.copy(error = "设备不支持语音识别") }
            return
        }
        val recognizerIntent = Intent("android.speech.action.RECOGNIZE_SPEECH").apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }
        _speechRecognizer.setRecognitionListener(this)
        _speechRecognizer.startListening(recognizerIntent)
        _state.update { it.copy(isSpeaking = true) }
    }

    fun stopListening() {
        _speechRecognizer.cancel()
        _state.update { it.copy(isSpeaking = false) }
    }

    override fun onReadyForSpeech(p0: Bundle?) {
        _state.update { it.copy(error = null) }
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(p0: Float) = Unit

    override fun onBufferReceived(p0: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        _state.update { it.copy(isSpeaking = false) }
    }

    override fun onError(p0: Int) {
        _state.update {
            it.copy(
                isSpeaking = false,
                error = "Speech recognizer error: $p0"
            )
        }
    }

    override fun onResults(p0: Bundle?) {
        p0?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
            _state.update {
                it.copy(
                    text = text,
                    isSpeaking = false
                )
            }
        } ?: _state.update {
            it.copy(isSpeaking = false, text = "")
        }
    }

    override fun onPartialResults(p0: Bundle?) = Unit

    override fun onEvent(p0: Int, p1: Bundle?) = Unit
}

data class SpeechToTextState(
    val text: String = "", val isSpeaking: Boolean = false, val error: String? = null
)