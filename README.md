# ControleVentilador

Aplicativo Android desenvolvido em **Kotlin** para interação com um sistema embarcado baseado em **ESP32**.

⚠️ **Este aplicativo depende de um projeto externo de ESP32** que expõe um servidor HTTP local e uma interface HTML para controle do ventilador.  
Sem esse backend, o app não possui funcionalidade isolada.

---

## 📱 Visão geral

O ControleVentilador atua como um cliente Android que:

- Descobre um ESP32 na rede local via **mDNS (.local)**
- Abre automaticamente a interface Web servida pelo dispositivo
- Permite interação direta com os controles existentes
- Liga e desliga **por voz** ("Ok Google, abre ventilador")

O controle continua no firmware do ESP32: o app usa as mesmas rotas HTTP do painel (`/state`, `/start`, `/stop`).

---

## 🗣️ Comando de voz (v1.3)

O app instala **dois ícones**:

| Ícone | O que faz |
| --- | --- |
| **Ventilador** | Tela de voz. É a entrada padrão do app, então **"Ok Google, abre ventilador"** cai aqui. |
| **Painel** | Abre o painel da placa (WebView), sem ligar nada. Tem um botão 🎤 que abre a tela de voz. |

Na tela de voz:

- **Ventilador desligado:** liga na hora por **5 minutos** e pergunta *"Por quanto tempo você quer?"*. Entende respostas como "meia hora", "uma hora e meia", "1h30", "quarenta e cinco minutos". Sem resposta, fica em 5 minutos.
- **Ventilador ligado:** pergunta *"Quer desligar?"* ("sim", "pode", "desliga" / "não", "deixa").
- Também mostra botões (15 min, 30 min, 1 hora, 2 horas…), que valem mesmo enquanto ele está ouvindo.
- Fala o resultado e **fecha sozinha** em alguns segundos.

Dicas: diga **"abre** ventilador" (e não "liga o ventilador", que o Google trata como casa inteligente) e, se o app não falar, baixe a voz em português em *Configurações → Acessibilidade → Conversão de texto em voz*.

### Outras melhorias

- Busca da placa com tempo limite de 10 s e mensagem de erro (antes ficava em "Buscando..." para sempre).
- Guarda o último endereço da placa e abre direto; se a placa mudar de IP, procura de novo sozinho.

---

## 🧪 Testes e APK

```bash
./gradlew testDebugUnitTest   # testes do entendimento das respostas faladas (VoiceParserTest)
./gradlew assembleRelease     # APK em app/build/outputs/apk/release/app-release.apk
```

O APK de release é assinado com a chave de depuração do Android Studio: serve para instalar direto no celular. Para publicar na Play Store, é preciso uma chave própria.

---

## 🛠️ Tecnologias utilizadas

- Kotlin + Jetpack Compose
- Android Studio
- WebView
- mDNS (NsdManager)
- Reconhecimento de voz e TextToSpeech do Android
- ESP32
- HTTP local

---

## 📜 Licença

Distribuído sob a **MIT License**.  
Sinta-se à vontade para usar, modificar e distribuir, desde que mantenha os créditos.

---

## 👤 Autor

**Ramias Lopes**  
Criador e desenvolvedor deste projeto.

---
