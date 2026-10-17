package pe.edu.unmsm.siderurgica.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import kotlinx.coroutines.tasks.await
import androidx.room.Room
import pe.edu.unmsm.siderurgica.data.local.*
import pe.edu.unmsm.siderurgica.data.remote.*
import pe.edu.unmsm.siderurgica.ui.screens.suppliers.SupplierViewModel
import pe.edu.unmsm.siderurgica.ui.screens.documents.DocumentViewModel
import pe.edu.unmsm.siderurgica.ui.screens.drafts.DraftViewModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import pe.edu.unmsm.siderurgica.firebase.*
import pe.edu.unmsm.siderurgica.domain.repository.*
import pe.edu.unmsm.siderurgica.domain.usecase.*
import pe.edu.unmsm.siderurgica.ui.screens.auth.*
import pe.edu.unmsm.siderurgica.ui.screens.home.HomeViewModel
import pe.edu.unmsm.siderurgica.ui.screens.products.ProductViewModel
import pe.edu.unmsm.siderurgica.ui.screens.quotes.QuoteViewModel
import pe.edu.unmsm.siderurgica.ui.screens.sales.SalesViewModel

val appModule = module {
    single { Room.databaseBuilder(androidContext(), LocalDatabase::class.java, "siderurgica-firebase-local.db").build() }
    single { get<LocalDatabase>().dao() }
    single<pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository> { FirestoreBusinessRepository(get(), get()) }
    single { pe.edu.unmsm.siderurgica.data.documents.ReceiptRepository(androidContext(), get()) }
    single { pe.edu.unmsm.siderurgica.data.documents.QuotePdfRepository(androidContext(), get()) }
    viewModel { pe.edu.unmsm.siderurgica.ui.screens.business.BusinessViewModel(get(), get(), get(), get(), get()) }
    single { DeviceCache(androidContext(), get()) }
    single { QueueRepository(androidContext(), get(), get()) }
    single { FirestoreSupplierRepository(get(), get()) }
    single { DocumentRepository(get(), get(), get()) }
    single { RucLookup(api = {
        val url=pe.edu.unmsm.siderurgica.BuildConfig.RUC_PROXY_BASE_URL
        check(url.isNotBlank()) { "La consulta RUC aún no está habilitada. Puedes completar los datos manualmente." }
        val parsed=java.net.URI(url)
        require(parsed.scheme=="https" && !parsed.host.isNullOrBlank() && parsed.rawUserInfo==null && parsed.query==null && parsed.fragment==null) { "Configuración de consulta RUC inválida." }
        Retrofit.Builder().baseUrl(url.trimEnd('/')+"/")
            .client(okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,java.util.concurrent.TimeUnit.SECONDS).callTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java)
    }, session = {
        val services=get<FirebaseServices>()
        val user=services.auth.currentUser?:error("Inicia sesión para consultar.")
        val token=user.getIdToken(false).await().token?:error("Inicia sesión nuevamente.")
        check(services.auth.currentUser?.uid==user.uid) { "La sesión cambió durante la consulta." }
        token
    }) }
    viewModel { SupplierViewModel(get(), get(), get()) }
    viewModel { DocumentViewModel(androidContext(), get()) }
    viewModel { DraftViewModel(get()) }
    single { FirebaseServices(androidContext()) }
    single<AuthRepository> { FirebaseAuthRepository(get()) }
    single<ProfileRepository> { FirestoreProfileRepository(get(), get()) }
    single<CommerceRepository> { FirestoreCommerceRepository(get(), get()) }
    single<SalesRepository> { FirestoreSalesRepository(get()) }
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    viewModel { SessionViewModel(get(), get(), get()) }
    viewModel { LoginViewModel(get(), get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { ProductViewModel(get()) }
    viewModel { QuoteViewModel(get(), get(), get()) }
    viewModel { SalesViewModel(get()) }
}
