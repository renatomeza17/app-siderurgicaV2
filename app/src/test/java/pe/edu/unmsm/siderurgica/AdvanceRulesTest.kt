package pe.edu.unmsm.siderurgica

import org.junit.Assert.*
import org.junit.Test
import pe.edu.unmsm.siderurgica.domain.model.SupplierDraft
import pe.edu.unmsm.siderurgica.domain.rules.*

class AdvanceRulesTest {
    @Test fun supplierValidationRejectsInvalidRucAndOversizedFields() {
        SupplierRules.validate(SupplierDraft("20100070970", "Proveedor", "Lima", "Ana", "999123456"))
        assertTrue(runCatching { SupplierRules.validate(SupplierDraft("20100070971", "Proveedor", "", "", "")) }.isFailure)
        assertTrue(runCatching { SupplierRules.validate(SupplierDraft("20100070970", "A", "", "", "")) }.isFailure)
        assertTrue(runCatching { SupplierRules.validate(SupplierDraft("20100070970", "Proveedor", "x".repeat(241), "", "")) }.isFailure)
    }
    @Test fun parserExtractsInvoiceAndNormalizesTotal() {
        val result = InvoiceParser.parse("RUC 20100070970\nF001-1234\n12/10/2026\nTOTAL S/ 1,234.50\n")
        assertEquals("20100070970", result.ruc)
        assertEquals("F001-1234", result.number)
        assertEquals("1234.50", result.amount)
        assertEquals(123450L, DocumentRules.amount(result.amount))
    }
    @Test fun reviewedInvoiceNeedsRealDateAndPositiveAmount() {
        DocumentRules.invoice("20100070970", "F001-1", "29/02/2024", 100)
        assertTrue(runCatching { DocumentRules.invoice("20100070970", "F001-1", "29/02/2025", 100) }.isFailure)
        assertTrue(runCatching { DocumentRules.invoice("20100070970", "F001-1", "01/01/2026", 0) }.isFailure)
        assertTrue(runCatching { DocumentRules.amount("12.001") }.isFailure)
    }
}
