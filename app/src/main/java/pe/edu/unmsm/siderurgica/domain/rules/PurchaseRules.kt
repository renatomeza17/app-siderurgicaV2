package pe.edu.unmsm.siderurgica.domain.rules

object PurchaseRules {
    fun period(value: String) { require(value.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) { "Selecciona un período AAAA-MM válido." } }
    fun metadata(period: String, delivery: String, quote: String) {
        period(period)
        require(runCatching { java.time.LocalDate.parse(delivery) }.isSuccess) { "Indica entrega estimada AAAA-MM-DD válida." }
        require(quote.trim().length in 3..120) { "Indica referencia de cotización o acuerdo (3–120 caracteres)." }
    }
    fun reason(value: String) { require(value.trim().length in 5..240) { "Indica el sustento (5–240 caracteres)." } }
    fun receipt(document: String, notes: String) {
        require(document.trim().length in 3..120) { "Indica factura o guía del proveedor (3–120 caracteres)." }
        require(notes.trim().length <= 240) { "Las observaciones admiten hasta 240 caracteres." }
    }
    fun available(total: Long, reserved: Long, spent: Long): Long {
        require(total in 0..400_000_000_000_000L && reserved >= 0 && spent >= 0 && reserved <= total && spent <= total-reserved) { "Presupuesto insuficiente o inválido." }
        return total-reserved-spent
    }
}
