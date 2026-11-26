# Integración Firebase / Google / Wemos

Este repositorio incorpora el flujo de autenticación Google-Firebase en Android y la comunicación de la aplicación con un Wemos ESP8266 a través de Firebase Realtime Database.

## Archivos relevantes
- `app/google-services.json`: descargar desde la configuración de la app Android en Firebase Console; no se incluye en Git.
- `firebase.properties`: copiar desde `firebase.properties.example` y completar el OAuth Client ID Web; no se incluye en Git.
- `firebase.database.rules.json`: reglas iniciales que exigen usuario autenticado.
- `firmware/WemosFirebase/WemosFirebase.ino`: puente Wi-Fi/Firebase para Wemos ESP8266.
- `firmware/ArduinoUno/ArduinoUnoControlAcceso.ino`: control de sensores, LEDs y servo en Arduino Uno.

## Flujo de ejecución
1. Firebase Console: registrar el paquete `com.example.sistemacontrolacceso`.
2. Habilitar Google en Authentication y registrar SHA-1/SHA-256 de depuración.
3. Habilitar Realtime Database y publicar las reglas.
4. Crear un usuario de dispositivo con Email/Password para el Wemos.
5. Configurar los dos archivos locales de Firebase.
6. Abrir en Android Studio la carpeta raíz (donde está `settings.gradle.kts`), usar JDK 17 y SDK 35, sincronizar y ejecutar.
7. Iniciar sesión con Google; solo después se abre MainActivity.
8. Cargar `ArduinoUnoControlAcceso.ino` en Arduino Uno.
9. Cargar `WemosFirebase.ino` en el Wemos con credenciales locales.
10. Cablear UART entre ambas placas con divisor resistivo en la línea Arduino TX -> Wemos RX y GND común.
11. Comprobar telemetría y órdenes en RTDB.

## Esquema de datos
- `/sensores/distancia` (cm)
- `/sensores/luz` (ADC)
- `/comandos/ultimo`: `seq`, `comando`, `payload`, `timestamp`, `checksum`
- `/acceso`: `barreraAbierta`, `anguloServo`, `actualizadoEn`

## Advertencias
El sketch es una base de integración que requiere validación con el cableado y placa exactos. El montaje documentado con Arduino Uno no equivale a un Wemos ESP8266; Arduino Uno necesita un módulo Wi-Fi compatible o se debe cambiar el microcontrolador. El Arduino Uno controla sensores y salidas; el Wemos es el puente Wi-Fi/Firebase y convierte órdenes RTDB a tramas UART. El checksum sirve para detectar alteraciones accidentales y no reemplaza firmas, roles ni reglas de autorización. Los pines publicados son una propuesta y deben cotejarse con el cableado físico. Las reglas iniciales permiten lectura/escritura a cualquier usuario autenticado: deben endurecerse antes de un despliegue real, separando permisos de operador y dispositivo. La guía de la asignatura también pide validar la comunicación serial, la reconexión, consumo energético e integridad mediante pruebas físicas.
