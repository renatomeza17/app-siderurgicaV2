package pe.edu.unmsm.siderurgica

import org.junit.Assert.*
import org.junit.Test
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.rules.*

class DomainRulesTest {
    @Test fun discountThresholdsAndRounding() {
        val expected = mapOf(9L to 0L, 10L to 5L, 49L to 5L, 50L to 7L, 99L to 7L, 100L to 10L)
        expected.forEach { (quantity, discount) -> assertEquals(discount, QuoteRules.amounts(quantity, 101).discountPercent) }
        assertEquals(960L, QuoteRules.amounts(10, 101).totalCents)
        assertEquals(QuoteAmounts(1000, 5, 950), QuoteRules.amounts(10, 100))
    }
    @Test fun limitsPreventOverflow() {
        assertEquals(100_000_000_000_000L, QuoteRules.amounts(1_000_000, 100_000_000).subtotalCents)
        assertTrue(runCatching { QuoteRules.amounts(Long.MAX_VALUE, 100) }.isFailure)
        assertTrue(runCatching { QuoteRules.amounts(0, 100) }.isFailure)
    }
    @Test fun priceParsingAndPrecision() {
        assertEquals(1250L, QuoteRules.parsePrice("12,50"))
        assertTrue(runCatching { QuoteRules.parsePrice("1.001") }.isFailure)
        assertTrue(runCatching { QuoteRules.parsePrice("NaN") }.isFailure)
        assertTrue(runCatching { QuoteRules.parsePrice("0") }.isFailure)
    }
    @Test fun pendingAndInactiveUsersHaveNoBusinessAccess() {
        val pending = UserProfile("uid", "a@b.com", "Ana", Role.PENDIENTE, true)
        assertFalse(pending.canSell)
        assertFalse(pending.canManageProducts)
        assertFalse(pending.canReadProducts)
        val inactive = pending.copy(role = Role.ADMIN, active = false)
        assertFalse(inactive.canSell)
        assertFalse(inactive.canManageProducts)
        assertTrue(pending.copy(role = Role.VENTAS).canSell)
        assertFalse(pending.copy(role = Role.VENTAS).canManageProducts)
    }
    @Test fun validatesEmailAndRuc() {
        assertEquals("ana@example.com", InputRules.email(" ana@example.com "))
        assertTrue(runCatching { InputRules.email("ana") }.isFailure)
        assertTrue(InputRules.validRuc("20100070970"))
        assertFalse(InputRules.validRuc("20100070971"))
        assertTrue(runCatching { QuoteRules.validate(QuoteDraft("p", "Ana", "123", 1)) }.isFailure)
    }
}
