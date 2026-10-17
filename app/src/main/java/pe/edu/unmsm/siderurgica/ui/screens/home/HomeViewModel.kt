package pe.edu.unmsm.siderurgica.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.CommerceRepository
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class HomeState(val products: List<Product>? = null, val quotes: List<pe.edu.unmsm.siderurgica.domain.model.BusinessRecord>? = null, val busy: Boolean = false, val message: String = "")
class HomeViewModel(private val repo: CommerceRepository, private val business: pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository) : ViewModel() {
    private val mutable = MutableStateFlow(HomeState())
    val state = mutable.asStateFlow()
    fun refresh(profile: UserProfile) {
        if (mutable.value.busy) return
        mutable.value = HomeState(busy = true)
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            if (profile.canReadProducts) try { mutable.value = mutable.value.copy(products = repo.products()) }
            catch (e: Exception) { if (e is CancellationException) throw e; errors += "Inventario: ${userMessage(e)}" }
            if (profile.canSell) try { mutable.value = mutable.value.copy(quotes = business.records("quotes")) }
            catch (e: Exception) { if (e is CancellationException) throw e; errors += "Cotizaciones: ${userMessage(e)}" }
            mutable.value = mutable.value.copy(busy = false, message = errors.joinToString("\n"))
        }
    }
}
