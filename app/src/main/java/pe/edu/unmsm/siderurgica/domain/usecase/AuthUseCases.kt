package pe.edu.unmsm.siderurgica.domain.usecase

import pe.edu.unmsm.siderurgica.domain.repository.AuthRepository
import pe.edu.unmsm.siderurgica.domain.rules.InputRules

class LoginUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) {
        val clean = InputRules.email(email)
        InputRules.password(password)
        auth.login(clean, password)
    }
}
class RegisterUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, confirmation: String) {
        val clean = InputRules.email(email)
        InputRules.password(password, registration = true)
        require(password == confirmation) { "Las contraseñas no coinciden." }
        auth.register(clean, password)
    }
}
class ResetPasswordUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String) = auth.resetPassword(InputRules.email(email))
}
