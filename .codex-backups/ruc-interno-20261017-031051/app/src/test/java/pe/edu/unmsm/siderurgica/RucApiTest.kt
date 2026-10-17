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
    @Test fun requestUsesBearerHeaderAndReturnedRucMatches() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"numero_documento":"20100070970","razon_social":"Proveedor de prueba","direccion":"Lima","estado":"ACTIVO","condicion":"HABIDO"}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            val result = RucLookup(api).lookup("20100070970", "personal-test-token")
            val request = server.takeRequest()
            assertEquals("/v1/sunat/ruc?numero=20100070970", request.path)
            assertEquals("Bearer personal-test-token", request.getHeader("Authorization"))
            assertFalse(request.path!!.contains("token"))
            assertEquals("Proveedor de prueba", result.name)
        } finally { server.shutdown() }
    }
    @Test fun invalidOrMismatchedResponseIsRejected() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
            assertTrue(runCatching { RucLookup(api).lookup("20100070970", "") }.isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"numero_documento":"20601030013","razon_social":"Otra empresa"}"""))
            assertTrue(runCatching { RucLookup(api).lookup("20100070970", "personal-test-token") }.isFailure)
        } finally { server.shutdown() }
    }
}
