package pe.edu.unmsm.siderurgica.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.SalesRepository
import pe.edu.unmsm.siderurgica.domain.rules.SalesRules

class FirestoreSalesRepository(private val services: FirebaseServices) : SalesRepository {
    private val db get() = services.database
    private fun uid() = services.auth.currentUser?.uid ?: error("Inicia sesión.")
    override suspend fun quoteState(id: String, state: String) {
        require(state in listOf("APROBADA", "RECHAZADA")) { "Estado no permitido." }
        val ref = db.collection("cotizaciones").document(id)
        val actor = uid()
        db.runTransaction { tx ->
            val quote = tx.get(ref)
            require(quote.exists()) { "Cotización no encontrada." }
            if (quote.getString("estado") != state) {
                SalesRules.requireState(quote.getString("estado").orEmpty(), "PENDIENTE")
                tx.update(ref, mapOf("estado" to state, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to actor))
            }
        }.await()
    }
    override suspend fun convertQuote(id: String) {
        val quoteRef = db.collection("cotizaciones").document(id)
        val orderRef = db.collection("pedidos").document(id)
        val actor = uid()
        db.runTransaction { tx ->
            val quote = tx.get(quoteRef)
            val order = tx.get(orderRef)
            require(quote.exists()) { "Cotización no encontrada." }
            if (order.exists()) {
                require(order.getString("cotizacionId") == id && quote.getString("estado") == "CONVERTIDA") { "La conversión está en un estado inconsistente." }
            } else {
                SalesRules.requireState(quote.text("estado"), "APROBADA")
                val subtotal = quote.number("totalCentimos")
                // Preserve the accepted quote's discount, then apply IGV to the net amount.
                tx.set(orderRef, mapOf(
                    "cotizacionId" to id, "creatorUid" to quote.text("creatorUid"), "convertedBy" to actor,
                    "cliente" to quote.text("cliente"), "ruc" to quote.text("ruc"),
                    "productoId" to quote.text("productoId"), "productoNombre" to quote.text("productoNombre"),
                    "cantidad" to quote.number("cantidad"), "precioCentimos" to quote.number("precioCentimos"),
                    "descuentoPorcentaje" to quote.number("descuentoPorcentaje"), "subtotalCentimos" to subtotal,
                    "igvCentimos" to SalesRules.taxCents(subtotal), "totalCentimos" to SalesRules.totalCents(subtotal),
                    "estado" to "PENDIENTE", "createdAt" to FieldValue.serverTimestamp()))
                tx.update(quoteRef, mapOf("estado" to "CONVERTIDA", "pedidoId" to id,
                    "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to actor))
            }
        }.await()
    }
    override suspend fun orders(): List<Order> = db.collection("pedidos").get(Source.SERVER).await().documents
        .sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0 }.map { d ->
            Order(d.id, d.text("cotizacionId"), d.text("cliente"), d.text("productoId"), d.text("productoNombre"),
                d.number("cantidad"), d.number("subtotalCentimos"), d.number("igvCentimos"), d.number("totalCentimos"), d.text("estado"))
        }
    override suspend fun approveOrder(id: String) {
        val ref = db.collection("pedidos").document(id)
        val actor = uid()
        db.runTransaction { tx ->
            val order = tx.get(ref)
            require(order.exists()) { "Pedido no encontrado." }
            if (order.text("estado") !in listOf("APROBADO", "FACTURADO")) {
                SalesRules.requireState(order.text("estado"), "PENDIENTE")
                val productRef = db.collection("productos").document(order.text("productoId"))
                val product = tx.get(productRef)
                require(product.exists()) { "Producto no encontrado. No se puede aprobar." }
                val newStock = SalesRules.stockAfterApproval(product.number("stock"), order.number("cantidad"))
                tx.update(productRef, mapOf("stock" to newStock, "ultimoPedidoId" to id, "updatedAt" to FieldValue.serverTimestamp()))
                tx.update(ref, mapOf("estado" to "APROBADO", "updatedBy" to actor, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    override suspend fun stopOrder(id: String, state: String) {
        require(state in listOf("ANULADO", "RECHAZADO")) { "Estado no permitido." }
        val ref = db.collection("pedidos").document(id)
        val actor = uid()
        db.runTransaction { tx ->
            val order = tx.get(ref)
            require(order.exists()) { "Pedido no encontrado." }
            if (order.text("estado") != state) {
                SalesRules.requireState(order.text("estado"), "PENDIENTE")
                tx.update(ref, mapOf("estado" to state, "updatedBy" to actor, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    override suspend fun createSale(id: String) {
        val orderRef = db.collection("pedidos").document(id)
        val saleRef = db.collection("ventas").document(id)
        val actor = uid()
        db.runTransaction { tx ->
            val order = tx.get(orderRef)
            val sale = tx.get(saleRef)
            require(order.exists()) { "Pedido no encontrado." }
            if (sale.exists()) {
                require(sale.text("pedidoId") == id && order.text("estado") == "FACTURADO") { "La venta está en un estado inconsistente." }
            } else {
                SalesRules.requireState(order.text("estado"), "APROBADO")
                tx.set(saleRef, mapOf("pedidoId" to id, "createdBy" to actor, "creatorUid" to order.text("creatorUid"),
                    "cliente" to order.text("cliente"), "ruc" to order.text("ruc"),
                    "productoId" to order.text("productoId"), "productoNombre" to order.text("productoNombre"),
                    "cantidad" to order.number("cantidad"), "subtotalCentimos" to order.number("subtotalCentimos"),
                    "igvCentimos" to order.number("igvCentimos"), "totalCentimos" to order.number("totalCentimos"),
                    "estado" to "PENDIENTE", "createdAt" to FieldValue.serverTimestamp()))
                tx.update(orderRef, mapOf("estado" to "FACTURADO", "ventaId" to id,
                    "updatedBy" to actor, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    override suspend fun sales(): List<Sale> = db.collection("ventas").get(Source.SERVER).await().documents
        .sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0 }.map { d ->
            Sale(d.id, d.text("pedidoId"), d.text("cliente"), d.text("productoNombre"), d.number("cantidad"),
                d.number("subtotalCentimos"), d.number("igvCentimos"), d.number("totalCentimos"), d.text("estado"))
        }
    private fun DocumentSnapshot.text(field: String) = getString(field) ?: error("Falta $field en el documento.")
    private fun DocumentSnapshot.number(field: String) = getLong(field) ?: error("Falta $field en el documento.")
}
