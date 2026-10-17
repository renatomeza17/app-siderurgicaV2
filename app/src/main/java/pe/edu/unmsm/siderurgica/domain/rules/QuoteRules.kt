package pe.edu.unmsm.siderurgica.domain.rules

import java.math.BigDecimal
import java.math.RoundingMode
import pe.edu.unmsm.siderurgica.domain.model.QuoteAmounts
import pe.edu.unmsm.siderurgica.domain.model.QuoteDraft

object QuoteRules {
    fun validate(draft: QuoteDraft) {
        require(draft.productId.isNotBlank()) { "Selecciona un producto." }
        require(draft.client.trim().length in 2..120) { "El cliente debe tener entre 2 y 120 caracteres." }
        require(draft.quantity in 1..1_000_000) { "La cantidad debe estar entre 1 y 1 000 000." }
        require(draft.ruc.isBlank() || InputRules.validRuc(draft.ruc)) { "El RUC no es válido." }
    }
    fun amounts(quantity: Long, priceCents: Long): QuoteAmounts {
        require(quantity in 1..1_000_000 && priceCents in 1..100_000_000) { "Cantidad o precio fuera de rango." }
        val discount = when { quantity >= 100 -> 10L; quantity >= 50 -> 7L; quantity >= 10 -> 5L; else -> 0L }
        val subtotal = Math.multiplyExact(quantity, priceCents)
        val total = BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(100 - discount))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact()
        return QuoteAmounts(subtotal, discount, total)
    }
    fun parsePrice(value: String): Long {
        val amount = value.trim().replace(',', '.').toBigDecimalOrNull()
            ?: error("Ingresa un precio válido.")
        require(amount.scale() <= 2) { "El precio admite hasta dos decimales." }
        val cents = amount.movePointRight(2).longValueExact()
        require(cents in 1..100_000_000) { "El precio debe ser positivo y no exceder S/ 1 000 000." }
        return cents
    }
}
