package pe.edu.unmsm.siderurgica

import org.junit.Assert.*
import org.junit.Test
import pe.edu.unmsm.siderurgica.domain.rules.SalesRules

class SalesRulesTest {
    @Test fun taxUsesNetAmountAndHalfUp() {
        assertEquals(171L, SalesRules.taxCents(950))
        assertEquals(1121L, SalesRules.totalCents(950))
        assertEquals(5L, SalesRules.taxCents(25))
        assertEquals(118_000_000_000_000L, SalesRules.totalCents(100_000_000_000_000))
    }
    @Test fun insufficientStockAndNegativeQuantityAreRejected() {
        assertEquals(4L, SalesRules.stockAfterApproval(10, 6))
        assertTrue(runCatching { SalesRules.stockAfterApproval(5, 6) }.isFailure)
        assertTrue(runCatching { SalesRules.stockAfterApproval(5, 0) }.isFailure)
    }
    @Test fun stateGuardStopsInvalidTransitions() {
        SalesRules.requireState("PENDIENTE", "PENDIENTE")
        assertTrue(runCatching { SalesRules.requireState("FACTURADO", "PENDIENTE") }.isFailure)
        assertTrue(runCatching { SalesRules.taxCents(0) }.isFailure)
    }
}
