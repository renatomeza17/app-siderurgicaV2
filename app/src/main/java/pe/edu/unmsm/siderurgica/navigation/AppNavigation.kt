@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package pe.edu.unmsm.siderurgica.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.fragment.app.FragmentActivity
import pe.edu.unmsm.siderurgica.ui.security.BiometricGate
import pe.edu.unmsm.siderurgica.ui.screens.suppliers.SupplierScreen
import pe.edu.unmsm.siderurgica.ui.screens.documents.DocumentScreen
import pe.edu.unmsm.siderurgica.ui.screens.drafts.DraftScreen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import pe.edu.unmsm.siderurgica.firebase.FirebaseServices
import pe.edu.unmsm.siderurgica.ui.screens.auth.*
import pe.edu.unmsm.siderurgica.ui.screens.home.*
import pe.edu.unmsm.siderurgica.ui.screens.products.ProductScreen
import pe.edu.unmsm.siderurgica.ui.screens.quotes.QuoteScreen
import pe.edu.unmsm.siderurgica.ui.screens.sales.SalesScreen
import pe.edu.unmsm.siderurgica.ui.common.*

private data class Tab(val route: String, val title: String, val icon: ImageVector)
@Composable fun AppNavigation(activity: FragmentActivity, vm: SessionViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val services: FirebaseServices = koinInject()
    if (services.configured && state.stage == SessionStage.READY) {
        key(state.account?.uid, state.profile?.role, state.profile?.active) {
            BiometricGate(activity, state.account!!.uid, vm::logout) { enabled, available, change ->
                BusinessShell(state, vm, enabled, available, change)
            }
        }
    } else Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!services.configured) Page("Configura Firebase") { Text("Agrega app/google-services.json, sincroniza Gradle y vuelve a ejecutar. Revisa CONFIGURACION_FIREBASE.md.") }
            else when (state.stage) {
                SessionStage.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                SessionStage.SIGNED_OUT -> key("signed-out") {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "login") {
                        composable("login") { LoginScreen(onRegister = { nav.navigate("register") { launchSingleTop = true } }) }
                        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }) }
                    }
                }
                SessionStage.VERIFY_EMAIL -> VerificationScreen(state, vm)
                SessionStage.PROFILE_ERROR -> Page("No pudimos cargar tu perfil") {
                    Notice(state.message)
                    Button(onClick = vm::refresh, enabled = !state.busy) { Text("Reintentar") }
                    TextButton(onClick = vm::logout) { Text("Cerrar sesión") }
                }
                SessionStage.READY -> Unit
            }
        }
    }
}
@Composable private fun BusinessShell(state: SessionState, vm: SessionViewModel, biometric: Boolean, biometricAvailable: Boolean, onBiometric: (Boolean) -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val profile = state.profile ?: return
    val tabs = buildList {
        add(Tab("home", "Inicio", Icons.Default.Home))
        if (profile.canSell) add(Tab("quotes", "Ventas", Icons.Default.ShoppingCart))
        if (profile.canReadProducts) add(Tab("products", "Stock", Icons.Default.List))
        add(Tab("more", "Más", Icons.Default.Menu))
    }
    val pending = mapOf("purchases" to "Compras", "movements" to "Movimientos de stock")
    val titles = mapOf("home" to "Siderúrgica Perú", "quotes" to "Gestión comercial", "products" to "Inventario", "more" to "Más opciones", "orders" to "Pedidos", "sales" to "Ventas", "suppliers" to "Proveedores", "documents" to "Documentos y códigos", "drafts" to "Borradores y sincronización") + pending
    fun navigate(destination: String) {
        if (tabs.any { it.route == destination }) nav.navigate(destination) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        } else nav.navigate(destination) { launchSingleTop = true }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(titles[route] ?: "Siderúrgica Perú") },
            navigationIcon = { if (route in pending || route in listOf("orders", "sales", "suppliers", "documents", "drafts")) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
            actions = { IconButton(onClick = { navigate("more") }) { Icon(Icons.Default.AccountCircle, "Mi cuenta") } }) },
        bottomBar = { NavigationBar {
            tabs.forEach { tab -> NavigationBarItem(selected = route == tab.route || (tab.route == "quotes" && route in listOf("orders", "sales")) || (tab.route == "more" && (route in pending || route in listOf("suppliers", "documents", "drafts"))),
                onClick = { navigate(tab.route) }, icon = { Icon(tab.icon, tab.title) }, label = { Text(tab.title) }) }
        } },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { HomeScreen(state, ::navigate) }
                composable("more") { MoreScreen(state, vm, ::navigate, biometric, biometricAvailable, onBiometric) }
                composable("products") {
                    if (profile.canReadProducts) ProductScreen(profile.canManageProducts, { navigate("home") }, onPurchases={navigate("purchases")})
                    else AccessUnavailable { navigate("home") }
                }
                composable("quotes") {
                    if (profile.canSell) pe.edu.unmsm.siderurgica.ui.screens.business.BusinessScreen("quotes", ::navigate)
                    else AccessUnavailable { navigate("home") }
                }
                composable("orders") {
                    if (profile.canSell) pe.edu.unmsm.siderurgica.ui.screens.business.BusinessScreen("orders", ::navigate) else AccessUnavailable { navigate("home") }
                }
                composable("sales") {
                    if (profile.canSell) pe.edu.unmsm.siderurgica.ui.screens.business.BusinessScreen("sales", ::navigate) else AccessUnavailable { navigate("home") }
                }
                composable("suppliers") { if (profile.canManageProducts) SupplierScreen() else AccessUnavailable { navigate("home") } }
                composable("documents") { if (profile.canManageProducts) DocumentScreen() else AccessUnavailable { navigate("home") } }
                composable("drafts") { DraftScreen() }
                composable("legacy") {
                    if (profile.canSell) QuoteScreen({ nav.popBackStack() }, onOrders = { navigate("legacyOrders") }, onSales = { navigate("legacySales") }) else AccessUnavailable { navigate("home") }
                }
                composable("legacyOrders") { if (profile.canSell) SalesScreen(false, { navigate(if (it=="orders") "legacyOrders" else if (it=="sales") "legacySales" else "legacy") }) else AccessUnavailable { navigate("home") } }
                composable("legacySales") { if (profile.canSell) SalesScreen(true, { navigate(if (it=="orders") "legacyOrders" else if (it=="sales") "legacySales" else "legacy") }) else AccessUnavailable { navigate("home") } }
                pending.forEach { (destination, _) -> composable(destination) {
                    if (profile.canManageProducts) pe.edu.unmsm.siderurgica.ui.screens.business.BusinessScreen(destination, ::navigate) else AccessUnavailable { navigate("home") }
                } }

            }
        }
    }
}
@Composable private fun AccessUnavailable(back: () -> Unit) { Page("Acceso no disponible") { TextButton(onClick = back) { Text("Volver al inicio") } } }
