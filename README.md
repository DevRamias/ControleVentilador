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

Nesta primeira versão, o app utiliza **WebView** como principal forma de interação, mantendo toda a lógica de controle no firmware do ESP32.

---

## 🛠️ Tecnologias utilizadas

- Kotlin
- Android Studio
- WebView
- mDNS
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
