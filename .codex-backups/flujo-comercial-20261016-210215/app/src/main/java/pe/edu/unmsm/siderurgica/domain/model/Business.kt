package pe.edu.unmsm.siderurgica.domain.model

data class BusinessLineDraft(val productId: String = "", val quantity: Long = 1,
    val priceCents: Long = 0, val discountPercent: Long = 0)
data class BusinessLine(val productId: String = "", val name: String = "", val quantity: Long = 0,
    val unitCents: Long = 0, val discountPercent: Long = 0, val grossCents: Long = 0, val netCents: Long = 0)
data class BusinessRecord(val id: String = "", val owner: String = "", val client: String = "", val ruc: String = "", val clientId: String = "",
    val lines: List<BusinessLine> = emptyList(), val status: String = "", val subtotalCents: Long = 0,
    val taxCents: Long = 0, val totalCents: Long = 0, val reference: String = "", val supplierRuc: String = "",
    val budgetCents: Long = 0, val createdAt: Long = 0)
data class BusinessClient(val id: String = "", val name: String = "", val ruc: String = "")
data class StockMovement(val id: String = "", val productId: String = "", val productName: String = "",
    val type: String = "", val quantity: Long = 0, val beforeStock: Long = 0, val afterStock: Long = 0,
    val reason: String = "", val actor: String = "", val createdAt: Long = 0)
data class BusinessCommand(val action: String = "", val target: String = "", val client: String = "", val ruc: String = "",
    val lines: List<BusinessLineDraft> = emptyList(), val reference: String = "", val state: String = "",
    val supplierRuc: String = "", val budgetCents: Long = 0, val type: String = "", val quantity: Long = 0,
    val expectedStock: Long = -1, val reason: String = "", val clientId: String = "", val receiptType: String = "BOLETA")
