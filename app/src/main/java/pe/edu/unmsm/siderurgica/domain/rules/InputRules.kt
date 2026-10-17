package pe.edu.unmsm.siderurgica.domain.rules

object InputRules {
    fun email(value: String): String {
        val clean = value.trim()
        require(clean.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Ingresa un correo válido." }
        return clean
    }
    fun password(value: String, registration: Boolean = false) {
        require(value.isNotBlank()) { "Ingresa la contraseña." }
        if (registration) require(value.length >= 6) { "La contraseña debe tener al menos 6 caracteres." }
    }
    fun name(value: String): String {
        val clean = value.trim()
        require(clean.length in 2..80) { "El nombre debe tener entre 2 y 80 caracteres." }
        return clean
    }
    fun validRuc(value: String): Boolean {
        if (!value.matches(Regex("\\d{11}")) || value.take(2) !in setOf("10", "15", "16", "17", "20")) return false
        val weights = listOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
        return (11 - weights.indices.sumOf { (value[it] - '0') * weights[it] } % 11) % 10 == value.last() - '0'
    }
}
