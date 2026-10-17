package pe.edu.unmsm.siderurgica.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.domain.model.Role
import pe.edu.unmsm.siderurgica.ui.screens.auth.SessionState
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable fun HomeScreen(session: SessionState, navigate: (String) -> Unit, vm: HomeViewModel = koinViewModel()) {
    val profile = session.profile ?: return
    val data by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.uid, profile.role, profile.active) { vm.refresh(profile) }
    Page("Hola, ${profile.displayName}", "Este es el resumen de tu operación.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusBadge(profile.role.name); StatusBadge(if (profile.active) "Cuenta activa" else "Acceso suspendido", !profile.active) }
        if (!profile.active) Notice("Tu acceso está deshabilitado. Contacta al administrador.")
        else if (profile.role == Role.PENDIENTE) Notice("Tu cuenta está registrada. El administrador debe asignarte un rol empresarial.")
        if (data.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Notice(data.message)
        Notice(session.message)
        if (profile.canSell) Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PROPUESTAS COMERCIALES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                Text(data.quotes?.let { money(it.sumOf { q -> q.totalCents }) } ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text(data.quotes?.let { "${it.size} cotizaciones en la última consulta" } ?: "Actualiza para consultar los importes", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (profile.canSell) MetricCard("Cotizaciones", data.quotes?.count { it.status == "PENDIENTE" }?.toString() ?: "—", "Pendientes", Modifier.weight(1f)) { navigate("quotes") }
            if (profile.canReadProducts) MetricCard("Productos", data.products?.size?.toString() ?: "—", "En catálogo", Modifier.weight(1f)) { navigate("products") }
        }
        val critical = data.products?.count { it.stock <= it.minimumStock }
        if (critical != null && critical > 0) ActionCard("$critical productos con stock crítico", "Revisa disponibilidad y mínimo configurado", Icons.Default.Warning) { navigate("products") }
        SectionTitle("Continúa tu trabajo")
        if (profile.canSell) ActionCard("Cotizar y vender", "Prepara y consulta cotizaciones", Icons.Default.ShoppingCart) { navigate("quotes") }
        if (profile.canSell) {
            ActionCard("Pedidos", "Aprueba y confirma disponibilidad", Icons.Default.List) { navigate("orders") }
            ActionCard("Ventas", "Consulta las ventas generadas", Icons.Default.ShoppingCart) { navigate("sales") }
        }
        if (profile.canReadProducts) ActionCard("Consultar inventario", "Productos, disponibilidad y precio", Icons.Default.List) { navigate("products") }
        if (profile.canManageProducts) ActionCard("Abastecimiento", "Proveedores, presupuesto y recepción", Icons.Default.Build) { navigate("purchases") }
        ActionCard("Borradores y sincronización", "Revisa los envíos locales de esta cuenta", Icons.Default.Edit) { navigate("drafts") }
        if (profile.canManageProducts) ActionCard("Capturar documento", "Fotografía, OCR y códigos", Icons.Default.Edit) { navigate("documents") }
        ActionCard("Tu cuenta", "Perfil y permisos de acceso", Icons.Default.AccountCircle) { navigate("more") }
        OutlinedButton(onClick = { vm.refresh(profile) }, enabled = !data.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Actualizar resumen") }
        Text("Los indicadores corresponden a datos consultados en Firebase. No representan ventas facturadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun MetricCard(title: String, value: String, caption: String, modifier: Modifier, click: () -> Unit) {
    OutlinedCard(onClick = click, modifier = modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(caption, style = MaterialTheme.typography.bodySmall)
        }
    }
}
