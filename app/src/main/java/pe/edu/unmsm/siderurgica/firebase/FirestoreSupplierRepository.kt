package pe.edu.unmsm.siderurgica.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.data.local.*
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.rules.SupplierRules

class FirestoreSupplierRepository(private val services: FirebaseServices, private val cache: DeviceCache) {
    suspend fun list(): List<Supplier> {
        val owner = services.auth.currentUser?.uid ?: error("Inicia sesión.")
        if (!cache.online) return cache.read<List<Supplier>>(owner, "suppliers", object : TypeToken<List<Supplier>>() {}.type) ?: emptyList()
        return try {
            services.database.collection("proveedores").get(Source.SERVER).await().documents.map { d ->
                Supplier(d.id, d.getString("nombre").orEmpty(), d.getString("direccion").orEmpty(), d.getString("contacto").orEmpty(), d.getString("telefono").orEmpty())
            }.sortedBy { it.name }.also { cache.save(owner, "suppliers", it) }
        } catch (e: Exception) {
            if (e is CancellationException || !networkFailure(e)) throw e
            cache.read<List<Supplier>>(owner, "suppliers", object : TypeToken<List<Supplier>>() {}.type) ?: throw e
        }
    }
    suspend fun create(id: String, draft: SupplierDraft, owner: String) {
        SupplierRules.validate(draft)
        require(services.auth.currentUser?.uid == owner) { "La operación pertenece a otra cuenta." }
        val ref = services.database.collection("proveedores").document(draft.ruc)
        services.database.runTransaction { tx ->
            val existing = tx.get(ref)
            if (existing.exists()) {
                require(existing.getString("operationId") == id && existing.getString("creatorUid") == owner) { "Ya existe un proveedor con ese RUC. Revisa el listado." }
            } else tx.set(ref, mapOf("ruc" to draft.ruc, "nombre" to draft.name.trim(), "direccion" to draft.address.trim(),
                "contacto" to draft.contact.trim(), "telefono" to draft.phone.trim(), "creatorUid" to owner,
                "operationId" to id, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }
}
