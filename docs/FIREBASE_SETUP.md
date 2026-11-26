# Configuración de Firebase para SistemaControlAcceso

La integración con Firebase requiere el proyecto real del equipo. No subir el archivo google-services.json al repositorio público ni guardar claves privadas en el código fuente.

## Pasos pendientes

1. En Firebase Console, crear o abrir el proyecto del sistema.
2. Registrar una aplicación Android con el identificador com.example.sistemacontrolacceso.
3. Descargar google-services.json.
4. Guardarlo localmente en app/google-services.json (no compartirlo en un repositorio público).
5. Habilitar Firebase Authentication si se utilizará identidad de operador y elegir Firestore o Realtime Database para el historial.
6. Definir reglas de seguridad para que solo usuarios autorizados puedan leer y escribir registros.
7. Incorporar el plugin y las dependencias Firebase compatibles con las versiones Gradle del proyecto.
8. Probar lectura/escritura, modo sin conexión y reintento de sincronización.

## Cola local

WemosEventStore guarda las transiciones de estado en SharedPreferences y permite recuperar eventos pendientes. La cola se limita a 200 registros. Los eventos solo deben marcarse como sincronizados después de recibir confirmación de Firebase.

## Limitaciones de seguridad actuales

- SHA-256 en el firmware se utiliza como huella/checksum; no es HMAC y no autentica por sí solo el dispositivo.
- El HTTP local no cifra el tráfico. Debe reservarse para la red de laboratorio y añadirse autenticación/autorización antes de producción.
- El archivo google-services.json identifica la app Firebase, pero no reemplaza las reglas de seguridad del backend.
