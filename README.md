<<<<<<< HEAD
# Sistema de Control de Acceso OT/IoT

**Prototipo académico de aplicación Android nativa para monitoreo y control de accesos en entornos operacionales.**

**Desarrolladores:** Vicente Vega Arroyo · Cristobal Jara · **Formación:** Analista Programador, INACAP Osorno  
**Código:** Kotlin · Android Studio · interfaz Android en XML  
**Repositorio:** https://github.com/Vichosurf/SistemaControlAcceso

> **Estado de la evidencia (21/10/2026).** La documentación reúne capturas reales de la aplicación Android, una fotografía del circuito de prueba y un video breve del montaje con Arduino encendido e indicadores LED cambiando de estado. Estas evidencias muestran una **demostración física de laboratorio**, pero no bastan para afirmar que la app Android se comunica directamente con el Arduino, que el servomotor abre una barrera ni que los sensores tienen una lectura calibrada. No se ha realizado auditoría de código ni certificación de seguridad.

## 1. El problema

Las operaciones de control de accesos en instalaciones técnicas requieren conocer el estado de una barrera, observar variables del entorno y disponer de controles manuales y registros de eventos. Este prototipo explora cómo centralizar esas interacciones en una aplicación móvil, especialmente para escenarios de baja luminosidad.

## 2. La solución

Desarrollé un prototipo Android con pantallas para autenticación, visualización de variables, configuración de umbrales, selección de modos de operación, comunicaciones y consulta de eventos operacionales.

La interfaz permite representar el estado de una barrera (0° cerrada / 90° abierta), la lectura o simulación de distancia y luminosidad y el estado de una conexión inalámbrica. Como complemento, monté un **circuito de pruebas sobre protoboard con Arduino Uno**, un sensor ultrasónico, indicadores LED y un servomotor en el montaje de demostración. En el video se observa la placa alimentada y un cambio visible de estado de los indicadores. **Todavía está pendiente documentar y comprobar la comunicación extremo a extremo entre la aplicación Android y el montaje físico.**

## 3. Capturas del proyecto

| Inicio de sesión | Monitoreo de variables | Control del actuador |
|:--:|:--:|:--:|
| ![Inicio de sesión](docs/capturas/01_login.png) | ![Monitoreo y estados](docs/capturas/02_monitoreo.png) | ![Modos y umbrales](docs/capturas/03_activador.png) |

| Conexión inalámbrica | Registro de eventos | Configuración |
|:--:|:--:|:--:|
| ![Panel de conexión](docs/capturas/04_conexion.png) | ![Registro de auditoría](docs/capturas/05_auditoria.png) | ![Temas e idioma](docs/capturas/06_configuracion.png) |

*Capturas de una ejecución de demostración. Se ocultaron credenciales de ejemplo, coordenadas e información de conexión para evitar divulgación accidental.*

## 4. Prototipo físico: fotografía y video de demostración

![Montaje de prueba con Arduino Uno, protoboard, módulo ultrasónico, LEDs y servomotor](docs/evidencia/montaje_fisico.jpg)

**[▶ Ver demostración del montaje físico en video (MP4, aproximadamente 14 segundos)](docs/evidencia/demo_hardware.mp4)**

La evidencia complementaria permite identificar visualmente:

- **Arduino Uno conectado por USB** al computador y con LEDs indicadores encendidos en la placa.
- **Protoboard con cableado de prueba**, un módulo ultrasónico de dos transductores (tipo HC-SR04), LEDs de indicación y otros componentes.
- **Servomotor compacto de carcasa azul**, visible con cableado en la fotografía; su movimiento **no queda acreditado** por los fotogramas revisados del video.
- **Variación visible de los LEDs** durante la grabación: el montaje no es únicamente una representación gráfica en el teléfono.

**Alcance de la demostración:** la fotografía confirma la existencia de un montaje de laboratorio y el video muestra indicadores físicos activos. No permite por sí solo atribuir al sistema móvil el control del circuito, demostrar lecturas de distancia calibradas, comprobar respuesta del servomotor ni medir latencia real de la integración. Para acreditar esas funciones se requiere una prueba filmada de extremo a extremo y la identificación del firmware, puertos, protocolo y comandos utilizados.

| Evidencia disponible | Se observa directamente | Verificación pendiente |
|---|---|---|
| Fotografía física | Arduino Uno, protoboard, módulo ultrasónico, servomotor y LEDs | Diagrama eléctrico, pines usados y conexiones funcionales |
| Video físico de ~14 s | Arduino alimentado y cambio visible en indicadores LED | Origen de la señal, estados programados y pruebas repetibles |
| Capturas Android | Interfaz de monitoreo, activación, conectividad y auditoría | Que esos eventos y controles correspondan al circuito físico mostrado |

## 5. Funcionalidades observables y alcance

| Módulo | Comportamiento mostrado | Qué falta acreditar |
|---|---|---|
| Acceso | Formulario de usuario y contraseña; acceso a configuración | Revisión del esquema real de autenticación y almacenamiento de credenciales |
| Monitoreo | Interfaz de distancia ultrasónica (ejemplo: 50 cm), luminosidad LDR (500), estado de barrera y LEDs; además, se observa un módulo ultrasónico físico en el prototipo | Verificar si los valores de la app provienen del sensor físico; pueden estar simulados |
| Activador | Interfaz con automático, forzar apertura 90°, forzar cierre 0°, anulación de emergencia y umbrales editables; servomotor físico visible en fotografía | Demostrar movimiento físico del servo y vinculación con comandos Android; pruebas de interbloqueos y estados de fallo |
| Comunicaciones | Selectores Bluetooth HC-05/ESP32, Wi-Fi/UDP e inter-app; latencia, pérdida de paquetes, RSSI y tramas | Identificar qué protocolos intercambian datos reales y cuáles son simulaciones |
| Registro de eventos | Marcas de tiempo, eventos de conexión, comandos, checksums y latencias | Integridad resistente a manipulación, retención y exportación de logs |
| Preferencias | Tema oscuro/claro e idiomas español/inglés | Validar cobertura completa de textos e interfaces |
| GPS y reloj | Panel de ubicación y hora del sistema | Permisos, tratamiento de datos y comportamiento sin señal |

### Evidencia visible de una sesión

En las capturas se observan, **a modo de ejemplo de interfaz**, una conexión `INTERAPP_MESH`, un mensaje `PING_HEARTBEAT`, latencia indicada de 20 ms, pérdida de paquetes indicada de 0,0 %, y un registro de eventos. Estos valores son **una fotografía de la sesión**, no estadísticas de rendimiento validadas.

## 6. Arquitectura conceptual

```mermaid
flowchart TD
    U[Operador] --> UI[Aplicación Android / Kotlin]
    UI --> A[Autenticación y preferencias]
    UI --> M[Monitoreo de variables]
    UI --> C[Control automático y manual]
    UI --> N[Panel de comunicaciones]
    UI --> L[Registro local de eventos]
    M --> S[Entradas de distancia y luz\nSimulación / interfaz]
    C --> B[Estado lógico de barrera\n0° / 90°]
    N --> T[Bluetooth / UDP / Inter-app\nvalidar integración real]
    UI --> G[GPS y hora del dispositivo]
```

Este diagrama describe **componentes conceptuales de la app observables o reportados** y no pretende representar clases exactas ni un backend desplegado. El montaje experimental con Arduino mostrado arriba se documenta **por separado**, ya que la conexión entre el circuito físico y la app todavía requiere evidencia directa. La integración con Python/FastAPI se considera una posible evolución, **no una característica terminada**.

## 7. Decisiones de diseño

- **Android nativo con Kotlin:** acceso a componentes del sistema operativo, interfaz móvil y funciones del dispositivo.
- **Separación por pestañas:** Monitoreo, Activador y Conexión, para que el operador distinga observación, control y comunicaciones.
- **Umbrales configurables:** permite experimentar con diferentes condiciones de distancia e iluminación.
- **Operación automática y manual:** ofrece distintos modos para observar y demostrar la lógica de control.
- **Tema oscuro y personalización:** se adapta visualmente a condiciones de uso diferentes; esto no sustituye una evaluación formal de accesibilidad.
- **Registro de eventos:** permite seguir las acciones presentadas por la aplicación y depurar su comportamiento.
- **Prototipado con hardware accesible:** uso de Arduino Uno y protoboard para ensayar físicamente señales de entrada/salida; el alcance de cada componente se valida en pruebas reproducibles.

## 8. Consideraciones de seguridad

El proyecto menciona **ISO/IEC 27001, ISO/IEC 27400 e IEC 62443 como referentes de diseño**. No afirmo certificación, conformidad normativa ni aprobación para un entorno OT de producción.

Aspectos que deben revisarse antes de representar la aplicación como segura:

1. **Contraseñas:** un hash SHA-256 directo no es, por sí solo, un mecanismo suficiente de almacenamiento de contraseñas; valorar una función específica de derivación de contraseñas y almacenamiento seguro.
2. **Checksums:** ayudan a detectar ciertos errores de transmisión, pero no demuestran autenticidad criptográfica ni protección ante ataques.
3. **Auditoría:** registrar eventos no garantiza que un archivo local sea inmutable o a prueba de alteraciones.
4. **Comunicación:** verificar cifrado, autenticación del extremo, autorización de comandos, reconexión y límites de confianza.
5. **Control físico:** para una eventual integración real se necesitan enclavamientos, fallos seguros y revisión especializada independiente de la app.

**Uso previsto de esta versión:** demostración académica; no utilizar para operar barreras, instalaciones industriales ni equipos de seguridad críticos.

## 9. Pruebas sugeridas para documentar resultados

| Caso | Condición | Evidencia esperada | Estado |
|---|---|---|---|
| T-01 | Usuario válido / inválido | Captura y resultado del intento | Pendiente de ficha de prueba |
| T-02 | Distancia inferior al umbral | Estado lógico esperado de la barrera | Pendiente |
| T-03 | Luminosidad inferior al umbral | Estado del indicador y decisión | Pendiente |
| T-04 | Forzar apertura y cierre | Transición 0° / 90° y registro | Pendiente |
| T-05 | Anulación de emergencia | Comportamiento consistente y seguro | Pendiente |
| T-06 | Conectar / desconectar | Estado, errores y reintentos | Pendiente |
| T-07 | Cambiar tema e idioma | Persistencia y traducción en pantallas | Pendiente |
| T-08 | Permisos de ubicación | Funcionamiento con permiso denegado | Pendiente |
| T-09 | Alimentación y estado del circuito | Fotografía y video de Arduino encendido | Evidencia visual disponible; falta protocolo de prueba |
| T-10 | Salidas LED en montaje físico | Video de cambio de estado y registro de condiciones de entrada | Cambio visual observado; falta correlación con comandos |
| T-11 | Sensor ultrasónico físico | Registrar distancia medida frente a distancia real | Montaje fotografiado; medición sin validar |
| T-12 | Servomotor físico | Registrar apertura/cierre con ángulos y condiciones de seguridad | Componente fotografiado; movimiento sin validar |
| T-13 | Integración Android ↔ Arduino | Secuencia filmada: orden desde la app → comunicación → respuesta física → log | Pendiente de prueba integral |

> La evidencia visual del montaje **no equivale a aprobar una prueba funcional de extremo a extremo**. Los casos anteriores deben contar con versión, dispositivo, condición inicial, estímulo, resultado esperado, resultado real y evidencia reproducible antes de marcarse como superados.

## 10. Abrir el proyecto

1. Instalar Android Studio y el SDK compatible con los archivos Gradle del repositorio.
2. Clonar el repositorio:

   ```bash
   git clone https://github.com/Vichosurf/SistemaControlAcceso.git
   ```
3. Abrir la carpeta clonada en Android Studio.
4. Esperar la sincronización de Gradle; revisar los requisitos que indique el proyecto.
5. Seleccionar un emulador o teléfono Android compatible y ejecutar la configuración de la aplicación.

**Importante:** el autor debe comprobar la rama estable antes de publicar esta guía: se mencionaron `main` y `Copy-Main`. No se especifican requisitos mínimos de Android o comandos de compilación concretos sin inspeccionar los archivos del repositorio.

## 11. Próximos pasos

- Publicar resultados verificables de las pruebas, con fecha, versión y dispositivo.
- Diferenciar en un anexo las funciones simuladas de las integraciones con hardware verificadas.
- Incorporar el código/firmware de Arduino, diagrama de conexión y tabla de pines, siempre que Vicente confirme que son parte del proyecto.
- Grabar una demostración integral en una sola toma: abrir la app, cambiar un control, observar el efecto físico y revisar el log.
- Revisar manejo de credenciales, registro de eventos y seguridad del transporte.
- Documentar la estructura de paquetes y clases a partir del código real.
- Mantener el video de montaje actualizado; publicar posteriormente una prueba de integración con la app si se confirma, y si corresponde un APK de demostración sin secretos.
- Evaluar integración futura con un servicio backend, **sin presentarla como ya desarrollada**.

## 12. Autoría y contexto

**Vicente Vega Arroyo**
**Cristobal Jara**
Estudiantes de Analista Programador, INACAP Osorno.  
Proyecto académico de desarrollo móvil y control operacional.  
[Perfil de GitHub](https://github.com/Vichosurf)
=======
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
>>>>>>> 57e08d3 (refactor: clean up README formatting, add MQTT support, unit tests, and security enhancements)
