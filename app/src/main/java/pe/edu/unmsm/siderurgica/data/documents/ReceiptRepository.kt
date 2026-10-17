package pe.edu.unmsm.siderurgica.data.documents

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pe.edu.unmsm.siderurgica.domain.model.BusinessRecord
import pe.edu.unmsm.siderurgica.domain.rules.InputRules
import pe.edu.unmsm.siderurgica.firebase.FirebaseServices
import java.io.File
import java.math.BigDecimal

class ReceiptRepository(private val context:Context,private val services:FirebaseServices) {
    private fun owner()=services.auth.currentUser?.uid?:error("Inicia sesión.")
    private fun key(saleId:String,type:String):String {
        require(saleId.matches(Regex("[a-f0-9-]{36}")) && type in listOf("BOLETA","FACTURA")) { "Comprobante inválido." }
        return "${saleId}_$type"
    }
    fun file(saleId:String,type:String,uid:String=owner()):File=File(context.filesDir,"comprobantes/$uid/${key(saleId,type)}.pdf").also{it.parentFile!!.mkdirs()}
    suspend fun generate(sale:BusinessRecord,type:String):File=withContext(Dispatchers.IO) {
        require(sale.status=="CONFIRMADA") { "Confirma la venta primero." }
        require(type!="FACTURA" || InputRules.validRuc(sale.ruc)) { "La factura requiere RUC válido del cliente." }
        val target=file(sale.id,type)
        val pdf=PdfDocument()
        try {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            var y=50f
            fun text(value:String,size:Float=12f){paint.textSize=size;page.canvas.drawText(value,36f,y,paint);y+=size+12}
            fun wrapped(value:String){value.chunked(75).forEach{ text(it) }}
            text("SIDERÚRGICA PERÚ",22f)
            text("$type · DEMOSTRACIÓN ACADÉMICA",16f)
            text("Sin validez tributaria. No emitido ni comunicado a SUNAT.",10f)
            text("Venta: ${sale.id}",10f)
            text("Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.ROOT).format(java.util.Date(sale.createdAt))}")
            wrapped("Cliente: ${sale.client}");text("RUC: ${sale.ruc.ifBlank{"—"}}")
            text("DETALLE",14f)
            sale.lines.forEach{l->
                wrapped(l.name)
                text("${l.quantity} × S/ ${BigDecimal.valueOf(l.unitCents,2)} · Descuento ${l.discountPercent} %",11f)
                text("Neto: S/ ${BigDecimal.valueOf(l.netCents,2)}",11f)
            }
            text("Subtotal neto: S/ ${BigDecimal.valueOf(sale.subtotalCents,2)}",14f)
            text("IGV (18 %): S/ ${BigDecimal.valueOf(sale.taxCents,2)}",14f)
            text("TOTAL: S/ ${BigDecimal.valueOf(sale.totalCents,2)}",18f)
            pdf.finishPage(page)
            target.outputStream().use{pdf.writeTo(it)}
        } finally { pdf.close() }
        target
    }
    suspend fun upload(saleId:String,type:String,uid:String) {
        require(owner()==uid){"Operación de otra cuenta."}
        val id=key(saleId,type)
        val metadata=services.database.collection("comprobantes_v2").document(uid).collection("items").document(id)
        if(metadata.get(Source.SERVER).await().exists())return
        val doc=services.database.collection("ventas_v2").document(saleId).get(Source.SERVER).await()
        require(doc.exists()){ "Venta no encontrada." }
        val sale=Gson().fromJson(Gson().toJson(doc.data!!.filterKeys{it !in listOf("createdAt","updatedAt","decisionAt")}),BusinessRecord::class.java)
            .copy(id=doc.id,createdAt=doc.getTimestamp("createdAt")!!.toDate().time)
        val pdf=generate(sale,type)
        require(owner()==uid){"La cuenta cambió durante el envío."}
        val path="usuarios/$uid/comprobantes/$id.pdf"
        val storage=FirebaseStorage.getInstance().apply{maxUploadRetryTimeMillis=60_000;maxOperationRetryTimeMillis=30_000}
        storage.reference.child(path).putFile(android.net.Uri.fromFile(pdf),StorageMetadata.Builder().setContentType("application/pdf").build()).await()
        services.database.runTransaction{tx->if(!tx.get(metadata).exists())tx.set(metadata,mapOf("saleId" to saleId,"type" to type,"storagePath" to path,"createdAt" to FieldValue.serverTimestamp()))}.await()
    }
    suspend fun download(saleId:String,type:String):File {
        val uid=owner()
        val target=file(saleId,type,uid)
        FirebaseStorage.getInstance().reference.child("usuarios/$uid/comprobantes/${key(saleId,type)}.pdf").getFile(target).await()
        return target
    }
}
