package com.example.controleventilador

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** Onde esta a placa: ultimo endereco que funcionou + busca na rede (mDNS). */
object Device {
    private const val PREFS = "controle_ventilador"
    private const val KEY_LAST_URL = "last_url"

    fun lastUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_URL, null)

    fun saveUrl(context: Context, url: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_URL, url).apply()
    }

    /** true se a placa respondeu em [url]/status dentro do tempo limite. */
    fun isReachable(url: String): Boolean = try {
        val conn = URL("$url/status").openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        try {
            conn.responseCode == HttpURLConnection.HTTP_OK
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Endereco da placa ("http://ip:porta") ou null se nao achou.
     * 1) testa o ultimo endereco salvo (~1,5 s); 2) procura na rede (ate 10 s).
     */
    suspend fun locate(context: Context): String? {
        lastUrl(context)?.let { saved ->
            if (withContext(Dispatchers.IO) { isReachable(saved) }) return saved
        }
        return suspendCancellableCoroutine { cont ->
            val helper = NsdHelper(
                context = context,
                onDeviceFound = { url ->
                    saveUrl(context, url)
                    if (cont.isActive) cont.resume(url)
                },
                onError = { if (cont.isActive) cont.resume(null) }
            )
            helper.startDiscovery()
            cont.invokeOnCancellation { helper.stopDiscovery() }
        }
    }
}
