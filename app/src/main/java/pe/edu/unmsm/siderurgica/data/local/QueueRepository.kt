package pe.edu.unmsm.siderurgica.data.local

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import pe.edu.unmsm.siderurgica.firebase.FirebaseServices
import pe.edu.unmsm.siderurgica.worker.PendingSyncWorker
import java.util.UUID
import java.util.concurrent.TimeUnit

class QueueRepository(
    private val context: Context,
    private val dao: LocalDao,
    private val services: FirebaseServices
) {

    // --- AUTENTICACIÓN Y SEGURIDAD ---
    // Obtiene el ID del usuario actual de Firebase; si no hay sesión activa, frena la app con error
    fun owner(): String = services.auth.currentUser?.uid ?: error("Inicia sesión.")

    // --- OBSERVACIÓN REACTIVA ---
    // Expone un Flow de Room con las operaciones locales pendientes del usuario en tiempo real
    fun observe(): Flow<List<PendingOperation>> = dao.observe(owner())

    // --- GUARDADO GENÉRICO (PROVEEDORES Y OTROS BORRADORES) ---
    // Guarda el registro en la base de datos local (Room) y opcionalmente dispara la sincronización
    suspend fun save(
        kind: String,
        title: String,
        value: Any,
        send: Boolean,
        id: String = UUID.randomUUID().toString()
    ): String {
        val uid = owner()

        // Inserta la operación en Room serializando el objeto a JSON con Gson
        dao.insert(
            PendingOperation(
                id,
                uid,
                kind,
                title,
                Gson().toJson(value),
                if (send) "PENDIENTE" else "BORRADOR",
                System.currentTimeMillis()
            )
        )

        // Si se eligió enviar directo, programa la tarea en segundo plano con WorkManager
        if (send) schedule(uid)
        return id
    }

    // --- RECEPCIÓN DE COMPRAS Y FOTO (TRANSACCIÓN LOCAL) ---
    // Inserta la orden de compra y opcionalmente la foto en un solo lote para que no se pierdan
    suspend fun purchaseReceipt(
        command: pe.edu.unmsm.siderurgica.domain.model.BusinessCommand,
        imagePath: String
    ) {
        require(command.action == "PURCHASE_RECEIVE")

        val uid = owner()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val operations = mutableListOf(
            PendingOperation(
                id,
                uid,
                "BUSINESS",
                "Recepción de compra",
                Gson().toJson(command),
                "PENDIENTE",
                now
            )
        )

        // Si se adjuntó una foto local, se encola vinculada al ID del comprobante
        if (imagePath.isNotBlank()) {
            operations.add(
                PendingOperation(
                    UUID.randomUUID().toString(),
                    uid,
                    "PURCHASE_PHOTO",
                    "Foto de la entrega",
                    Gson().toJson(
                        pe.edu.unmsm.siderurgica.domain.model.PurchasePhoto(
                            command.target,
                            imagePath,
                            id
                        )
                    ),
                    "PENDIENTE",
                    now + 1
                )
            )
        }

        // Room ejecuta la lista de forma transaccional (se guardan ambos o ninguno)
        dao.insertOperations(operations)
        schedule(uid)
    }

    // Pasa una operación de "BORRADOR" a "PENDIENTE" y activa el sincronizador
    suspend fun enqueue(id: String) {
        val uid = owner()
        dao.enqueue(id, uid)
        schedule(uid)
    }

    // Elimina un borrador o registro pendiente de Room
    suspend fun delete(id: String) {
        dao.delete(id, owner())
    }

    // --- PROGRAMADOR DE TAREAS EN SEGUNDO PLANO (WORKMANAGER) ---
    // Configura la subida automática a Firebase: solo corre si hay internet y reintenta con backoff exponencial
    fun schedule(uid: String = owner()) {
        val work = OneTimeWorkRequestBuilder<PendingSyncWorker>()
            .setInputData(workDataOf("owner" to uid))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Requiere conexión activa
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS) // Reintentos a 30s, 60s, 120s...
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "firebase-sync-$uid",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            work
        )
    }

    // --- CIERRE DE SESIÓN Y LIMPIEZA ---
    // Cancela tareas pendientes, borra el usuario guardado en preferencias y cancela notificaciones activas
    fun cancel(uid: String) {
        WorkManager.getInstance(context).cancelUniqueWork("firebase-sync-$uid")
        WorkManager.getInstance(context).cancelUniqueWork("inventory-$uid")
        context.getSharedPreferences("inventory-notifications", Context.MODE_PRIVATE)
            .edit()
            .remove("authorizedOwner")
            .apply()
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
    }

    // Dispara el worker periódico para revisión y control de inventario
    fun inventory(uid: String) {
        pe.edu.unmsm.siderurgica.worker.InventoryReviewWorker.schedule(context, uid)
    }
}