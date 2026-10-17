package pe.edu.unmsm.siderurgica.domain.repository

import pe.edu.unmsm.siderurgica.domain.model.Product
import pe.edu.unmsm.siderurgica.domain.model.Quote
import pe.edu.unmsm.siderurgica.domain.model.QuoteDraft

interface CommerceRepository {
    suspend fun products(): List<Product>
    suspend fun addProduct(name: String, category: String, priceCents: Long, stock: Long, minimum: Long)
    suspend fun quotes(): List<Quote>
    suspend fun createQuote(operationId: String, draft: QuoteDraft, ownerUid: String? = null)
}
