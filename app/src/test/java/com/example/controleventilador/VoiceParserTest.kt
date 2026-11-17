package com.example.controleventilador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParserTest {

    private fun min(texto: String) = VoiceParser.parseMinutes(texto)

    @Test
    fun minutosComNumero() {
        assertEquals(30, min("30 minutos"))
        assertEquals(30, min("30"))
        assertEquals(45, min("por 45 min"))
        assertEquals(20, min("20min"))
        assertEquals(20, min("deixa 20 minutos por favor"))
    }

    @Test
    fun minutosPorExtenso() {
        assertEquals(5, min("cinco minutos"))
        assertEquals(10, min("dez"))
        assertEquals(15, min("quinze minutos"))
        assertEquals(45, min("quarenta e cinco minutos"))
        assertEquals(25, min("vinte e cinco"))
        assertEquals(120, min("cento e vinte minutos"))
    }

    @Test
    fun horas() {
        assertEquals(60, min("uma hora"))
        assertEquals(60, min("1 hora"))
        assertEquals(120, min("duas horas"))
        assertEquals(120, min("2h"))
        assertEquals(60, min("hora"))
    }

    @Test
    fun horasEMinutos() {
        assertEquals(90, min("uma hora e meia"))
        assertEquals(90, min("1 hora e 30"))
        assertEquals(90, min("1h30"))
        assertEquals(90, min("1:30"))
        assertEquals(135, min("duas horas e quinze minutos"))
        assertEquals(150, min("2 horas e 30 minutos"))
    }

    @Test
    fun meiaHora() {
        assertEquals(30, min("meia hora"))
        assertEquals(30, min("uma meia hora"))
        assertEquals(30, min("Meia hora, por favor"))
    }

    @Test
    fun acentosEMaiusculas() {
        assertEquals(3, min("TRÊS minutos"))
        assertEquals(180, min("três horas"))
    }

    @Test
    fun limiteDe24Horas() {
        assertEquals(VoiceParser.MAX_MINUTES, min("30 horas"))
    }

    @Test
    fun naoEntendeu() {
        assertNull(min("não"))
        assertNull(min("deixa assim"))
        assertNull(min(""))
        assertNull(min("zero minutos"))
    }

    @Test
    fun simOuNao() {
        assertEquals(true, VoiceParser.parseYesNo("sim"))
        assertEquals(true, VoiceParser.parseYesNo("Pode desligar"))
        assertEquals(true, VoiceParser.parseYesNo("desliga"))
        assertEquals(true, VoiceParser.parseYesNo("quero"))
        assertEquals(false, VoiceParser.parseYesNo("não"))
        assertEquals(false, VoiceParser.parseYesNo("Não quero"))
        assertEquals(false, VoiceParser.parseYesNo("deixa ligado"))
        assertEquals(false, VoiceParser.parseYesNo("mantém"))
        assertNull(VoiceParser.parseYesNo("talvez mais tarde"))
    }

    @Test
    fun descrever() {
        assertEquals("5 minutos", VoiceParser.descrever(5))
        assertEquals("1 minuto", VoiceParser.descrever(1))
        assertEquals("1 hora", VoiceParser.descrever(60))
        assertEquals("2 horas", VoiceParser.descrever(120))
        assertEquals("1 hora e 30 minutos", VoiceParser.descrever(90))
    }
}
