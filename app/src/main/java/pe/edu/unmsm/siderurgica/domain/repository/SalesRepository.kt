package pe.edu.unmsm.siderurgica.domain.repository

import pe.edu.unmsm.siderurgica.domain.model.Order
import pe.edu.unmsm.siderurgica.domain.model.Sale

interface SalesRepository {
    suspend fun quoteState(id: String, state: String)
    suspend fun convertQuote(id: String)
    suspend fun orders(): List<Order>
    suspend fun approveOrder(id: String)
    suspend fun stopOrder(id: String, state: String)
    suspend fun createSale(id: String)
    suspend fun sales(): List<Sale>
}
