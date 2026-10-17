package pe.edu.unmsm.siderurgica.domain.model

enum class Role { PENDIENTE, VENTAS, ALMACEN, RRHH, ADMIN }
data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val cached: Boolean = false
) {
    val canReadProducts get() = active && role in setOf(Role.ADMIN, Role.VENTAS, Role.ALMACEN)
    val canManageProducts get() = active && role in setOf(Role.ADMIN, Role.ALMACEN)
    val canSell get() = active && role in setOf(Role.ADMIN, Role.VENTAS)
}
data class AuthAccount(val uid: String, val email: String, val verified: Boolean)
