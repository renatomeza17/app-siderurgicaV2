import kotlin.test.*
class InventoryNotifierTest {
    @Test fun thresholdAndRearming(){
        assertFalse(shouldAlert(8,5,null))
        assertTrue(shouldAlert(5,5,null))
        assertFalse(shouldAlert(5,5,"5:5"))
        assertTrue(shouldAlert(4,5,"5:5"))
        assertTrue(shouldAlert(5,5,"NORMAL"))
    }
}
