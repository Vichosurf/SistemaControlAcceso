package com.example.sistemacontrolacceso

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sincroniza la cola local de eventos con Cloud Firestore.
 *
 * El ID local se utiliza como ID del documento para que los reintentos sean idempotentes.
 * Un evento solo se elimina de la cola local después de que Firestore confirme el batch.
 */
object FirebaseEventSync {
    private const val COLLECTION = "control_acceso_eventos"
    private val sincronizando = AtomicBoolean(false)

    fun sincronizarPendientes(
        context: Context,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val pendientes = WemosEventStore.obtenerPendientes(context)
        if (pendientes.length() == 0) {
            onComplete?.invoke(true, "sin eventos pendientes")
            return
        }
        if (!sincronizando.compareAndSet(false, true)) return

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (FirebaseApp.getApps(context).isEmpty()) {
                sincronizando.set(false)
                onComplete?.invoke(false, "falta app/google-services.json")
                return
            }

            val auth = FirebaseAuth.getInstance()
            val continuar: () -> Unit = {
                subirBatch(context, pendientes, onComplete)
            }

            if (auth.currentUser != null) {
                continuar()
            } else {
                auth.signInAnonymously()
                    .addOnSuccessListener { continuar() }
                    .addOnFailureListener { error ->
                        sincronizando.set(false)
                        onComplete?.invoke(false, "Error de autenticación Firebase: ${error.localizedMessage}")
                    }
            }
        } catch (error: Exception) {
            sincronizando.set(false)
            onComplete?.invoke(false, "Firebase no disponible: ${error.localizedMessage}")
        }
    }

    private fun subirBatch(
        context: Context,
        pendientes: JSONArray,
        onComplete: ((Boolean, String) -> Unit)?
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val batch = db.batch()
            val ids = mutableSetOf<String>()

            for (i in 0 until pendientes.length()) {
                val evento = pendientes.getJSONObject(i)
                val id = evento.getString("id")
                ids.add(id)

                val datos = hashMapOf<String, Any>(
                    "id" to id,
                    "barrera" to evento.optString("barrera"),
                    "evento" to evento.optString("evento"),
                    "distancia_cm" to evento.optInt("distancia"),
                    "lectura_ldr" to evento.optInt("luz"),
                    "operador" to evento.optString("operador", "desconocido"),
                    "fecha_local" to evento.optString("timestamp"),
                    "origen" to "wemos_d1",
                    "uid" to (FirebaseAuth.getInstance().currentUser?.uid ?: "sin_uid"),
                    "fecha_servidor" to FieldValue.serverTimestamp()
                )
                batch.set(db.collection(COLLECTION).document(id), datos)
            }

            batch.commit()
                .addOnSuccessListener {
                    WemosEventStore.marcarSincronizado(context, ids)
                    sincronizando.set(false)
                    onComplete?.invoke(true, "${ids.size} evento(s) sincronizado(s)")
                }
                .addOnFailureListener { error ->
                    // Se conservan los eventos locales para reintentar en la siguiente consulta.
                    sincronizando.set(false)
                    onComplete?.invoke(false, "No se pudo sincronizar: ${error.localizedMessage}")
                }
        } catch (error: Exception) {
            sincronizando.set(false)
            onComplete?.invoke(false, "Error preparando eventos: ${error.localizedMessage}")
        }
    }
}
