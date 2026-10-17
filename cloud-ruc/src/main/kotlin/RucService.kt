import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class RucData(val numero_documento:String,val razon_social:String,val direccion:String="",val estado:String="",val condicion:String="")
data class RucAccess(val uid:String,val verified:Boolean,val active:Boolean,val role:String)
class RucFailure(val status:Int):RuntimeException()
fun validRuc(ruc:String):Boolean {
    if(!ruc.matches(Regex("(10|15|16|17|20)[0-9]{9}")))return false
    val weights=intArrayOf(5,4,3,2,7,6,5,4,3,2)
    val value=11-(0..9).sumOf{ruc[it].digitToInt()*weights[it]}%11
    return (if(value==10)0 else if(value==11)1 else value)==ruc.last().digitToInt()
}
class RucService(
    private val authenticate:(String)->RucAccess,
    private val cached:(String)->RucData?,
    private val reserve:(String,Boolean)->Unit,
    private val fetch:(String)->RucData,
    private val store:(String,RucData)->Unit
) {
    fun lookup(authorization:String,ruc:String):RucData {
        if(!authorization.startsWith("Bearer ") || authorization.length !in 10..8192)throw RucFailure(401)
        val user=authenticate(authorization.removePrefix("Bearer "))
        if(!user.verified || !user.active || user.role !in setOf("ADMIN","ALMACEN","VENTAS"))throw RucFailure(403)
        if(!validRuc(ruc))throw RucFailure(400)
        val existing=cached(ruc)
        reserve(user.uid,existing==null)
        if(existing!=null)return existing
        val result=fetch(ruc)
        if(result.numero_documento!=ruc || result.razon_social.isBlank() || result.razon_social.length>160)throw RucFailure(502)
        store(ruc,result)
        return result
    }
}
class DecolectaClient(private val secret:String) {
    private val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    fun lookup(ruc:String):RucData {
        if(!validRuc(ruc))throw RucFailure(400)
        val request=HttpRequest.newBuilder(URI("https://api.decolecta.com/v1/sunat/ruc?numero=$ruc"))
            .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer $secret").header("Accept","application/json").GET().build()
        val response=http.send(request,HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body->
            if(response.statusCode()!=200)throw RucFailure(when(response.statusCode()){404->404;429->429;else->502})
            val bytes=body.readNBytes(32769);if(bytes.size>32768)throw RucFailure(502)
            val obj=runCatching{Gson().fromJson(String(bytes,Charsets.UTF_8),com.google.gson.JsonObject::class.java)}.getOrNull()?:throw RucFailure(502)
            fun field(name:String)=obj.get(name)?.takeIf{!it.isJsonNull}?.asString.orEmpty()
            return RucData(field("numero_documento"),field("razon_social"),field("direccion").ifBlank{field("dirección")}.take(240),field("estado").take(80),field("condicion").take(80))
        }
    }
}
