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

object QuotePdfContent {
    fun rows(quote:BusinessRecord):List<String> {
        require(quote.lines.size in 1..3 && quote.client.isNotBlank()) { "La cotización no contiene un detalle válido." }
        fun amount(cents:Long)="S/ ${BigDecimal.valueOf(cents,2).toPlainString()}"
        return buildList {
            add("SIDERÚRGICA PERÚ")
            add("COTIZACIÓN COMERCIAL")
            add("Referencia: ${quote.id}")
            add("Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.ROOT).format(java.util.Date(quote.createdAt))}")
            add("Cliente: ${quote.client}")
            add("RUC: ${quote.ruc.ifBlank{"No indicado"}}")
            add("PRODUCTOS")
            quote.lines.forEachIndexed{index,line->
                add("${index+1}. ${line.name}")
                add("Cantidad: ${line.quantity} · Precio unitario: ${amount(line.unitCents)}")
                add("Importe bruto: ${amount(line.grossCents)} · Descuento acordado: ${line.discountPercent} %")
                add("Importe neto: ${amount(line.netCents)}")
            }
            add("Subtotal neto: ${amount(quote.subtotalCents)}")
            add("IGV (18 %): ${amount(quote.taxCents)}")
            add("TOTAL PROPUESTO: ${amount(quote.totalCents)}")
            add("Propuesta comercial. No constituye comprobante de venta ni acredita pago.")
            add("Confirma tu aceptación o solicita cambios por el canal de contacto del vendedor.")
        }
    }
}

class QuotePdfRepository(private val context:Context,private val services:FirebaseServices) {
    suspend fun generate(quote:BusinessRecord):File=withContext(Dispatchers.IO){
        val owner=services.auth.currentUser?.uid?:error("Inicia sesión.")
        require(quote.id.matches(Regex("[a-f0-9-]{36}"))) { "Referencia de cotización inválida." }
        val rows=QuotePdfContent.rows(quote)
        val directory=File(context.filesDir,"cotizaciones/$owner").apply{mkdirs()}
        val target=File(directory,"COT-${quote.id}.pdf")
        val temporary=File(directory,"COT-${quote.id}.tmp")
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
