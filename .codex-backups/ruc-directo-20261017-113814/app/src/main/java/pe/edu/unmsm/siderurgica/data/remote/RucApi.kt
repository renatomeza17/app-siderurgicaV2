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
    @GET("ruc") suspend fun lookup(@Query("numero") ruc: String, @Header("Authorization") session: String): RucResponse
}
// Only the Firebase session is sent to our service; the provider secret never reaches Android.
class RucLookup(private val api: () -> RucApi, private val session: suspend () -> String) {
    suspend fun lookup(ruc: String): RucResult {
        require(InputRules.validRuc(ruc)) { "Revisa el RUC antes de consultar." }
        val response = try {
            val token=session()
            require(token.isNotBlank()) { "Inicia sesión nuevamente para consultar." }
            api().lookup(ruc,"Bearer $token")
        } catch(e:retrofit2.HttpException) {
            // Never display upstream bodies, authorization headers or internal configuration.
            error(when(e.code()) {
                400 -> "Revisa el RUC antes de consultar."
                401 -> "La sesión venció. Inicia sesión nuevamente."
                403 -> "Tu cuenta no tiene permiso para consultar RUC."
                404 -> "No se encontraron datos para ese RUC. Puedes registrarlo manualmente."
                429 -> "Se alcanzó el límite de consultas. Intenta más tarde o registra los datos manualmente."
                else -> "La consulta no está disponible. Intenta después o completa los datos manualmente."
            })
        }
        require(response.number == ruc && !response.name.isNullOrBlank()) { "La consulta no devolvió datos válidos para ese RUC." }
        return RucResult(ruc,response.name,response.address.orEmpty(),response.status.orEmpty(),response.condition.orEmpty())
    }
}
