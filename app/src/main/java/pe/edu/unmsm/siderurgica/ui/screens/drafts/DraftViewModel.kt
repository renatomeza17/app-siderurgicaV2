package pe.edu.unmsm.siderurgica.ui.screens.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.data.local.*
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class DraftState(val operations: List<PendingOperation> = emptyList(), val busy: Boolean = false, val message: String = "")
class DraftViewModel(private val repo: QueueRepository) : ViewModel() {
    private val mutable = MutableStateFlow(DraftState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { repo.observe().collect { mutable.value = mutable.value.copy(operations = it) } } }
    fun send(id: String) = action { repo.enqueue(id); mutable.value = mutable.value.copy(message = "Envío programado. Requiere conexión y una sesión de esta cuenta.") }
    fun delete(id: String) = action { repo.delete(id) }
    fun sync() { repo.schedule(); mutable.value = mutable.value.copy(message = "Sincronización programada; Android decidirá cuándo ejecutarla según la red.") }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "")
        viewModelScope.launch {
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
