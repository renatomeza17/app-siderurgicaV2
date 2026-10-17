package pe.edu.unmsm.siderurgica
import org.junit.Test
import org.junit.Assert.*
import pe.edu.unmsm.siderurgica.domain.rules.PurchaseRules
class PurchaseRulesTest {
    @Test fun allocationCannotConsumeExistingReservationsOrReceivedPurchases() {
        assertEquals(100L,PurchaseRules.available(1000,400,500))
        for (v in listOf(899L,-1L)) assertTrue(runCatching{PurchaseRules.available(v,400,500)}.isFailure)
    }
    @Test fun receiptRequiresDocumentAndPurchaseRequiresValidAgreedDelivery() {
        PurchaseRules.metadata("2026-10","2026-10-25","Cotización proveedor C-01")
        assertTrue(runCatching{PurchaseRules.metadata("2026-13","2026-10-25","C01")}.isFailure)
        assertTrue(runCatching{PurchaseRules.metadata("2026-10","2026-02-30","C01")}.isFailure)
        assertTrue(runCatching{PurchaseRules.receipt("","")}.isFailure)
        PurchaseRules.receipt("F001-123","Recibido conforme")
    }
    @Test fun purchasePdfRequiresApprovalAndKeepsNegotiatedPrices() {
        val line=pe.edu.unmsm.siderurgica.domain.model.BusinessLine("p1","Acero",5,2000,0,10000,10000)
        val order=pe.edu.unmsm.siderurgica.domain.model.BusinessRecord(id="order",client="Proveedor",lines=listOf(line),status="APROBADA",subtotalCents=10000,taxCents=1800,totalCents=11800,supplierQuote="COT-001",deliveryDate="2026-10-25")
        val rows=pe.edu.unmsm.siderurgica.data.documents.PurchasePdfContent.rows(order).joinToString("\n")
        assertTrue(rows.contains("TOTAL: S/ 118.00"))
        assertTrue(rows.contains("No acredita pago ni recepción"))
        assertTrue(runCatching{pe.edu.unmsm.siderurgica.data.documents.PurchasePdfContent.rows(order.copy(status="PENDIENTE"))}.isFailure)
    }
}
