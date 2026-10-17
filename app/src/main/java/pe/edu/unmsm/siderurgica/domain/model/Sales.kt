package pe.edu.unmsm.siderurgica.domain.model

data class Order(val id: String, val quoteId: String, val client: String, val productId: String, val productName: String,
    val quantity: Long, val subtotalCents: Long, val taxCents: Long, val totalCents: Long, val status: String)
data class Sale(val id: String, val orderId: String, val client: String, val productName: String, val quantity: Long,
    val subtotalCents: Long, val taxCents: Long, val totalCents: Long, val status: String)
