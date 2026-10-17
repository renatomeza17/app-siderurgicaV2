package pe.edu.unmsm.siderurgica.domain.repository
import pe.edu.unmsm.siderurgica.domain.model.*
interface BusinessRepository {
    suspend fun records(kind: String): List<BusinessRecord>
    suspend fun clients(): List<BusinessClient>
    suspend fun purchaseBudget(period: String): PurchaseBudget
    suspend fun currentRole(): String
    suspend fun movements(): List<StockMovement>
    suspend fun execute(id: String, owner: String, command: BusinessCommand)
}
