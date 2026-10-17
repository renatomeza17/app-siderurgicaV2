package pe.edu.unmsm.siderurgica.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.model.Product
import pe.edu.unmsm.siderurgica.domain.repository.CommerceRepository
import pe.edu.unmsm.siderurgica.domain.rules.QuoteRules
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class ProductState(val products: List<Product> = emptyList(), val busy: Boolean = false, val message: String = "", val saved: Boolean = false)
class ProductViewModel(private val repo: CommerceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProductState())
    val state = mutable.asStateFlow()
    init { refresh() }
    fun refresh() = action { mutable.value = mutable.value.copy(products = repo.products()) }
    fun save(name: String, category: String, price: String, stock: String, minimum: String) = action {
        repo.addProduct(name, category, QuoteRules.parsePrice(price), stock.toLongOrNull() ?: -1, minimum.toLongOrNull() ?: -1)
        mutable.value = mutable.value.copy(saved = true, message = "Producto registrado.")
        try { mutable.value = mutable.value.copy(products = repo.products()) }
        catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = "Producto registrado. Actualiza el listado para consultarlo.") }
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
