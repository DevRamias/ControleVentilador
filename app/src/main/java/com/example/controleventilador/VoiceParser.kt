package com.example.controleventilador

import java.text.Normalizer

/**
 * Entende as respostas faladas (em portugues) do fluxo "Liga":
 *  - [parseMinutes]: "meia hora", "1 hora e 30", "quarenta e cinco minutos", "2h" -> minutos
 *  - [parseYesNo]:   "sim, pode desligar" -> true; "nao, deixa ligado" -> false
 *
 * E codigo puro (sem Android), por isso tem testes unitarios em VoiceParserTest.
 */
object VoiceParser {

    /** Limite do firmware para /start (24 h). */
    const val MAX_MINUTES = 1440

    private val UNIDADES = mapOf(
        "zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3,
        "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9,
        "dez" to 10, "onze" to 11, "doze" to 12, "treze" to 13, "catorze" to 14,
        "quatorze" to 14, "quinze" to 15, "dezesseis" to 16, "dezessete" to 17,
        "dezoito" to 18, "dezenove" to 19
    )
    private val DEZENAS = mapOf(
        "vinte" to 20, "trinta" to 30, "quarenta" to 40, "cinquenta" to 50,
        "sessenta" to 60, "setenta" to 70, "oitenta" to 80, "noventa" to 90,
        "cem" to 100, "cento" to 100
    )
    private val HORA = setOf("hora", "horas", "h", "hr", "hrs")
    private val MINUTO = setOf("minuto", "minutos", "min", "mins", "m")

    private val SIM = setOf(
        "sim", "pode", "desliga", "desligar", "desligue", "quero", "claro", "isso",
        "ok", "okay", "confirmo", "confirma", "beleza", "afirmativo", "por favor"
    )
    private val NAO = setOf(
        "nao", "deixa", "deixe", "mantem", "manter", "mantenha", "continua",
        "continue", "negativo", "nunca", "cancela", "cancelar"
    )

    /** Minutos pedidos na frase, ou null se nao entendeu. Limitado a 1..1440. */
    fun parseMinutes(texto: String): Int? {
        var t = normalizar(texto)
        // "1h30", "1h 30", "1:30" -> "1 hora 30 minutos"
        t = Regex("""(\d+)\s*(?:h|:)\s*(\d{1,2})\b""").replace(t) { m ->
            "${m.groupValues[1]} hora ${m.groupValues[2]} minutos"
        }
        // "30min", "2h" -> separa numero e unidade
        t = Regex("""(\d+)(h|hr|hrs|min|mins|m)\b""").replace(t) { m ->
            "${m.groupValues[1]} ${m.groupValues[2]}"
        }

        val tokens = juntarNumeros(t.split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() })

        var total = 0
        var achou = false
        var pendente: Int? = null // numero ainda sem unidade
        var i = 0
        while (i < tokens.size) {
            val tk = tokens[i]
            val n = tk.toIntOrNull()
            when {
                n != null -> {
                    if (pendente != null) total += pendente // dois numeros seguidos: o 1o vira minutos
                    pendente = n
                    achou = true
                }
                tk in HORA -> {
                    total += (pendente ?: 1) * 60 // "hora" sozinha = 1 hora
                    pendente = null
                    achou = true
                    // "hora e meia"
                    if (tokens.getOrNull(i + 1) == "e" && tokens.getOrNull(i + 2) == "meia") {
                        total += 30
                        i += 2
                    }
                }
                tk in MINUTO -> {
                    if (pendente != null) {
                        total += pendente
                        pendente = null
                        achou = true
                    }
                }
                tk == "meia" && tokens.getOrNull(i + 1) in HORA -> {
                    total += 30 // "meia hora"
                    pendente = null // "uma meia hora": o "uma" e so artigo
                    achou = true
                    i++
                }
            }
            i++
        }
        if (pendente != null) total += pendente // "30" sozinho = 30 minutos
        if (!achou || total <= 0) return null
        return total.coerceAtMost(MAX_MINUTES)
    }

    /** true = sim, false = nao, null = nao entendeu. "Nao" ganha de "sim" ("nao quero"). */
    fun parseYesNo(texto: String): Boolean? {
        val t = " ${normalizar(texto).replace(Regex("""[^a-z ]"""), " ")} "
        if (NAO.any { t.contains(" $it ") }) return false
        if (SIM.any { t.contains(" $it ") }) return true
        return null
    }

    /** "90" -> "1 hora e 30 minutos" (para falar e mostrar). */
    fun descrever(minutos: Int): String {
        val h = minutos / 60
        val m = minutos % 60
        val horas = if (h == 1) "1 hora" else "$h horas"
        val mins = if (m == 1) "1 minuto" else "$m minutos"
        return when {
            h == 0 -> mins
            m == 0 -> horas
            else -> "$horas e $mins"
        }
    }

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "") // tira acentos

    /** Troca palavras por numeros: "vinte e cinco" -> "25", "uma" -> "1". */
    private fun juntarNumeros(tokens: List<String>): List<String> {
        val saida = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val tk = tokens[i]
            val dezena = DEZENAS[tk]
            val unidade = UNIDADES[tk]
            when {
                dezena != null -> {
                    var valor = dezena
                    // "vinte e cinco", "cento e vinte"
                    if (tokens.getOrNull(i + 1) == "e") {
                        val prox = tokens.getOrNull(i + 2)
                        val d2 = DEZENAS[prox]
                        val u2 = UNIDADES[prox]
                        if (d2 != null && d2 < dezena) {
                            valor += d2
                            i += 2
                            if (tokens.getOrNull(i + 1) == "e") {
                                UNIDADES[tokens.getOrNull(i + 2)]?.let { valor += it; i += 2 }
                            }
                        } else if (u2 != null && u2 < 10) {
                            valor += u2
                            i += 2
                        }
                    }
                    saida += valor.toString()
                }
                unidade != null -> saida += unidade.toString()
                else -> saida += tk
            }
            i++
        }
        return saida
    }
}
