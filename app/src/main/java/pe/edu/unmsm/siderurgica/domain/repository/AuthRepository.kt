package pe.edu.unmsm.siderurgica.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.edu.unmsm.siderurgica.domain.model.AuthAccount

interface AuthRepository {
    val accounts: Flow<AuthAccount?>
    suspend fun login(email: String, password: String)
    suspend fun register(email: String, password: String)
    suspend fun refreshAccount(): AuthAccount?
    suspend fun sendVerification()
    suspend fun resetPassword(email: String)
    fun logout()
}
