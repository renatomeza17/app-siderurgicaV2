package pe.edu.unmsm.siderurgica.domain.model

data class Product(val id: String, val name: String, val category: String, val priceCents: Long, val stock: Long, val minimumStock: Long)
data class QuoteDraft(val productId: String, val client: String, val ruc: String, val quantity: Long, val expectedPriceCents: Long? = null)
data class QuoteAmounts(val subtotalCents: Long, val discountPercent: Long, val totalCents: Long)
data class Quote(val id: String, val creatorUid: String, val client: String, val ruc: String, val productName: String,
    val quantity: Long, val unitPriceCents: Long, val subtotalCents: Long, val discountPercent: Long,
    val totalCents: Long, val status: String, val productId: String = "")
