package com.example.controleventilador

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.controleventilador.ui.theme.ControleVentiladorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AppState {
    object Searching : AppState()
    data class Found(val url: String) : AppState()
    data class Error(val message: String) : AppState()
}

class MainActivity : ComponentActivity() {
    private lateinit var nsdHelper: NsdHelper

    // Guarda o ultimo endereco que funcionou: na proxima vez o app abre direto,
    // sem esperar a busca na rede (compartilhado com a tela "Liga").
    private var lastUrl: String?
        get() = Device.lastUrl(this)
        set(value) = Device.saveUrl(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var appState by remember { mutableStateOf<AppState>(AppState.Searching) }

            fun search() {
                appState = AppState.Searching
                nsdHelper.startDiscovery()
            }

            LaunchedEffect(Unit) {
                nsdHelper = NsdHelper(
                    context = this@MainActivity,
                    onDeviceFound = { url ->
                        lastUrl = url
                        appState = AppState.Found(url)
                    },
                    onError = {
                        appState = AppState.Error("Dispositivo não encontrado. Verifique se você está na mesma rede Wi-Fi que o ESP32.")
                    }
                )

                // 1) Tenta o ultimo endereco (resposta em ~1,5 s no maximo)
                val saved = lastUrl
                if (saved != null && withContext(Dispatchers.IO) { Device.isReachable(saved) }) {
                    appState = AppState.Found(saved)
                } else {
                    // 2) Nao respondeu (IP mudou, outra rede...): procura na rede
                    nsdHelper.startDiscovery()
                }
            }

            ControleVentiladorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (val state = appState) {
                            is AppState.Searching -> LoadingScreen()
                            is AppState.Found -> {
                                WebViewScreen(state.url) {
                                    // A pagina nao carregou (placa mudou de IP?): procura de novo
                                    lastUrl = null
                                    search()
                                }
                                // Comando de voz dentro do painel: abre a tela de voz
                                // (desligado -> liga e pergunta o tempo; ligado -> pergunta se desliga)
                                FloatingActionButton(
                                    onClick = {
                                        startActivity(Intent(this@MainActivity, LigaActivity::class.java))
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(20.dp)
                                ) {
                                    Text("🎤", fontSize = 24.sp)
                                }
                            }
                            is AppState.Error -> ErrorScreen(state.message) { search() }
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
fun WebViewScreen(url: String, onLoadError: () -> Unit = {}) {
    val currentOnLoadError by rememberUpdatedState(onLoadError)
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
                
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        // So a pagina principal importa (ignora favicon etc.)
                        if (request.isForMainFrame) currentOnLoadError()
                    }
                }
                webChromeClient = WebChromeClient() // Permite diálogos JS e progresso
                
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
