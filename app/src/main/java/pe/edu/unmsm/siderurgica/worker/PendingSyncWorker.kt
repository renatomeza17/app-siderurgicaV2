package pe.edu.unmsm.siderurgica.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext
import pe.edu.unmsm.siderurgica.data.local.*
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.CommerceRepository
import pe.edu.unmsm.siderurgica.firebase.*
import pe.edu.unmsm.siderurgica.ui.common.userMessage

class PendingSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val koin = GlobalContext.get()
        val services = koin.get<FirebaseServices>()
        if (!services.configured) return Result.success()
        val owner = inputData.getString("owner") ?: return Result.failure()
        if (services.auth.currentUser?.uid != owner) return Result.success()
        val dao = koin.get<LocalDao>()
        var retry = false
        for (operation in dao.ready(owner)) {
            if (services.auth.currentUser?.uid != owner) return Result.success()
            try {
                // Remain PENDIENTE until a remote acknowledgment: process death cannot strand an ENVIANDO row.
                when (operation.kind) {
                    "BUSINESS" -> koin.get<pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository>().execute(operation.id, owner, Gson().fromJson(operation.payload, BusinessCommand::class.java))
                    "RECEIPT" -> { val c = Gson().fromJson(operation.payload, BusinessCommand::class.java); koin.get<pe.edu.unmsm.siderurgica.data.documents.ReceiptRepository>().upload(c.target, c.receiptType, owner) }
                    "QUOTE" -> koin.get<CommerceRepository>().createQuote(operation.id, Gson().fromJson(operation.payload, QuoteDraft::class.java), owner)
                    "SUPPLIER" -> koin.get<FirestoreSupplierRepository>().create(operation.id, Gson().fromJson(operation.payload, SupplierDraft::class.java), owner)
                    "PURCHASE_PHOTO" -> koin.get<PurchaseEvidenceRepository>().upload(operation.id,owner,Gson().fromJson(operation.payload,PurchasePhoto::class.java))
                    "DOCUMENT" -> koin.get<DocumentRepository>().upload(operation.id, owner)
                    else -> error("Tipo de operación desconocido.")
                }
                dao.status(operation.id, owner, "ENVIADO", "Confirmado por Firebase.", operation.attempts + 1)
                if (services.auth.currentUser?.uid == owner) pe.edu.unmsm.siderurgica.notifications.AppNotifications.show(applicationContext, operation.id, "Sincronización completada", "La operación fue confirmada. Consulta Borradores.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val transient = networkFailure(e) && operation.attempts < 7
                dao.status(operation.id, owner, if (transient) "PENDIENTE" else "ERROR", userMessage(e), operation.attempts + 1)
                if (!transient && services.auth.currentUser?.uid == owner) pe.edu.unmsm.siderurgica.notifications.AppNotifications.show(applicationContext, operation.id, "No se pudo sincronizar", "Revisa la operación y su motivo en Borradores.")
                retry = retry || transient
            }
        }
        return if (retry || dao.ready(owner).isNotEmpty()) Result.retry() else Result.success()
    }
}
