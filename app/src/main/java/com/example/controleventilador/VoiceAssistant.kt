package com.example.controleventilador

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Fala (TextToSpeech) e ouve (SpeechRecognizer) em portugues do Brasil.
 * As duas coisas sao "suspend": o fluxo do app fica escrito em ordem
 * (fala -> espera terminar -> ouve -> recebe o texto).
 */
class VoiceAssistant(context: Context) {

    private val appContext = context.applicationContext
    private val ttsReady = CompletableDeferred<Boolean>()
    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        ttsReady.complete(status == TextToSpeech.SUCCESS)
    }

    val canListen: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** Fala o texto e so retorna quando terminar de falar (ou em ate 10 s). */
    suspend fun speak(text: String) {
        val ok = withTimeoutOrNull(5000) { ttsReady.await() } ?: false
        if (!ok) return
        tts.language = Locale.forLanguageTag("pt-BR")
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                val id = UUID.randomUUID().toString()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                })
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                cont.invokeOnCancellation { tts.stop() }
            }
        }
    }

    /**
     * Ouve uma resposta e devolve as alternativas reconhecidas (a mais provavel
     * primeiro), ou lista vazia se nao ouviu nada / deu erro.
     */
    suspend fun listen(): List<String> = withContext(Dispatchers.Main) {
        if (!canListen) return@withContext emptyList()
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            fun finish(result: List<String>) {
                recognizer.destroy()
                if (cont.isActive) cont.resume(result)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                }
                override fun onError(error: Int) {
                    Log.w(TAG, "Reconhecimento de voz: erro $error")
                    finish(emptyList())
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            recognizer.startListening(intent)
            cont.invokeOnCancellation {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "Voz"
    }
}
