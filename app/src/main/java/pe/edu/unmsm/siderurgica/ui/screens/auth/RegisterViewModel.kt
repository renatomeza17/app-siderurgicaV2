package pe.edu.unmsm.siderurgica.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.usecase.RegisterUseCase
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class RegisterState(val busy: Boolean = false, val message: String = "")
class RegisterViewModel(private val register: RegisterUseCase) : ViewModel() {
    private val mutable = MutableStateFlow(RegisterState())
    val state = mutable.asStateFlow()
    fun submit(email: String, password: String, confirmation: String) {
        if (mutable.value.busy) return
        mutable.value = RegisterState(busy = true)
        viewModelScope.launch {
            try { register(email, password, confirmation) }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
