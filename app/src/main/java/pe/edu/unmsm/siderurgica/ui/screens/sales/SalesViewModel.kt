package pe.edu.unmsm.siderurgica.ui.screens.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.SalesRepository
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class SalesState(val orders: List<Order> = emptyList(), val sales: List<Sale> = emptyList(), val busy: Boolean = false, val message: String = "")
class SalesViewModel(private val repo: SalesRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SalesState())
    val state = mutable.asStateFlow()
    init { refresh() }
    fun refresh() = action { load() }
    fun approve(id: String) = mutate("Pedido aprobado. El stock fue actualizado.") { repo.approveOrder(id) }
    fun stop(id: String, state: String) = mutate("Pedido actualizado.") { repo.stopOrder(id, state) }
    fun sell(id: String) = mutate("Venta registrada. Pedido facturado.") { repo.createSale(id) }
    private suspend fun load() {
        val orders = repo.orders()
        val sales = repo.sales()
        mutable.value = mutable.value.copy(orders = orders, sales = sales)
    }
    private fun mutate(message: String, block: suspend () -> Unit) = action {
        block()
        mutable.value = mutable.value.copy(message = message)
        try { load() }
        catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = "$message Actualiza para consultar los listados.") }
    }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "")
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
