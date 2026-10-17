import com.google.cloud.Timestamp
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID

/** Runs on private Cloud Run, invoked by Scheduler with OIDC. Credentials use ADC. */
fun main() {
    val project=System.getenv("GOOGLE_CLOUD_PROJECT") ?: error("Configure GOOGLE_CLOUD_PROJECT.")
    FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project)
        .setCredentials(GoogleCredentials.getApplicationDefault()).build())
    val db=FirestoreClient.getFirestore()
    val server=HttpServer.create(InetSocketAddress(System.getenv("PORT")?.toInt()?:8080),0)
    server.createContext("/review") { exchange ->
        if(exchange.requestMethod!="POST"){
            exchange.sendResponseHeaders(405,-1);exchange.close()
        } else {
            val response=try { "processed="+reviewInventory(db) } catch(e:Exception){
                // No tokens, credentials, client data or request payloads in logs.
                System.err.println("Inventory review failed: "+e.javaClass.simpleName)
                exchange.sendResponseHeaders(503,-1);exchange.close();null
            }
            if(response!=null){val bytes=response.toByteArray();exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}}
        }
    }
    server.start()
}

fun signature(stock:Long,minimum:Long)="$stock:$minimum"
fun shouldAlert(stock:Long,minimum:Long,previous:String?)=stock<=minimum&&signature(stock,minimum)!=previous

fun reviewInventory(db:Firestore):Int {
    var processed=0
    var cursor:com.google.cloud.firestore.DocumentSnapshot?=null
    while(true){
        var query=db.collection("productos").orderBy(FieldPath.documentId()).limit(100)
        if(cursor!=null)query=query.startAfter(cursor)
        val page=query.get().get().documents
        if(page.isEmpty())break
        for(product in page){
            val stock=product.getLong("stock")?:continue
            val minimum=product.getLong("stockMinimo")?:continue
            val state=db.collection("alertas_envios").document(product.id)
            if(stock>minimum){state.set(mapOf("signature" to "NORMAL","updatedAt" to FieldValue.serverTimestamp())).get();continue}
            val lease=UUID.randomUUID().toString()
            val claimed=db.runTransaction { tx ->
                val previous=tx.get(state).get()
                val until=previous.getTimestamp("leaseUntil")?.seconds?:0
                if(!shouldAlert(stock,minimum,previous.getString("signature"))||until>Timestamp.now().seconds)false
                else {tx.set(state,mapOf("signature" to (previous.getString("signature")?:""),"lease" to lease,
                    "leaseUntil" to Timestamp.ofTimeSecondsAndNanos(Timestamp.now().seconds+300,0)));true}
            }.get()
            if(!claimed)continue
            try {
                val users=db.collection("usuarios").whereEqualTo("activo",true).get().get().documents
                    .filter{it.getString("rol") in listOf("ADMIN","ALMACEN")}
                var failed=false
                for(user in users){
                    val devices=user.reference.collection("dispositivos").get().get().documents
                    val targets=devices.filter{!it.getString("token").isNullOrBlank()}
                    targets.chunked(500).forEach{chunk->
                        val messages=chunk.map{device->Message.builder().setToken(device.getString("token")!!)
                            .putData("owner",user.id).putData("productId",product.id)
                            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).setTtl(900_000).build()).build()}
                        val result=FirebaseMessaging.getInstance().sendEach(messages)
                        result.responses.forEachIndexed{i,r->if(!r.isSuccessful){
                            if(r.exception?.messagingErrorCode==MessagingErrorCode.UNREGISTERED)chunk[i].reference.delete().get()
                            else failed=true
                        }}
                    }
                }
                // Mark only fully acknowledged runs. FCM is at least once; Android replaces by product ID.
                db.runTransaction { tx ->
                    val current=tx.get(state).get()
                    if(current.getString("lease")==lease)tx.update(state,mapOf("signature" to (if(failed)"" else signature(stock,minimum)),
                        "leaseUntil" to Timestamp.ofTimeSecondsAndNanos(0,0),"updatedAt" to FieldValue.serverTimestamp()))
                    null
                }.get()
                if(failed)error("FCM delivery needs retry")
                processed++
            }catch(e:Exception){
                // Five-minute lease expires automatically; a failed scheduled run is retried.
                throw e
            }
        }
        cursor=page.last()
    }
    return processed
}
