package pe.edu.unmsm.siderurgica
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import pe.edu.unmsm.siderurgica.data.remote.*
class RucApiTest {
    @Test fun directRequestUsesConfiguredProviderCredentialWithoutAskingWorker()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"numero_documento":"20100070970","razon_social":"Proveedor","direccion":"Lima"}"""))
            val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            val result=RucLookup({api},{"test-provider-credential"}).lookup("20100070970")
            val request=server.takeRequest()
            assertEquals("/v1/sunat/ruc?numero=20100070970",request.path)
            assertEquals("Bearer test-provider-credential",request.getHeader("Authorization"))
            assertFalse(request.path!!.contains("token"));assertEquals("Proveedor",result.name)
        }finally{server.shutdown()}
    }
    @Test fun invalidRucMissingCredentialAndMismatchedResponseAreRejected()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            assertTrue(runCatching{RucLookup({api},{"test-provider-credential"}).lookup("123")}.isFailure)
            assertTrue(runCatching{RucLookup({api},{""}).lookup("20100070970")}.isFailure)
            assertEquals(0,server.requestCount)
            server.enqueue(MockResponse().setBody("""{"numero_documento":"20601030013","razon_social":"Otra empresa"}"""))
            assertTrue(runCatching{RucLookup({api},{"test-provider-credential"}).lookup("20100070970")}.isFailure)
        }finally{server.shutdown()}
    }
    @Test fun providerErrorBodiesAreNeverShownToWorker()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(429).setBody("secret-upstream-details"))
            val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            val error=runCatching{RucLookup({api},{"test-provider-credential"}).lookup("20100070970")}.exceptionOrNull()!!
            assertTrue(error.message!!.contains("límite"));assertFalse(error.message!!.contains("secret-upstream"))
        }finally{server.shutdown()}
    }
    @Test fun bearerPrefixIsNormalizedAndAuthorizationFailuresDoNotRevealCredential()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("test-private-provider-details"))
            val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            val error=runCatching{RucLookup({api},{" Bearer test-provider-credential "}).lookup("20100070970")}.exceptionOrNull()!!
            assertEquals("Bearer test-provider-credential",server.takeRequest().getHeader("Authorization"))
            assertTrue(error.message!!.contains("configuración"))
            assertFalse(error.message!!.contains("test-private"));assertFalse(error.message!!.contains("test-provider-credential"))
        }finally{server.shutdown()}
    }
}
