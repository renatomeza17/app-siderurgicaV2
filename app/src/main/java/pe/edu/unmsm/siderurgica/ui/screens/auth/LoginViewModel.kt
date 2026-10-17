package pe.edu.unmsm.siderurgica.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.usecase.LoginUseCase
import pe.edu.unmsm.siderurgica.domain.usecase.ResetPasswordUseCase
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class LoginState(val busy: Boolean = false, val message: String = "")
class LoginViewModel(private val login: LoginUseCase, private val reset: ResetPasswordUseCase) : ViewModel() {
    private val mutable = MutableStateFlow(LoginState())
    val state = mutable.asStateFlow()
    fun submit(email: String, password: String) = action { login(email, password) }
    fun resetPassword(email: String) = action {
        reset(email)
        mutable.value = mutable.value.copy(message = "Si el correo tiene una cuenta, recibirás instrucciones para recuperar el acceso.")
    }
    fun clear() { mutable.value = LoginState() }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = LoginState(busy = true)
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
