package pe.edu.unmsm.siderurgica.data.local

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import pe.edu.unmsm.siderurgica.firebase.FirebaseServices
import pe.edu.unmsm.siderurgica.worker.PendingSyncWorker
import java.util.UUID
import java.util.concurrent.TimeUnit

class QueueRepository(private val context: Context, private val dao: LocalDao, private val services: FirebaseServices) {
    fun owner(): String = services.auth.currentUser?.uid ?: error("Inicia sesión.")
    fun observe(): Flow<List<PendingOperation>> = dao.observe(owner())
    suspend fun save(kind: String, title: String, value: Any, send: Boolean, id: String = UUID.randomUUID().toString()): String {
        val uid = owner()
        dao.insert(PendingOperation(id, uid, kind, title, Gson().toJson(value), if (send) "PENDIENTE" else "BORRADOR", System.currentTimeMillis()))
        if (send) schedule(uid)
        return id
    }
    suspend fun purchaseReceipt(command:pe.edu.unmsm.siderurgica.domain.model.BusinessCommand,imagePath:String) {
        require(command.action=="PURCHASE_RECEIVE")
        val uid=owner();val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
        val operations=mutableListOf(PendingOperation(id,uid,"BUSINESS","Recepción de compra",Gson().toJson(command),"PENDIENTE",now))
        if(imagePath.isNotBlank()) operations.add(PendingOperation(UUID.randomUUID().toString(),uid,"PURCHASE_PHOTO","Foto de la entrega",Gson().toJson(pe.edu.unmsm.siderurgica.domain.model.PurchasePhoto(command.target,imagePath,id)),"PENDIENTE",now+1))
        // A Room @Insert of a list is transactional: receipt and optional photo survive together.
        dao.insertOperations(operations)
        schedule(uid)
    }
    suspend fun enqueue(id: String) {
        val uid = owner()
        dao.enqueue(id, uid)
        schedule(uid)
    }
    suspend fun delete(id: String) { dao.delete(id, owner()) }
    fun schedule(uid: String = owner()) {
        val work = OneTimeWorkRequestBuilder<PendingSyncWorker>()
            .setInputData(workDataOf("owner" to uid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("firebase-sync-$uid", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }
    fun cancel(uid: String) {
        WorkManager.getInstance(context).cancelUniqueWork("firebase-sync-$uid")
        WorkManager.getInstance(context).cancelUniqueWork("inventory-$uid")
        context.getSharedPreferences("inventory-notifications", Context.MODE_PRIVATE).edit().remove("authorizedOwner").apply()
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
    }
    fun inventory(uid: String) { pe.edu.unmsm.siderurgica.worker.InventoryReviewWorker.schedule(context, uid) }
}
