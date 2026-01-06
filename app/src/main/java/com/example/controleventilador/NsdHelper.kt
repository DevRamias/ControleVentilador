package com.example.controleventilador

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdHelper(context: Context, private val onDeviceFound: (String) -> Unit, private val onError: () -> Unit) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_http._tcp."
    private val targetServiceName = "esp32" // Alterado para corresponder ao seu mDNS esp32.local

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d("NSD", "Discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d("NSD", "Service found: ${service.serviceName}")
            // Verifica se o tipo do serviço bate e se o nome contém "esp32"
            if (service.serviceType == serviceType && service.serviceName.contains(targetServiceName, ignoreCase = true)) {
                Log.d("NSD", "Target service found, resolving...")
                nsdManager.resolveService(service, resolveListener)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e("NSD", "Service lost: $service")
        }

        override fun onDiscoveryStopped(regType: String) {
            Log.i("NSD", "Discovery stopped: $regType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("NSD", "Discovery failed: Error code:$errorCode")
            nsdManager.stopServiceDiscovery(this)
            onError()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("NSD", "Stop discovery failed: Error code:$errorCode")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("NSD", "Resolve failed: $errorCode")
            onError()
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d("NSD", "Resolve Succeeded. $serviceInfo")
            val host = serviceInfo.host.hostAddress
            val port = serviceInfo.port
            val url = "http://$host:$port"
            onDeviceFound(url)
        }
    }

    fun startDiscovery() {
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e("NSD", "Error stopping discovery", e)
        }
    }
}
