package com.example.controleventilador

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet6Address
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Procura o ESP32 na rede local via mDNS/DNS-SD (servico _http._tcp com "esp32" no nome).
 *
 * - Desiste depois de [timeoutMs] e chama [onError] (antes ficava em "Buscando..." para sempre).
 * - Para a busca assim que encontra (antes ela continuava rodando em segundo plano).
 * - Resolve so um servico por vez (resolver dois ao mesmo tempo dava erro no Android).
 * - [onDeviceFound] e [onError] sao sempre chamados na thread principal.
 */
class NsdHelper(
    context: Context,
    private val onDeviceFound: (String) -> Unit,
    private val onError: () -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceType = "_http._tcp."
    private val targetServiceName = "esp32" // corresponde ao mDNS esp32.local

    // Um listener novo a cada busca: o Android nao deixa reutilizar um que ainda
    // esta registrado (o "Tentar novamente" logo apos parar dava erro).
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val finished = AtomicBoolean(true)
    private val resolving = AtomicBoolean(false)

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "ESP32 nao encontrado em ${timeoutMs} ms")
        finishWithError()
    }

    fun startDiscovery() {
        stopDiscovery()
        finished.set(false)
        resolving.set(false)

        val listener = createDiscoveryListener()
        discoveryListener = listener
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Nao foi possivel iniciar a busca", e)
            discoveryListener = null
            finishWithError()
            return
        }
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
    }

    fun stopDiscovery() {
        mainHandler.removeCallbacks(timeoutRunnable)
        val listener = discoveryListener ?: return
        discoveryListener = null
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar a busca", e)
        }
    }

    private fun finishWithUrl(url: String) {
        if (!finished.compareAndSet(false, true)) return
        mainHandler.post {
            stopDiscovery()
            onDeviceFound(url)
        }
    }

    private fun finishWithError() {
        if (!finished.compareAndSet(false, true)) return
        mainHandler.post {
            stopDiscovery()
            onError()
        }
    }

    private fun createDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Busca iniciada")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Servico encontrado: ${service.serviceName}")
            val isTarget = service.serviceType == serviceType &&
                service.serviceName.contains(targetServiceName, ignoreCase = true)
            if (isTarget && !finished.get() && resolving.compareAndSet(false, true)) {
                Log.d(TAG, "ESP32 encontrado, resolvendo o endereco...")
                @Suppress("DEPRECATION")
                nsdManager.resolveService(service, createResolveListener())
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.w(TAG, "Servico perdido: ${service.serviceName}")
        }

        override fun onDiscoveryStopped(regType: String) {
            Log.i(TAG, "Busca parada: $regType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Falha ao iniciar a busca: $errorCode")
            discoveryListener = null // nao chega a ficar registrado; nao precisa parar
            finishWithError()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Falha ao parar a busca: $errorCode")
        }
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Nao desiste na hora: libera para tentar de novo se o servico
            // reaparecer. Se nada der certo, o tempo limite mostra o erro.
            Log.e(TAG, "Falha ao resolver: $errorCode")
            resolving.set(false)
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Endereco resolvido: $serviceInfo")
            @Suppress("DEPRECATION")
            val address = serviceInfo.host
            val host = if (address is Inet6Address) {
                // IPv6 precisa de colchetes na URL (e o "%" do escopo vira "%25")
                "[${address.hostAddress?.replace("%", "%25")}]"
            } else {
                address.hostAddress
            }
            finishWithUrl("http://$host:${serviceInfo.port}")
        }
    }

    companion object {
        private const val TAG = "NSD"
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
