package pe.edu.unmsm.siderurgica.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
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
    single { PurchaseEvidenceRepository(androidContext(),get(),get()) }
    single { DocumentRepository(get(), get(), get()) }
    single<RucApi> { Retrofit.Builder().baseUrl("https://api.decolecta.com/")
        .client(okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,java.util.concurrent.TimeUnit.SECONDS).callTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java) }
    single { RucLookup(api={get<RucApi>()},credential={pe.edu.unmsm.siderurgica.BuildConfig.DECOLECTA_TOKEN}) }
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
