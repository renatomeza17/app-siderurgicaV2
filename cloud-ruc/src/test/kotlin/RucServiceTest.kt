import kotlin.test.*
class RucServiceTest {
    private val ruc="20100070970"
    private val data=RucData(ruc,"Empresa de prueba","Lima","ACTIVO","HABIDO")
    private fun access(role:String="ALMACEN",verified:Boolean=true,active:Boolean=true)=RucAccess("uid",verified,active,role)
    @Test fun authorizationAndRucAreCheckedBeforeCallingProvider() {
        var calls=0
        fun service(user:RucAccess)=RucService({user},{null},{_,_->},{calls++;data},{_,_->})
        assertEquals(401,assertFailsWith<RucFailure>{service(access()).lookup("",ruc)}.status)
        for(user in listOf(access("RRHH"),access("PENDIENTE"),access(verified=false),access(active=false)))assertEquals(403,assertFailsWith<RucFailure>{service(user).lookup("Bearer session",ruc)}.status)
        assertEquals(400,assertFailsWith<RucFailure>{service(access()).lookup("Bearer session","20100070971")}.status)
        assertEquals(0,calls)
    }
    @Test fun validRolesUseProviderAndCachedResultAvoidsExternalConsumption() {
        var external=0;var reads=0;var cached:RucData?=null
        for(role in listOf("ADMIN","ALMACEN","VENTAS")){
            val service=RucService({access(role)},{cached},{_,remote->reads++;if(remote)external++},{data},{_,d->cached=d})
            assertEquals(data,service.lookup("Bearer session",ruc))
        }
        assertEquals(1,external);assertEquals(3,reads)
    }
    @Test fun quotaFailurePreventsProviderRequestAndMismatchedRucIsNotCached() {
        var saved=false;var called=false
        val limited=RucService({access()},{null},{_,_->throw RucFailure(429)},{called=true;data},{_,_->})
        assertEquals(429,assertFailsWith<RucFailure>{limited.lookup("Bearer session",ruc)}.status);assertFalse(called)
        val wrong=RucService({access()},{null},{_,_->},{data.copy(numero_documento="wrong")},{_,_->saved=true})
        assertEquals(502,assertFailsWith<RucFailure>{wrong.lookup("Bearer session",ruc)}.status);assertFalse(saved)
    }
}
