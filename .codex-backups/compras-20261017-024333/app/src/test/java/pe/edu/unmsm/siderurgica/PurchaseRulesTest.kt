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
}
