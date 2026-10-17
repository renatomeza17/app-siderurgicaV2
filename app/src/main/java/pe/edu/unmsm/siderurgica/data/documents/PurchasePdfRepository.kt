package pe.edu.unmsm.siderurgica.data.documents

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.edu.unmsm.siderurgica.domain.model.BusinessRecord
import pe.edu.unmsm.siderurgica.firebase.FirebaseServices
import java.io.File
import java.math.BigDecimal

object PurchasePdfContent {
    fun rows(purchase:BusinessRecord):List<String> {
        require(purchase.lines.size in 1..3 && purchase.client.isNotBlank()) { "La orden no contiene un detalle válido." }
        fun amount(cents:Long)="S/ ${BigDecimal.valueOf(cents,2).toPlainString()}"
        require(purchase.status=="APROBADA") { "Solo se emiten órdenes autorizadas." }
        return buildList {
            add("SIDERÚRGICA PERÚ")
            add("ORDEN DE COMPRA")
            add("Referencia: ${purchase.id}")
            add("Proveedor: ${purchase.client} · RUC ${purchase.supplierRuc}")
            add("Acuerdo/cotización: ${purchase.supplierQuote}")
            add("Entrega estimada: ${purchase.deliveryDate}")
            purchase.lines.forEach { line ->
                add(line.name)
                add("${line.quantity} × ${amount(line.unitCents)} sin IGV = ${amount(line.netCents)}")
            }
            add("Subtotal: ${amount(purchase.subtotalCents)}")
            add("IGV 18 %: ${amount(purchase.taxCents)}")
            add("TOTAL: ${amount(purchase.totalCents)}")
            add("Coordinar entrega con la empresa. No acredita pago ni recepción de mercadería.")
        }
    }
}

class PurchasePdfRepository(private val context:Context,private val services:FirebaseServices) {
    suspend fun generate(purchase:BusinessRecord):File=withContext(Dispatchers.IO){
        val owner=services.auth.currentUser?.uid?:error("Inicia sesión.")
        require(purchase.id.matches(Regex("[a-f0-9-]{36}"))) { "Referencia de orden inválida." }
        val rows=PurchasePdfContent.rows(purchase)
        val directory=File(context.filesDir,"ordenes-compra/$owner").apply{mkdirs()}
        val target=File(directory,"OC-${purchase.id}.pdf")
        val temporary=File(directory,"OC-${purchase.id}.tmp")
        val pdf=PdfDocument()
        try {
            var pageNumber=1
            var page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            var y=44f
            rows.forEachIndexed{index,row->
                paint.textSize=when(index){0->20f;1->16f;else->11f}
                paint.isFakeBoldText=index<2 || row.startsWith("TOTAL PROPUESTO")
                var remaining=row
                while(remaining.isNotEmpty()){
                    if(y+20>800){pdf.finishPage(page);pageNumber++;page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create());y=44f}
                    val count=paint.breakText(remaining,true,515f,null).coerceAtLeast(1)
                    page.canvas.drawText(remaining.take(count),40f,y,paint)
                    remaining=remaining.drop(count)
                    y+=paint.textSize+7
                }
                y+=5
            }
            pdf.finishPage(page)
            temporary.outputStream().use{pdf.writeTo(it)}
            require(services.auth.currentUser?.uid==owner){"La sesión cambió durante la generación."}
            temporary.copyTo(target,overwrite=true)
        } finally {pdf.close();temporary.delete()}
        target
    }
}
