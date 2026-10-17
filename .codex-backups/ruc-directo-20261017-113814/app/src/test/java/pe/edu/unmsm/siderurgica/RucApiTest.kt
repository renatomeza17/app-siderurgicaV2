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
    @Test fun proxyReceivesFirebaseSessionAutomaticallyAndNeverProviderSecret()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"numero_documento":"20100070970","razon_social":"Proveedor","direccion":"Lima"}"""))
            val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            val result=RucLookup({api},{"firebase-test-session"}).lookup("20100070970")
            val request=server.takeRequest()
            assertEquals("/ruc?numero=20100070970",request.path)
            assertEquals("Bearer firebase-test-session",request.getHeader("Authorization"))
            assertFalse(request.path!!.contains("token"));assertEquals("Proveedor",result.name)
        }finally{server.shutdown()}
    }
    @Test fun invalidRucMissingSessionAndMismatchedResponseAreRejected()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            assertTrue(runCatching{RucLookup({api},{"session"}).lookup("123")}.isFailure)
            assertTrue(runCatching{RucLookup({api},{""}).lookup("20100070970")}.isFailure)
            assertEquals(0,server.requestCount)
            server.enqueue(MockResponse().setBody("""{"numero_documento":"20601030013","razon_social":"Otra empresa"}"""))
            assertTrue(runCatching{RucLookup({api},{"session"}).lookup("20100070970")}.isFailure)
        }finally{server.shutdown()}
    }
    @Test fun providerErrorBodiesAreNeverShownToWorker()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(429).setBody("secret-upstream-details"))
            val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            val error=runCatching{RucLookup({api},{"session"}).lookup("20100070970")}.exceptionOrNull()!!
            assertTrue(error.message!!.contains("límite"));assertFalse(error.message!!.contains("secret-upstream"))
        }finally{server.shutdown()}
    }
}
