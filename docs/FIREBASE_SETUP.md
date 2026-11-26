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

Los plugins de Google Services y Crashlytics se activan automáticamente cuando existe app/google-services.json. Sin ese archivo el proyecto conserva la compilación de desarrollo, pero la sincronización con Firebase no funcionará.

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

## Reglas iniciales para pruebas

Publica estas reglas en Firestore Rules después de habilitar Authentication. Estas reglas permiten a cualquier sesión autenticada crear o actualizar eventos, pero no leerlos ni eliminarlos desde la app cliente:

rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /control_acceso_eventos/{eventId} {
      allow create, update: if request.auth != null;
      allow read, delete: if false;
    }
  }
}

Advertencia: el inicio anónimo sirve para prototipos/pruebas y no demuestra la identidad real del operador. Para producción se debe vincular la identidad a Firebase Authentication o emitir tokens desde un backend confiable, además de endurecer las reglas y activar App Check.

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

No se han desplegado estas reglas desde este entorno: el despliegue requiere autorización en la cuenta Firebase propietaria.

## Verificación de compilación

El workflow .github/workflows/android-build.yml compila :app:assembleDebug en GitHub Actions al actualizar Copy-Main. Se ejecuta sin google-services.json porque los plugins de Google Services y Crashlytics se aplican condicionalmente cuando el archivo local existe.

Cuando se agregue google-services.json en app/, el workflow también compilará con los plugins de Firebase activos. El archivo se mantiene excluido de Git.
