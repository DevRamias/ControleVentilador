package com.example.controleventilador

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.controleventilador.ui.theme.ControleVentiladorTheme

sealed class AppState {
    object Searching : AppState()
    data class Found(val url: String) : AppState()
    data class Error(val message: String) : AppState()
}

class MainActivity : ComponentActivity() {
    private lateinit var nsdHelper: NsdHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var appState by remember { mutableStateOf<AppState>(AppState.Searching) }

            LaunchedEffect(Unit) {
                nsdHelper = NsdHelper(
                    context = this@MainActivity,
                    onDeviceFound = { url ->
                        appState = AppState.Found(url)
                    },
                    onError = {
                        appState = AppState.Error("Dispositivo não encontrado. Verifique se você está na mesma rede Wi-Fi que o ESP32.")
                    }
                )
                nsdHelper.startDiscovery()
            }

            ControleVentiladorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (val state = appState) {
                            is AppState.Searching -> LoadingScreen()
                            is AppState.Found -> WebViewScreen(state.url)
                            is AppState.Error -> ErrorScreen(state.message) {
                                appState = AppState.Searching
                                nsdHelper.stopDiscovery()
                                nsdHelper.startDiscovery()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::nsdHelper.isInitialized) {
            nsdHelper.stopDiscovery()
        }
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Buscando ESP32 na rede...")
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Tentar Novamente")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Configurações essenciais para interatividade
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true // Necessário para muitos frameworks web modernos
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient() // Permite diálogos JS e progresso
                
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
