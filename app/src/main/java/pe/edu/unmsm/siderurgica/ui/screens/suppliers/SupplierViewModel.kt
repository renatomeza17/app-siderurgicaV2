package pe.edu.unmsm.siderurgica.ui.screens.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.data.local.QueueRepository
import pe.edu.unmsm.siderurgica.data.remote.RucLookup
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.rules.SupplierRules
import pe.edu.unmsm.siderurgica.firebase.FirestoreSupplierRepository
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class SupplierState(val suppliers: List<Supplier> = emptyList(), val lookup: RucResult? = null,
    val busy: Boolean = false, val saved: Boolean = false, val message: String = "")
class SupplierViewModel(private val repo: FirestoreSupplierRepository, private val queue: QueueRepository, private val api: RucLookup) : ViewModel() {
    private val mutable = MutableStateFlow(SupplierState())
    val state = mutable.asStateFlow()
    init { refresh() }
    fun refresh() = action { mutable.value = mutable.value.copy(suppliers = repo.list()) }
    fun lookup(ruc: String) = action {
        val result = api.lookup(ruc.trim())
        mutable.value = mutable.value.copy(lookup = result, message = "Datos consultados. Revisa y confirma antes de guardar.")
    }
    fun save(draft: SupplierDraft, send: Boolean) = action {
        SupplierRules.validate(draft)
        require(mutable.value.suppliers.none { it.ruc == draft.ruc }) { "Ya existe un proveedor con ese RUC en los datos cargados." }
        queue.save("SUPPLIER", draft.name, draft, send)
        mutable.value = mutable.value.copy(saved = true, lookup = null, message = if (send) "Proveedor pendiente de sincronización." else "Borrador de proveedor guardado localmente.")
    }
    fun dismissSaved() { mutable.value = mutable.value.copy(saved = false) }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "")
        viewModelScope.launch {
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
