package pe.edu.unmsm.siderurgica.domain.rules

import pe.edu.unmsm.siderurgica.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode

object BusinessRules {
    const val MAX_LINES = 3
    fun client(name: String, ruc: String) {
        require(name.trim().length in 2..120) { "Selecciona un cliente válido." }
        require(ruc.isBlank() || InputRules.validRuc(ruc)) { "RUC inválido." }
    }
    fun lines(drafts: List<BusinessLineDraft>, products: List<Product>, purchase: Boolean = false): List<BusinessLine> {
        require(drafts.size in 1..MAX_LINES) { "Agrega de uno a tres productos distintos." }
        require(drafts.map { it.productId }.distinct().size == drafts.size) { "No repitas productos; modifica la cantidad." }
        return drafts.map { d ->
            val p = products.firstOrNull { it.id == d.productId } ?: error("Producto no encontrado.")
            require(d.quantity in 1..1_000_000 && d.priceCents in 1..100_000_000 && d.discountPercent in 0..30) { "Revisa cantidades, precios y descuento (0–30 %)." }
            require(purchase || d.priceCents == p.priceCents) { "El precio cambió. Revisa el borrador y prepara una propuesta nueva." }
            require(!purchase || d.discountPercent == 0L) { "Las compras utilizan el precio pactado, sin descuento adicional." }
            val gross = Math.multiplyExact(d.quantity, d.priceCents)
            val net = BigDecimal.valueOf(gross).multiply(BigDecimal.valueOf(100-d.discountPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact()
            BusinessLine(p.id, p.name, d.quantity, d.priceCents, d.discountPercent, gross, net)
        }
    }
    fun tax(subtotal: Long) = SalesRules.taxCents(subtotal)
    fun total(lines: List<BusinessLine>): Triple<Long, Long, Long> {
        val subtotal = lines.fold(0L) { sum, l -> Math.addExact(sum, l.netCents) }
        val tax = tax(subtotal)
        return Triple(subtotal, tax, Math.addExact(subtotal, tax))
    }
    fun stock(type: String, current: Long, quantity: Long, expected: Long = -1): Long {
        require(current in 0..1_000_000) { "Stock actual inválido." }
        require(quantity in (if (type == "AJUSTE") 0L else 1L)..1_000_000L) { "Cantidad inválida." }
        val next = when(type) {
            "ENTRADA", "COMPRA" -> Math.addExact(current, quantity)
            "SALIDA", "VENTA" -> Math.subtractExact(current, quantity)
            "AJUSTE" -> { require(expected == current) { "El stock cambió. Actualiza antes de ajustar." }; quantity }
            else -> error("Movimiento inválido.")
        }
        require(next in 0..1_000_000) { "Stock insuficiente o fuera del límite permitido." }
        return next
    }
}
