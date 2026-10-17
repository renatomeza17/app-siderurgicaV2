package pe.edu.unmsm.siderurgica.firebase

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.data.local.*
import pe.edu.unmsm.siderurgica.domain.rules.DocumentRules
import java.io.File
import java.util.UUID

class DocumentRepository(private val services: FirebaseServices, private val dao: LocalDao, private val queue: QueueRepository) {
    private fun owner() = services.auth.currentUser?.uid ?: error("Inicia sesión.")
    fun observe(): Flow<List<LocalDocument>> = dao.documents(owner())
    suspend fun save(path: String, ruc: String, number: String, date: String, cents: Long, code: String, codeOnly: Boolean) {
        require(File(path).isFile) { "Vuelve a capturar el documento." }
        require(code.length <= 4000) { "El código es demasiado largo." }
        if (codeOnly) require(code.isNotBlank()) { "No se detectó un código legible." }
        else DocumentRules.invoice(ruc, number, date, cents)
        dao.document(LocalDocument(UUID.randomUUID().toString(), owner(), path,
            if (codeOnly) "" else ruc, if (codeOnly) "Código escaneado" else number.trim(),
            if (codeOnly) java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")) else date,
            if (codeOnly) 0 else cents, code, System.currentTimeMillis()))
    }
    suspend fun enqueue(id: String) {
        val uid = owner()
        val document = dao.document(id, uid) ?: error("Documento no encontrado.")
        require(document.storagePath == null) { "El documento ya está subido." }
        if (dao.operation(id, uid) == null) queue.save("DOCUMENT", document.number, mapOf("documentId" to id), true, id)
        else queue.enqueue(id)
    }
    suspend fun upload(id: String, uid: String) {
        require(services.auth.currentUser?.uid == uid) { "La operación pertenece a otra cuenta." }
        val document = dao.document(id, uid) ?: error("Documento no encontrado en este dispositivo.")
        if (document.storagePath != null) return
        val file = File(document.imagePath)
        require(file.isFile && file.length() < 10 * 1024 * 1024) { "La imagen no existe o supera 10 MB." }
        val path = "usuarios/$uid/documentos/$id.jpg"
        val storage = FirebaseStorage.getInstance().apply { maxUploadRetryTimeMillis = 60_000; maxOperationRetryTimeMillis = 30_000 }
        storage.reference.child(path).putFile(Uri.fromFile(file), StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        val ref = services.database.collection("documentos").document(uid).collection("items").document(id)
        services.database.runTransaction { tx ->
            if (!tx.get(ref).exists()) tx.set(ref, mapOf("ownerUid" to uid, "ruc" to document.ruc,
                "numero" to document.number, "fecha" to document.date, "montoCentimos" to document.amountCents,
                "codigo" to document.code, "storagePath" to path, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
        dao.uploaded(id, uid, path)
    }
}
