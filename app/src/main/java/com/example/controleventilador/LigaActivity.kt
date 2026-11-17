package com.example.controleventilador

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.controleventilador.ui.theme.ControleVentiladorTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tela de voz: aberta pelo Assistente ("Ok Google, abre ventilador") ou pelo
 * icone "Ventilador". (O icone "Painel" abre a MainActivity.)
 *
 *  - Ventilador DESLIGADO: liga por 5 min na hora e pergunta por quanto tempo.
 *    A resposta (falada ou no botao) troca o timer; sem resposta fica em 5 min.
 *  - Ventilador LIGADO: pergunta se quer desligar.
 *
 * Fecha sozinha quando termina.
 */
class LigaActivity : ComponentActivity() {

    /** O que a tela mostra. */
    data class Ui(
        val title: String,
        val subtitle: String? = null,
        val busy: Boolean = false,
        val listening: Boolean = false,
        val options: List<Pair<String, Any>> = emptyList(),
        val error: Boolean = false
    )

    private val ui = mutableStateOf(Ui("Procurando o ventilador...", busy = true))
    private lateinit var voice: VoiceAssistant
    private var flowJob: Job? = null

    // Escolha feita num botao enquanto o app pergunta (ganha da voz)
    private var pendingPick: ((Any) -> Unit)? = null

    private var micResult: CompletableDeferred<Boolean>? = null
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micResult?.complete(granted)
        }
    private var micAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        voice = VoiceAssistant(this)
        setContent {
            ControleVentiladorTheme {
                LigaScreen(
                    ui = ui.value,
                    onOption = { value -> pendingPick?.invoke(value) },
                    onOpenPanel = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }
        startFlow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startFlow() // "Ok Google, abre Liga" de novo com a tela aberta
    }

    override fun onDestroy() {
        flowJob?.cancel()
        voice.shutdown()
        super.onDestroy()
    }

    private fun startFlow() {
        flowJob?.cancel()
        flowJob = lifecycleScope.launch { runFlow() }
    }

    private suspend fun runFlow() {
        ui.value = Ui("Procurando o ventilador...", busy = true)
        micAllowed = ensureMicPermission()

        val url = Device.locate(this)
        if (url == null) {
            fail("Não encontrei o ventilador", "Confira se o celular está no mesmo Wi-Fi da placa.")
            return
        }
        val api = FanApi(url)

        val state = try {
            api.state()
        } catch (e: Exception) {
            fail("O ventilador não respondeu", "Tente de novo em alguns segundos.")
            return
        }

        if (!state.on) {
            // 1) Liga por 5 minutos na hora
            try {
                api.start(DEFAULT_MINUTES)
            } catch (e: Exception) {
                fail("Não consegui ligar o ventilador", "Tente de novo em alguns segundos.")
                return
            }
            val pergunta = "Por quanto tempo você quer?"
            ui.value = Ui(
                title = "Ventilador ligado por 5 minutos",
                subtitle = pergunta,
                options = listOf(
                    "15 min" to 15, "30 min" to 30, "1 hora" to 60,
                    "2 horas" to 120, "Manter 5 min" to DEFAULT_MINUTES
                )
            )
            voice.speak("Liguei o ventilador por 5 minutos. $pergunta")

            // 2) Espera o tempo (voz ou botao)
            val minutes: Int? = ask(pergunta) { VoiceParser.parseMinutes(it) }
            if (minutes != null && minutes != DEFAULT_MINUTES) {
                try {
                    api.start(minutes)
                    done("Ventilador ligado por ${VoiceParser.descrever(minutes)}")
                    voice.speak("Pronto, ventilador ligado por ${VoiceParser.descrever(minutes)}.")
                } catch (e: Exception) {
                    fail("Não consegui mudar o tempo", "O ventilador continua ligado por 5 minutos.")
                    return
                }
            } else {
                done("Ventilador ligado por 5 minutos")
                voice.speak("Tudo bem, fica ligado por 5 minutos.")
            }
        } else {
            // Ja estava ligado: pergunta se quer desligar
            val pergunta = "Quer desligar?"
            ui.value = Ui(
                title = "O ventilador já está ligado",
                subtitle = pergunta,
                options = listOf("Desligar" to true, "Deixar ligado" to false)
            )
            voice.speak("O ventilador já está ligado. $pergunta")
            val desligar: Boolean? = ask(pergunta) { VoiceParser.parseYesNo(it) }
            if (desligar == true) {
                try {
                    api.stop()
                    done("Ventilador desligado")
                    voice.speak("Desliguei o ventilador.")
                } catch (e: Exception) {
                    fail("Não consegui desligar", "Tente de novo em alguns segundos.")
                    return
                }
            } else {
                done("O ventilador continua ligado")
                voice.speak("Ok, deixei ligado.")
            }
        }

        delay(CLOSE_DELAY_MS)
        finish()
    }

    /**
     * Pergunta e espera a resposta: ouve ate 2 vezes e, se nao entender,
     * ainda espera um botao por alguns segundos. Um toque num botao a qualquer
     * momento ganha da voz. null = nenhuma resposta valida.
     */
    private suspend fun <T : Any> ask(question: String, parse: (String) -> T?): T? {
        val pick = CompletableDeferred<T>()
        @Suppress("UNCHECKED_CAST")
        pendingPick = { value -> pick.complete(value as T) }
        try {
            if (!micAllowed || !voice.canListen) {
                return withTimeoutOrNull(BUTTON_WAIT_MS * 2) { pick.await() }
            }
            for (tentativa in 0 until 2) {
                if (pick.isCompleted) return pick.await()
                setListening(true)
                val heard: List<String>? = coroutineScope {
                    val escuta = async { voice.listen() }
                    select {
                        pick.onAwait { escuta.cancel(); null }
                        escuta.onAwait { it }
                    }
                }
                setListening(false)
                if (heard == null) return pick.await() // tocou num botao
                heard.firstNotNullOfOrNull(parse)?.let { return it }
                if (tentativa == 0) {
                    voice.speak(if (heard.isEmpty()) "Não ouvi. $question" else "Não entendi. $question")
                }
            }
            return withTimeoutOrNull(BUTTON_WAIT_MS) { pick.await() }
        } finally {
            pendingPick = null
            setListening(false)
        }
    }

    private suspend fun ensureMicPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) return true
        val result = CompletableDeferred<Boolean>()
        micResult = result
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        return result.await()
    }

    private fun setListening(on: Boolean) {
        ui.value = ui.value.copy(listening = on)
    }

    private fun done(title: String) {
        ui.value = Ui(title = title)
    }

    private suspend fun fail(title: String, subtitle: String) {
        ui.value = Ui(title = title, subtitle = subtitle, error = true)
        voice.speak(title)
    }

    companion object {
        const val DEFAULT_MINUTES = 5
        private const val BUTTON_WAIT_MS = 8_000L
        private const val CLOSE_DELAY_MS = 2_500L
    }
}

@Composable
private fun LigaScreen(
    ui: LigaActivity.Ui,
    onOption: (Any) -> Unit,
    onOpenPanel: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = ui.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (ui.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            ui.subtitle?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            if (ui.busy) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
            }
            if (ui.listening) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "🎤 Ouvindo...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (ui.options.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                ui.options.chunked(2).forEach { linha ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        linha.forEach { (label, value) ->
                            Button(onClick = { onOption(value) }, modifier = Modifier.weight(1f)) {
                                Text(label)
                            }
                        }
                        if (linha.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenPanel) { Text("Abrir painel") }
                TextButton(onClick = onClose) { Text("Fechar") }
            }
        }
    }
}
