package pe.edu.unmsm.siderurgica.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import pe.edu.unmsm.siderurgica.domain.model.RucResult
import pe.edu.unmsm.siderurgica.domain.rules.InputRules

data class RucResponse(@SerializedName("numero_documento") val number: String?,
    @SerializedName("razon_social") val name: String?,
    @SerializedName(value = "direccion", alternate = ["dirección"]) val address: String?,
    @SerializedName("estado") val status: String?, @SerializedName("condicion") val condition: String?)
interface RucApi {
    @GET("v1/sunat/ruc") suspend fun lookup(@Query("numero") ruc: String, @Header("Authorization") bearer: String): RucResponse
}
class RucLookup(private val api: RucApi) {
    suspend fun lookup(ruc: String, token: String): RucResult {
        require(InputRules.validRuc(ruc)) { "Revisa el RUC antes de consultar." }
        val secret = token.trim().removePrefix("Bearer ").trim()
        require(secret.isNotBlank()) { "Ingresa tu token de Decolecta solo para esta consulta. El registro manual funciona sin token." }
        require(secret.length in 8..2000 && secret.matches(Regex("[A-Za-z0-9._~+/=-]+"))) { "El formato del token no es válido." }
        val response = api.lookup(ruc, "Bearer $secret")
        require(response.number == ruc && !response.name.isNullOrBlank()) { "La API no devolvió una empresa válida para ese RUC." }
        return RucResult(ruc, response.name, response.address.orEmpty(), response.status.orEmpty(), response.condition.orEmpty())
    }
}
