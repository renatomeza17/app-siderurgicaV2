package pe.edu.unmsm.siderurgica.firebase
import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.data.local.LocalDao
import pe.edu.unmsm.siderurgica.domain.model.PurchasePhoto
import java.io.File

class PurchaseEvidenceRepository(private val context:Context,private val services:FirebaseServices,private val dao:LocalDao) {
    suspend fun upload(id:String,owner:String,c:PurchasePhoto) {
        require(services.auth.currentUser?.uid==owner){"Inicia sesión con la cuenta que registró la recepción."}
        val dependency=dao.operation(c.receiptOperation,owner)
        check(dependency?.status=="ENVIADO"){"Primero confirma la recepción pendiente. Luego reintenta la foto; no registres otra entrada de stock."}
        val order=services.database.collection("recepciones_v2").document(c.orderId)
        val receipt=order.get(Source.SERVER).await()
        require(receipt.getString("receivedBy")==owner && receipt.getString("operationId")==c.receiptOperation){"La foto no corresponde a esta recepción."}
        val meta=order.collection("adjuntos").document(id)
        if(meta.get(Source.SERVER).await().exists())return
        val file=File(c.imagePath).canonicalFile
        val directory=File(context.filesDir,"documentos").canonicalFile
        require(file.toPath().startsWith(directory.toPath()) && file.isFile && file.length() in 1 until 10*1024*1024){"La fotografía no está disponible o supera 10 MB. La recepción conserva su registro."}
        val path="compras/${c.orderId}/$id.jpg"
        val storage=FirebaseStorage.getInstance().apply{maxUploadRetryTimeMillis=60_000;maxOperationRetryTimeMillis=30_000}
        storage.reference.child(path).putFile(Uri.fromFile(file),StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        check(services.auth.currentUser?.uid==owner){"La sesión cambió. Reintenta la foto con la cuenta original."}
        services.database.runTransaction { tx->
            if(!tx.get(meta).exists()) tx.set(meta,mapOf("owner" to owner,"storagePath" to path,"receiptOperation" to c.receiptOperation,"createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }
    suspend fun download(orderId:String):File {
        val uid=services.auth.currentUser?.uid?:error("Inicia sesión.")
        require(orderId.matches(Regex("[a-f0-9-]{36}")))
        val dir=File(context.cacheDir,"recepciones/$uid").apply{mkdirs()}
        val file=File(dir,"$orderId.jpg")
        if(file.isFile)return file
        val records=services.database.collection("recepciones_v2").document(orderId).collection("adjuntos").limit(1).get(Source.SERVER).await()
        val path=records.documents.firstOrNull()?.getString("storagePath")?:error("No hay foto confirmada. Si la adjuntaste, revisa su envío en Pendientes.")
        require(path.matches(Regex("compras/$orderId/[a-f0-9-]{36}[.]jpg")))
        val temp=File(dir,"$orderId.tmp")
        try {
            FirebaseStorage.getInstance().reference.child(path).getFile(temp).await()
            require(temp.length() in 1 until 10*1024*1024)
            check(services.auth.currentUser?.uid==uid){"La sesión cambió."}
            temp.copyTo(file,overwrite=true)
        }finally{temp.delete()}
        return file
    }
}
