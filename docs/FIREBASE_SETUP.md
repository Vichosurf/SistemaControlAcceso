# Configuración Firebase — SistemaControlAcceso

## Servicios usados

- Firebase Authentication con proveedor anónimo para identificar la sesión de la instalación.
- Cloud Firestore para guardar eventos de apertura/cierre del WeMos.
- Crashlytics para recopilar informes de fallos Android.

## Registrar la aplicación

1. Abre Firebase Console y crea o selecciona el proyecto.
2. Registra una app Android con el identificador exacto: com.example.sistemacontrolacceso.
3. Descarga el archivo google-services.json.
4. Colócalo en app/google-services.json. Está excluido de Git por seguridad.
5. En Authentication > Sign-in method, habilita Anonymous.
6. Crea una base de datos Cloud Firestore.
7. En Crashlytics, habilita el servicio y ejecuta una compilación de prueba para validar el envío de informes.

Los plugins de Google Services y Crashlytics quedan aplicados en app/build.gradle.kts. El proyecto fija Kotlin Gradle Plugin 2.4.21 para mantener compatibilidad de metadatos con Firebase Auth 25.x. El archivo google-services.json se incorpora en app/google-services.json y debe corresponder al applicationId exacto. Firebase indica que este archivo contiene identificadores no secretos; de todos modos, restringe la API key desde Google Cloud Console y nunca agregues claves de cuentas de servicio o credenciales privadas al cliente Android.

## Estructura de Firestore

Colección: control_acceso_eventos

Cada documento usa el UUID generado localmente como ID e incluye:
- id
- barrera (abierta/cerrada)
- evento (motivo reportado por el firmware)
- distancia_cm
- lectura_ldr
- operador
- fecha_local
- fecha_servidor
- origen
- uid

## Reglas de Firestore

El archivo firestore.rules limita las escrituras a usuarios autenticados y valida:
- El UID del documento debe coincidir con el UID de Firebase Authentication.
- El identificador del documento debe coincidir con el ID del evento.
- Solo se aceptan los campos previstos, tipos de datos y estados de barrera válidos.
- No se permite lectura ni eliminación desde la aplicación cliente.

La regla permite actualizar únicamente el documento del mismo usuario para que un reintento idempotente de Firestore no duplique eventos. El despliegue sigue pendiente de autorización del propietario del proyecto.

## Funcionamiento Offline

1. Los cambios reales de estado se guardan primero en SharedPreferences.
2. La app intenta iniciar sesión anónima si no existe sesión Firebase.
3. Se envía un batch a Firestore usando los UUID locales como IDs de documento.
4. Solo después de la confirmación del batch se eliminan los eventos de la cola local.
5. Si falla Wi-Fi, autenticación o Firestore, los eventos permanecen en el dispositivo para el siguiente reintento.

La cola local tiene un máximo de 200 eventos. La consulta al WeMos se realiza mientras MainActivity está activa; la sincronización no es un servicio persistente de fondo.

## Seguridad

- No publicar google-services.json en un repositorio público.
- No almacenar claves privadas de cuentas de servicio en Android.
- SHA-256 del firmware es un checksum y no autentica al dispositivo.
- El enlace HTTP local con el WeMos no está cifrado; usarlo solo en una red de laboratorio protegida.


## Publicar las reglas de Firestore

El archivo firestore.rules está en la raíz del repositorio. Después de autenticar Firebase CLI con una cuenta que tenga permisos sobre el proyecto, selecciona el proyecto correcto y ejecuta:

```bash
firebase login
firebase use --add
firebase deploy --only firestore:rules
```

Las reglas están preparadas en el repositorio, pero todavía deben desplegarse en el proyecto Firebase con una sesión autorizada del propietario.

## Verificación de compilación

El workflow .github/workflows/android-build.yml compila :app:assembleDebug en GitHub Actions al actualizar Copy-Main. Se ejecuta sin google-services.json porque los plugins de Google Services y Crashlytics se aplican condicionalmente cuando el archivo local existe.

Cuando se agregue google-services.json en app/, el workflow también compilará con los plugins de Firebase activos. El archivo se mantiene excluido de Git.
