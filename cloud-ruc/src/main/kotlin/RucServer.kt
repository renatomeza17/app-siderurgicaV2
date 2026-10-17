import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.Firestore
import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RucStorage(private val db:Firestore,private val dailyLimit:Long) {
    fun cached(ruc:String):RucData? {
        val d=db.collection("_ruc_cache").document(ruc).get().get(10,TimeUnit.SECONDS)
        if(!d.exists() || (d.getLong("expiresAt")?:0)<Instant.now().epochSecond)return null
        return Gson().fromJson(d.getString("result"),RucData::class.java)
    }
    fun store(ruc:String,data:RucData) {
        db.collection("_ruc_cache").document(ruc).set(mapOf("result" to Gson().toJson(data),"expiresAt" to Instant.now().epochSecond+86400)).get(10,TimeUnit.SECONDS)
    }
    fun reserve(uid:String,external:Boolean) {
        val now=Instant.now();val day=now.atOffset(ZoneOffset.UTC).toLocalDate().toString();val minute=now.epochSecond/60
        val user=db.collection("_ruc_limits").document("user-$uid")
        val global=db.collection("_ruc_limits").document("provider-$day")
        db.runTransaction { tx->
            val u=tx.get(user).get();val g=if(external)tx.get(global).get()else null
            val count=if(u.getLong("minute")==minute)u.getLong("count")?:0 else 0
            val calls=g?.getLong("calls")?:0
            if(count>=5 || (external && calls>=dailyLimit))throw RucFailure(429)
            tx.set(user,mapOf("minute" to minute,"count" to count+1))
            if(external)tx.set(global,mapOf("calls" to calls+1))
            true
        }.get(15,TimeUnit.SECONDS)
    }
}
fun main() {
    val project=System.getenv("GOOGLE_CLOUD_PROJECT")?.takeIf{it.isNotBlank()}?:error("GOOGLE_CLOUD_PROJECT required")
    val secret=System.getenv("DECOLECTA_TOKEN")?.trim()?.removePrefix("Bearer ")?.trim()?.takeIf{it.matches(Regex("[A-Za-z0-9._~+/=-]{8,2000}"))}?:error("DECOLECTA_TOKEN required")
    val credentials=GoogleCredentials.getApplicationDefault()
    FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).setProjectId(project).build())
    val db=FirestoreOptions.newBuilder().setCredentials(credentials).setProjectId(project).build().service
    val store=RucStorage(db,(System.getenv("RUC_DAILY_LIMIT")?.toLongOrNull()?:20).coerceIn(1,1000))
    val upstream=DecolectaClient(secret)
    val service=RucService(authenticate={ token->
        val decoded=try { FirebaseAuth.getInstance().verifyIdToken(token,true) }catch(e:Exception){throw RucFailure(401)}
        val profile=db.collection("usuarios").document(decoded.uid).get().get(10,TimeUnit.SECONDS)
        RucAccess(decoded.uid,decoded.isEmailVerified,profile.getBoolean("activo")==true,profile.getString("rol").orEmpty())
    },cached=store::cached,reserve=store::reserve,fetch=upstream::lookup,store=store::store)
    val server=HttpServer.create(InetSocketAddress(System.getenv("PORT")?.toIntOrNull()?:8080),16)
    server.executor=Executors.newFixedThreadPool(8)
    server.createContext("/") { exchange->
        var code=200
        val body=try {
            if(exchange.requestURI.path!="/ruc")throw RucFailure(404)
            if(exchange.requestMethod!="GET")throw RucFailure(405)
            val raw=exchange.requestURI.rawQuery.orEmpty();if(raw.length>100)throw RucFailure(400)
            val parts=raw.split('&');if(parts.size!=1 || !parts[0].startsWith("numero="))throw RucFailure(400)
            val ruc=URLDecoder.decode(parts[0].substringAfter('='),Charsets.UTF_8)
            Gson().toJson(service.lookup(exchange.requestHeaders.getFirst("Authorization").orEmpty(),ruc))
        }catch(e:Exception){
            var cause:Throwable?=e;var status=503
            repeat(8){if(cause is RucFailure)status=(cause as RucFailure).status;cause=cause?.cause}
            code=status
            // Deliberately exclude exception details and upstream bodies from responses and logs.
            "{\"error\":\"consulta_no_disponible\"}"
        }
        val bytes=body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type","application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control","no-store")
        if(code==429)exchange.responseHeaders.set("Retry-After","60")
        try {exchange.sendResponseHeaders(code,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}}finally{exchange.close()}
    }
    Runtime.getRuntime().addShutdownHook(Thread{server.stop(1);db.close()})
    server.start()
}
