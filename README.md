# Sistema Control de Acceso OT / IoT - Android Native

![Android Native](https://img.shields.io/badge/Android-Kotlin-green.svg)
![Security Standard](https://img.shields.io/badge/ISO%2FIEC-27001%20%7C%2027400-blue.svg)
![Wireless](https://img.shields.io/badge/IoT-Bluetooth%20%7C%20Wi--Fi%20%7C%20MQTT-orange.svg)
![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)

---

## 📌 1. Identificación de la Problemática y Dispositivos

En entornos industriales, mineros o de plantas de energía (entornos **OT - Operational Technology**), los puntos de control de acceso a zonas de riesgo presentan baja visibilidad ("zonas oscuras") o condiciones ambientales adversas. Esto genera la necesidad de automatizar la apertura de barreras vehiculares y peatonales mediante la fusión de sensores de aproximación y luminosidad, garantizando la seguridad operacional y la protección de datos e infraestructura según normativas internacionales.

### 🤖 Dispositivos e Infraestructura IoT a Conectar:
1. **Sensor Ultrasónico HC-SR04:** Mide la distancia de aproximación de vehículos o personas (rango 0 - 100 cm).
2. **Sensor Fotorresistencia LDR:** Detecta niveles de luz ambiente (0 - 1023 ADC) para evaluar condiciones de oscuridad.
3. **Actuador Servomotor:** Controla la apertura (90°) y cierre (0°) de la barrera física.
4. **Señalización LED Indicativa:** LED Verde (Acceso Concedido / Abierto) y LED Rojo (Acceso Denegado / En Reposo).
5. **Switch de Anulación de Emergencia (OT Safety Switch):** Dispositivo de interrupción física/lógica ante contingencias.
6. **Módulo de Comunicación Inalámbrica:** Microcontroladores ESP32 / módulos Bluetooth HC-05 / Sockets Wi-Fi UDP / Broker MQTT Industrial.
7. **Dispositivo Móvil Android:** Estación de control y monitoreo en tiempo real operada por personal autorizado.

---

## ⚙️ 2. Funcionalidades Clave de la Aplicación

1. **Monitoreo en Tiempo Real (Panel de Entradas):** Lectura interactiva de sensores (distancia y luz), estado de barrera, ángulo del servomotor e indicadores LED de señalización.
2. **Control y Activador de Actuadores:**
   * **Modos de Operación:** Automático (Lógica de sensores), Manual Forzado Abierto (90°) y Manual Forzado Cerrado (0°).
   * **Anulación de Emergencia OT:** Switch de alta prioridad que inhabilita aperturas no autorizadas.
   * **Calibración Dinámica de Umbrales:** Ajuste deslizante en vivo para umbrales de distancia (cm) y luz (ADC).
   * **Envío de Comandos Inalámbricos:** Transmisión de *payloads* personalizados hacia otros nodos IoT.
3. **Gestor de Comunicaciones Inalámbricas:**
   * Selección en tiempo real de **Bluetooth HC-05**, **Wi-Fi UDP Sockets**, **MQTT Broker IoT** e **InterApp Mesh**.
   * Panel de métricas de rendimiento de red: **Latencia (ms)**, **Pérdida de Paquetes (%)**, **Paquetes Enviados/Recibidos** y **Potencia RSSI (dBm)**.
   * Mecanismo de **Heartbeat (Keep-Alive)** cada 3 segundos y **Checksum Verification** de tramas.
4. **Seguridad Normativa ISO 27400 e ISO/IEC 27001:**
   * Cifrado de credenciales mediante algoritmo **Hash SHA-256**.
   * Prevención de ataques de fuerza bruta con **bloqueo temporal de 30 segundos tras 3 intentos fallidos consecutivos**.
   * **Registro inmutable de Auditoría OT (Audit Trail)** con marcas de tiempo en formato ISO 8601.
5. **Servicios de Contexto y Notificaciones:**
   * Geolocalización GPS del punto de control en tiempo real.
   * Sincronización con el reloj digital del sistema.
   * Sistema de notificaciones push locales con canal de alta prioridad (vibración y alertas sonoras).
6. **Ajustes de UI/UX:**
   * Soporte para **Modo Nocturno y Modo Diurno**.
   * Soporte multi-idioma (**Español e Inglés**).

---

## 🛠️ 3. Justificación de Herramientas de Desarrollo

* **Kotlin:** Lenguaje oficial para Android recomendado por Google. Ofrece seguridad contra nulos (*Null Safety*), concisión en el código y alto rendimiento.
* **Android Studio & Gradle Version Catalog (`libs.versions.toml`):** Entorno de desarrollo líder con gestión centralizada de dependencias y compilación optimizada.
* **Material Design 3 Components:** Proporciona componentes visuales estandarizados (`MaterialCardView`, `MaterialButton`, `SwitchCompat`, etc.) que garantizan usabilidad y consistencia.
* **Android Jetpack Libraries (`AppCompat`, `Core-KTX`, `Activity-KTX`):** Aseguran compatibilidad hacia atrás en un rango amplio de dispositivos Android (minSdk 24 a targetSdk 37).

---

## 📡 4. Selección y Justificación de Comunicaciones Inalámbricas

| Método de Comunicación | Distancia / Alcance | Consumo de Energía | Estabilidad y Resiliencia | Escenario de Uso Recomendado en OT |
| :--- | :--- | :--- | :--- | :--- |
| **Bluetooth (HC-05 / BLE)** | Corto (10 metros) | **Muy Bajo** | Alta en enlace directo punto a punto | Mantenimiento local e inspección directa al pie de la barrera por el operador. |
| **Wi-Fi UDP Socket** | Mediano (50 - 100 metros) | Moderado | Alta velocidad / Latencia ultra baja (<20ms) | Control en tiempo real dentro de la red LAN de la planta industrial. |
| **MQTT Broker (Pub/Sub)** | Ilimitado (LAN / Cloud) | **Bajo / Optimizado** | **Máxima** (Soporta reconexión automática y QoS 1) | Monitoreo remoto y telemetría centralizada en servidor IoT / SCADA. |
| **Red Mesh Inter-App** | Adaptable por saltos | Distribuido | Alta resiliencia sin punto único de falla | Comunicación entre múltiples dispositivos móviles de guardias en terreno. |

---

## 🔐 5. Medidas de Seguridad ISO 27400 e ISO/IEC 27001

1. **Protección de Credenciales:** Las contraseñas ingresadas nunca se almacenan ni transmiten en texto plano. Se procesan mediante **SHA-256** (`SecurityManager.sha256()`).
2. **Control de Acceso y Rate Limiting:**
   * Máximo de 3 intentos fallidos de autenticación.
   * Al sobrepasar el límite, la cuenta queda bloqueada por 30 segundos, notificando al operador y registrando el evento en auditoría.
3. **Integridad de Datos Inalámbricos:** Cada trama enviada incluye un hash de verificación **Checksum de 8 caracteres** calculado como `SHA256(payload + "_OT_KEY_2026").take(8)`.
4. **Audit Log Inmutable:** Todos los eventos de seguridad (logins, fallos, bloqueos, cambios de estado de emergencia, comandos enviados) se registran con marcas de tiempo en formato `yyyy-MM-dd HH:mm:ss`.

---

## 🧪 6. Pruebas de Funcionamiento Automatizadas

El proyecto cuenta con una suite de pruebas unitarias automatizadas escritas en JUnit que certifican la calidad del código:

```bash
# Ejecución de pruebas unitarias en Gradle
./gradlew app:testDebugUnitTest
```

* **`testSha256Hashing`:** Valida que el algoritmo de hash SHA-256 genere hashes de exactamente 64 caracteres hexadecimales.
* **`testFrameChecksumCalculation`:** Certifica que el checksum de las tramas inalámbricas sea correcto de 8 caracteres en mayúsculas.
* **`testAuditLogging`:** Verifica el almacenamiento inmutable de eventos de auditoría con marcas de tiempo.
* **`testTechTypeComparisonInfo`:** Comprueba la generación de la justificación técnica de red para Bluetooth, Wi-Fi, MQTT y Mesh.
* **`testDisconnectedStateCannotSendFrame`:** Garantiza la validación de estado antes de permitir el envío de comandos inalámbricos.

---

## 📋 7. Guía de Presentación Oral y Defensa

### 🎙️ Estructura del Pitch de Presentación (5 Minutos):
1. **Introducción (1 min):** Presentar la problemática industrial OT (control de acceso en zonas oscuras) y los dispositivos conectados (ultrasonido, LDR, servomotor, leds, ESP32/Bluetooth/Wi-Fi/MQTT).
2. **Demostración en Vivo (2 min):**
   * Iniciar sesión en `LoginActivity` (mostrar hash SHA-256 y probar la política de bloqueo al colocar 3 contraseñas erróneas).
   * Monitorear sensores en `MainActivity` moviendo los deslizadores de distancia y luz. Observar el cambio de estado de la barrera (servo 0° a 90° y leds).
   * Cambiar a la pestaña de **Activador**, probar el switch de emergencia y la calibración dinámica de umbrales.
   * Cambiar a la pestaña de **Conexión Inalámbrica**, seleccionar **MQTT Broker** / **Bluetooth**, presionar CONECTAR y observar las métricas de latencia, pérdidas y tramas enviadas con Checksum.
   * Mostrar el registro de auditoría haciendo clic en el botón `AUDITORÍA ISO 27001`.
3. **Justificación Tecnológica y Seguridad (1 min):** Explicar por qué se eligió Kotlin, Material 3, la diferencia entre Bluetooth/Wi-Fi/MQTT y las medidas de la norma ISO 27400 / 27001.
4. **Conclusión y Preguntas (1 min).**

### ❓ Posibles Preguntas del Docente y Respuestas Preparadas:

* **P1: ¿Por qué eligieron SHA-256 para las credenciales y no MD5?**
  * **R:** MD5 está matemáticamente vulnerado por colisiones desde hace años. La norma ISO/IEC 27001 exige algoritmos criptográficos robustos. SHA-256 genera una huella de 256 bits inmune a colisiones conocidas, cumpliendo los estándares de seguridad industrial.
* **P2: ¿Cómo maneja la app el consumo de batería al conectarse por Wi-Fi o Bluetooth?**
  * **R:** Para inspecciones directas a corta distancia se recomienda **Bluetooth LE/HC-05** por su consumo mínimo. Para comunicación en tiempo real en planta se utiliza **Wi-Fi UDP** debido a su latencia reducida. Y para monitoreo en segundo plano se emplea **MQTT**, ya que su arquitectura Publish/Subscribe y encabezados de pocos bytes optimizan tanto el consumo de datos como de batería.
* **P3: ¿Qué ocurre si un atacante intenta enviar tramas falsas a la barrera?**
  * **R:** La aplicación implementa verificación de integridad basada en la norma ISO 27400. Cada trama contiene un número de secuencia creciente, timestamp y un **Checksum de 8 caracteres** derivado de una clave secreta OT. Si una trama es alterada o reinyectada, el checksum no coincidirá y el receptor descartará el comando.
* **P4: ¿Cómo garantiza la aplicación la usabilidad del usuario en la zona oscura?**
  * **R:** La UI cuenta con un selector de **Tema Nocturno con alto contraste** (colores oscuros `#0F172A`, leds brillantes y textos en blanco `#F8FAFC`), evitando el deslumbramiento del operador en la noche y mejorando la legibilidad.
