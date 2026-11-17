package com.example.controleventilador

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Conversa com o servidor HTTP do ESP32 (as mesmas rotas que o painel usa). */
class FanApi(private val baseUrl: String) {

    data class State(val on: Boolean, val remainingSeconds: Long)

    /** Estado atual. Usa /state; se a placa for antiga (sem /state), cai para /status. */
    suspend fun state(): State = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(get("/state"))
            State(on = j.optBoolean("on"), remainingSeconds = j.optLong("remaining"))
        } catch (e: IOException) {
            val status = get("/status")
            State(on = status.contains("Ligado"), remainingSeconds = 0)
        }
    }

    /** Liga por [minutes] minutos (o timer recomeca a contar a partir de agora). */
    suspend fun start(minutes: Int) = withContext(Dispatchers.IO) {
        get("/start?duration=${minutes.coerceIn(1, VoiceParser.MAX_MINUTES)}")
        Unit
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        get("/stop")
        Unit
    }

    private fun get(path: String): String {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode} em $path")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
