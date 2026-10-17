package pe.edu.unmsm.siderurgica.ui.screens.quotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.CommerceRepository
import pe.edu.unmsm.siderurgica.domain.repository.SalesRepository
import pe.edu.unmsm.siderurgica.data.local.QueueRepository
import pe.edu.unmsm.siderurgica.domain.rules.QuoteRules
import pe.edu.unmsm.siderurgica.ui.common.userMessage
import java.util.UUID

data class QuoteState(val quotes: List<Quote> = emptyList(), val products: List<Product> = emptyList(),
    val busy: Boolean = false, val message: String = "", val saved: Boolean = false, val retryPending: Boolean = false)
class QuoteViewModel(private val repo: CommerceRepository, private val flow: SalesRepository, private val queue: QueueRepository) : ViewModel() {
    private val mutable = MutableStateFlow(QuoteState())
    val state = mutable.asStateFlow()
    init { refresh() }
    fun refresh() = action {
        val products = repo.products()
        val quotes = repo.quotes()
        mutable.value = mutable.value.copy(products = products, quotes = quotes)
    }
    fun save(draft: QuoteDraft, send: Boolean = true) = action {
        QuoteRules.validate(draft)
        val product = mutable.value.products.firstOrNull { it.id == draft.productId } ?: error("Selecciona un producto de los datos cargados.")
        queue.save("QUOTE", "${draft.client} · ${product.name}", draft.copy(expectedPriceCents = product.priceCents), send)
        mutable.value = mutable.value.copy(saved = true, retryPending = false,
            message = if (send) "Cotización pendiente de sincronización. Revisa Borradores." else "Borrador guardado localmente.")
    }
    fun approve(id: String) = transition("Cotización aprobada.") { flow.quoteState(id, "APROBADA") }
    fun reject(id: String) = transition("Cotización rechazada.") { flow.quoteState(id, "RECHAZADA") }
    fun convert(id: String) = transition("Pedido creado. Consulta la sección Pedidos.") { flow.convertQuote(id) }
    private fun transition(message: String, block: suspend () -> Unit) = action {
        block()
        mutable.value = mutable.value.copy(message = message)
        try { mutable.value = mutable.value.copy(quotes = repo.quotes()) }
        catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = "$message Actualiza el listado.") }
    }
    fun dismissSaved() { mutable.value = mutable.value.copy(saved = false) }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "", saved = false)
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
