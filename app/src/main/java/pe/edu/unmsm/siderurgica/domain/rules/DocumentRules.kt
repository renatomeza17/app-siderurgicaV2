package pe.edu.unmsm.siderurgica.domain.rules

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
object DocumentRules {
    fun invoice(ruc: String, number: String, date: String, cents: Long) {
        require(InputRules.validRuc(ruc) && number.trim().length in 1..80) { "Revisa el RUC y número del documento." }
        require(runCatching { LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)) }.isSuccess) { "Ingresa una fecha válida dd/mm/aaaa." }
        require(cents in 1..100_000_000_000) { "Revisa el importe." }
    }
    fun amount(value: String): Long {
        val amount = value.trim().replace(',', '.').toBigDecimalOrNull() ?: error("Monto no válido.")
        require(amount.scale() <= 2) { "El monto admite hasta dos decimales." }
        return amount.movePointRight(2).longValueExact()
    }
}
