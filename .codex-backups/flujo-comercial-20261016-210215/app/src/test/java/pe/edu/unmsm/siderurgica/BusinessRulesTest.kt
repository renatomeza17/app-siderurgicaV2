package pe.edu.unmsm.siderurgica
import org.junit.Assert.*
import org.junit.Test
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.rules.BusinessRules

class BusinessRulesTest {
    private val products=(1..4).map{Product("p$it","Acero $it","General",101,10,5)}
    @Test fun multiLineDiscountAndTaxUseHalfUpCents(){
        val lines=BusinessRules.lines(listOf(BusinessLineDraft("p1",2,101,5),BusinessLineDraft("p2",1,101,0)),products)
        assertEquals(192L,lines[0].netCents)
        assertEquals(Triple(293L,53L,346L),BusinessRules.total(lines))
    }
    @Test fun changedPriceAndDuplicateProductsAreRejected(){
        assertThrows(IllegalArgumentException::class.java){BusinessRules.lines(listOf(BusinessLineDraft("p1",1,100)),products)}
        assertThrows(IllegalArgumentException::class.java){BusinessRules.lines(List(2){BusinessLineDraft("p1",1,101)},products)}
        assertThrows(IllegalArgumentException::class.java){BusinessRules.lines(products.map{BusinessLineDraft(it.id,1,101)},products)}
    }
    @Test fun purchaseUsesNegotiatedPriceButRequiresNoExtraDiscount(){
        assertEquals(150L,BusinessRules.lines(listOf(BusinessLineDraft("p1",3,50)),products,true).single().netCents)
        assertThrows(IllegalArgumentException::class.java){BusinessRules.lines(listOf(BusinessLineDraft("p1",1,50,1)),products,true)}
    }
    @Test fun staleAdjustmentCannotEraseInterveningStockMovement(){
        assertThrows(IllegalArgumentException::class.java){BusinessRules.stock("AJUSTE",12,8,10)}
        assertEquals(0L,BusinessRules.stock("AJUSTE",12,0,12))
        assertEquals(15L,BusinessRules.stock("COMPRA",10,5))
        assertEquals(5L,BusinessRules.stock("VENTA",10,5))
        assertThrows(IllegalArgumentException::class.java){BusinessRules.stock("VENTA",1,2)}
    }
    @Test fun clientCannotBeEmptyOrHaveInvalidRuc(){
        BusinessRules.client("Cliente local","")
        assertThrows(IllegalArgumentException::class.java){BusinessRules.client("","")}
        assertThrows(IllegalArgumentException::class.java){BusinessRules.client("Cliente","12345678901")}
    }
}
