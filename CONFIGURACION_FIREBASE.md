# Configuración Firebase, Google y Wemos

## Android Studio y Firebase Authentication

1. Abre Firebase Console y selecciona el proyecto `hola-b970e` (o el proyecto que realmente usarás).
2. Registra una app Android con el ID exacto `com.example.sistemacontrolacceso`.
3. Descarga `google-services.json` y colócalo en `app/google-services.json`.
4. En Authentication > Sign-in method, habilita Google.
5. Agrega a Firebase los SHA-1 y SHA-256 del certificado de depuración del equipo. Ejecuta `gradlew signingReport` para obtenerlos.
6. Copia `firebase.properties.example` a `firebase.properties` en la raíz y completa `google_web_client_id` con el OAuth Client ID de tipo Web.
7. Configura Realtime Database y publica las reglas de `firebase.database.rules.json`. No habilites lectura/escritura pública.

## Wemos ESP8266

1. En Authentication habilita también Email/Password y crea un usuario exclusivo para el Wemos.
2. En `firmware/WemosFirebase/WemosFirebase.ino` completa SSID, contraseña Wi-Fi, API key, URL RTDB y credenciales del dispositivo. No subas claves reales a Git.
3. En Arduino IDE agrega el índice `http://arduino.esp8266.com/stable/package_esp8266com_index.json`, instala ESP8266 Community y selecciona la placa Wemos/ESP8266 correcta.
4. Instala `Firebase ESP Client` y `Servo`. Ajusta los pines del sketch al cableado real.
5. Carga el sketch y abre el monitor serial a 115200 baudios.

## Abrir y ejecutar

1. Descomprime el repositorio y abre en Android Studio la carpeta que contiene `settings.gradle.kts`.
2. Usa JDK 17 y Android SDK 35; espera la sincronización de Gradle.
3. Verifica que estén configurados `app/google-services.json` y `firebase.properties`.
4. Ejecuta en un dispositivo o emulador con Google Play Services e Internet.
5. La pantalla inicial exige autenticación con Google antes de abrir los módulos de la aplicación.

## Estructura RTDB

- `/sensores/distancia`: distancia en centímetros.
- `/sensores/luz`: lectura ADC de la fotorresistencia.
- `/comandos/ultimo`: objeto con `seq`, `comando`, `payload`, `timestamp` y `checksum`.
- `/acceso`: estado de barrera, ángulo y marca de tiempo.

## Alcance y seguridad

Firebase Realtime Database usa HTTPS/TLS. La aplicación utiliza Firebase Authentication y escucha `.info/connected` para reflejar conectividad. El checksum SHA-256 de la trama detecta cambios accidentales, pero no es una firma criptográfica ni sustituye autorización por roles. Las reglas incluidas son un punto de partida: para producción separa permisos del operador y del dispositivo mediante roles/claims.

El firmware incluido es para Wemos/ESP8266, no para Arduino Uno sin módulo Wi-Fi. El tutorial de Notion sirve como guía de instalación/registro, pero las credenciales, reglas y el sketch completo deben configurarse y probarse por separado. No se afirma que la integración esté certificada ni que el prototipo sea apto para control de acceso crítico.
