package pe.edu.unmsm.siderurgica

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.rules.BusinessRules
import pe.edu.unmsm.siderurgica.data.documents.QuotePdfContent

class CommercialFlowTest {
    @Test fun automaticDiscountCoversAllQuantityBoundaries(){
        listOf(1L to 0L,9L to 0L,10L to 5L,49L to 5L,50L to 7L,99L to 7L,100L to 10L,1000L to 10L).forEach{(q,expected)->assertEquals(expected,BusinessRules.automaticDiscount(q))}
    }
    @Test fun manualDiscountCannotBypassCommercialPolicy(){
        val products=listOf(Product("p1","Acero","Acero",101,1000,5))
        listOf(1L to 30L,10L to 0L,50L to 10L,100L to 100L).forEach{(q,discount)->
            assertThrows(IllegalArgumentException::class.java){BusinessRules.lines(listOf(BusinessLineDraft("p1",q,101,discount)),products)}
        }
        assertEquals(6575L,BusinessRules.lines(listOf(BusinessLineDraft("p1",70,101,7)),products).single().netCents)
    }
    @Test fun decisionsRequireSustentAndAppropriateChannel(){
        BusinessRules.decision("Aceptación por correo OC-123","CORREO",true)
        BusinessRules.decision("Disponibilidad revisada con almacén","INTERNO",false)
        assertThrows(IllegalArgumentException::class.java){BusinessRules.decision("ok","CORREO",true)}
        assertThrows(IllegalArgumentException::class.java){BusinessRules.decision("Cliente acepta","",true)}
        assertThrows(IllegalArgumentException::class.java){BusinessRules.decision("Stock revisado","CORREO",false)}
    }
    @Test fun oldAcknowledgedCommandHasSameMeaningAfterAddingChannel(){
        val gson=Gson()
        val previous="""{"action":"SALE_CREATE","target":"old-order"}"""
        val restored=gson.fromJson(previous,BusinessCommand::class.java)
        assertEquals(BusinessCommand("SALE_CREATE","old-order"),restored)
        assertEquals(restored,gson.fromJson(gson.toJson(restored),BusinessCommand::class.java))
    }
    @Test fun quotePdfPreservesAgreedLegacyAmountsWithoutRepricing(){
        val line=BusinessLine("p1","Acero",2,10000,10,20000,18000)
        val quote=BusinessRecord(id="reference",client="Cliente de prueba",lines=listOf(line),subtotalCents=18000,taxCents=3240,totalCents=21240)
        val rows=QuotePdfContent.rows(quote).joinToString("\n")
        assertTrue(rows.contains("Descuento acordado: 10 %"))
        assertTrue(rows.contains("TOTAL PROPUESTO: S/ 212.40"))
        assertTrue(rows.contains("No constituye comprobante de venta ni acredita pago"))
        assertEquals(10L,quote.lines.single().discountPercent)
    }
}
