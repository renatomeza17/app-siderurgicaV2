package pe.edu.unmsm.siderurgica.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import pe.edu.unmsm.siderurgica.data.local.*
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.CommerceRepository
import pe.edu.unmsm.siderurgica.domain.rules.QuoteRules

class FirestoreCommerceRepository(private val services: FirebaseServices, private val cache: DeviceCache) : CommerceRepository {
    override suspend fun products(): List<Product> {
        val owner = services.auth.currentUser?.uid ?: error("Inicia sesión.")
        if (!cache.online) return cache.read<List<Product>>(owner, "products", object : TypeToken<List<Product>>() {}.type)
            ?: error("No hay productos guardados. Consulta el catálogo una vez con internet.")
        return try {
            services.database.collection("productos").get(Source.SERVER).await().documents.map { d ->
                Product(d.id, d.getString("nombre").orEmpty(), d.getString("categoria").orEmpty(), d.getLong("precioCentimos") ?: 0,
                    d.getLong("stock") ?: 0, d.getLong("stockMinimo") ?: 0)
            }.sortedBy { it.name }.also { cache.save(owner, "products", it) }
        } catch (e: Exception) {
            if (e is CancellationException || !networkFailure(e)) throw e
            cache.read<List<Product>>(owner, "products", object : TypeToken<List<Product>>() {}.type) ?: throw e
        }
    }

    override suspend fun addProduct(name: String, category: String, priceCents: Long, stock: Long, minimum: Long) {
        require(name.trim().length in 2..120 && category.trim().length in 2..80) { "Completa nombre y categoría." }
        require(priceCents in 1..100_000_000 && stock in 0..1_000_000 && minimum in 0..1_000_000) { "Revisa precio y stock." }
        val ref = services.database.collection("productos").document()
        // Creation online only. No silent pending stock writes in this first stage.
        services.database.runTransaction { tx ->
            tx.set(ref, mapOf("nombre" to name.trim(), "categoria" to category.trim(), "precioCentimos" to priceCents,
                "stock" to stock, "stockMinimo" to minimum, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }
    override suspend fun quotes(): List<Quote> {
        val owner = services.auth.currentUser?.uid ?: error("Inicia sesión.")
        if (!cache.online) return cache.read<List<Quote>>(owner, "quotes", object : TypeToken<List<Quote>>() {}.type) ?: emptyList()
        return try { remoteQuotes().also { cache.save(owner, "quotes", it) } }
        catch (e: Exception) {
            if (e is CancellationException || !networkFailure(e)) throw e
            cache.read<List<Quote>>(owner, "quotes", object : TypeToken<List<Quote>>() {}.type) ?: throw e
        }
    }
    private suspend fun remoteQuotes(): List<Quote> = services.database.collection("cotizaciones").get(Source.SERVER).await()
        .documents.sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0 }.map { d ->
            Quote(d.id, d.getString("creatorUid").orEmpty(), d.getString("cliente").orEmpty(), d.getString("ruc").orEmpty(),
                d.getString("productoNombre").orEmpty(), d.getLong("cantidad") ?: 0, d.getLong("precioCentimos") ?: 0,
                d.getLong("subtotalCentimos") ?: 0, d.getLong("descuentoPorcentaje") ?: 0,
                d.getLong("totalCentimos") ?: 0, d.getString("estado").orEmpty(), d.getString("productoId").orEmpty())
        }
    override suspend fun createQuote(operationId: String, draft: QuoteDraft, ownerUid: String?) {
        QuoteRules.validate(draft)
        val uid = services.auth.currentUser?.uid ?: error("Inicia sesión.")
        require(ownerUid == null || ownerUid == uid) { "La operación pertenece a otra cuenta." }
        val ref = services.database.collection("cotizaciones").document(operationId)
        val productRef = services.database.collection("productos").document(draft.productId)
        services.database.runTransaction { tx ->
            val existing = tx.get(ref)
            if (existing.exists()) {
                require(existing.getString("creatorUid") == uid && existing.getString("productoId") == draft.productId &&
                    existing.getString("cliente") == draft.client.trim() && existing.getString("ruc") == draft.ruc &&
                    existing.getLong("cantidad") == draft.quantity) { "El identificador ya se utilizó para otra cotización." }
            } else {
                val product = tx.get(productRef)
                require(product.exists()) { "El producto ya no está disponible." }
                val price = product.getLong("precioCentimos") ?: error("El producto no tiene precio válido.")
                require(draft.expectedPriceCents == null || draft.expectedPriceCents == price) { "El precio cambió desde el borrador. Revisa el producto y prepara una nueva cotización." }
                val amounts = QuoteRules.amounts(draft.quantity, price)
                tx.set(ref, mapOf("creatorUid" to uid, "cliente" to draft.client.trim(), "ruc" to draft.ruc,
                    "productoId" to draft.productId, "productoNombre" to product.getString("nombre"),
                    "cantidad" to draft.quantity, "precioCentimos" to price,
                    "subtotalCentimos" to amounts.subtotalCents, "descuentoPorcentaje" to amounts.discountPercent,
                    "totalCentimos" to amounts.totalCents, "estado" to "PENDIENTE",
                    "createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
}
