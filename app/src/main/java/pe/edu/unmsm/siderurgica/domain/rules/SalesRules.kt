package pe.edu.unmsm.siderurgica.domain.rules

object SalesRules {
    fun taxCents(netSubtotal: Long): Long {
        require(netSubtotal in 1..100_000_000_000_000) { "Importe fuera de rango." }
        return Math.addExact(Math.multiplyExact(netSubtotal, 18), 50) / 100
    }
    fun totalCents(netSubtotal: Long) = Math.addExact(netSubtotal, taxCents(netSubtotal))
    fun stockAfterApproval(stock: Long, quantity: Long): Long {
        require(quantity > 0 && stock >= quantity) { "Stock insuficiente. Disponible: $stock unidades." }
        return stock - quantity
    }
    fun requireState(actual: String, expected: String) {
        require(actual == expected) { "Esta operación requiere estado $expected. Estado actual: $actual." }
    }
}
