# Matriz de verificación — TI3042 Unidad 3

Esta matriz relaciona la implementación del repositorio con los criterios de la guía. Los elementos marcados como pendientes requieren prueba real en hardware y Android.

| Criterio | Implementación / evidencia | Estado |
|---|---|---|
| 3.1.1 Envío seguro | Firebase RTDB sobre TLS y trama con secuencia, timestamp y checksum SHA-256 | Parcial: checksum no es firma |
| 3.1.2 Comunicación serial | Monitor Serial del firmware a 115200 baudios para diagnóstico | Parcial: datos app-Wemos viajan por Firebase/Wi-Fi, no UART |
| 3.1.3 Microcontrolador inalámbrico | Sketch Wemos ESP8266 que publica sensores y recibe comandos | Requiere prueba física |
| 3.1.4 Android conectado de forma segura | Google Sign-In + Firebase Authentication + reglas RTDB autenticadas | Requiere configurar Firebase y probar |

## Pruebas pendientes para la entrega

- Compilar desde Android Studio con JDK 17 y SDK 35.
- Probar Google Sign-In con SHA-1/SHA-256 del equipo.
- Verificar lectura real de distancia y luminosidad desde Wemos.
- Enviar comandos y observar la respuesta real del servo y LEDs.
- Desconectar/reconectar Wi-Fi y comprobar sincronización de comandos pendientes.
- Medir consumo energético y latencia en el montaje real.
- Restringir las reglas RTDB con roles para separar operador y dispositivo.
