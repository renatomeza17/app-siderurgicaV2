package pe.edu.unmsm.siderurgica.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.edu.unmsm.siderurgica.ui.screens.auth.*
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable fun MoreScreen(state: SessionState, vm: SessionViewModel, navigate: (String) -> Unit, biometric: Boolean, biometricAvailable: Boolean, onBiometric: (Boolean) -> Unit) {
    val profile = state.profile ?: return
    var edit by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
    var logout by remember { mutableStateOf(false) }
    Page("Más opciones", "Organiza tu trabajo y administra tu perfil.") {
        OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AccountCircle, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                Text(profile.email)
                StatusBadge(profile.role.name)
                TextButton(onClick = { edit = !edit }) { Text(if (edit) "Ocultar edición" else "Editar nombre") }
                if (edit) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, enabled = !state.busy)
                    Button(onClick = { vm.updateName(name) }, enabled = !state.busy) { Text("Guardar") }
                }
            }
        }
        Notice(state.message)
        SectionTitle("Gestión operativa")
        if (profile.canManageProducts) {
            ActionCard("Proveedores", "Registro, consulta RUC y borradores", Icons.Default.Person) { navigate("suppliers") }
            ActionCard("Compras", "Órdenes, presupuesto y recepción", Icons.Default.ShoppingCart) { navigate("purchases") }
            ActionCard("Documentos y códigos", "Cámara, OCR, QR y guardado", Icons.Default.Edit) { navigate("documents") }
            ActionCard("Movimientos de stock", "Entradas, salidas y ajustes", Icons.Default.List) { navigate("movements") }
        }
        if (profile.canSell) {
            ActionCard("Pedidos", "Aprobación, stock y seguimiento", Icons.Default.List) { navigate("orders") }
            ActionCard("Ventas", "Ventas generadas desde pedidos", Icons.Default.ShoppingCart) { navigate("sales") }
        }
        ActionCard("Borradores y sincronización", "SQLite y envíos con WorkManager", Icons.Default.Edit) { navigate("drafts") }
        if (profile.canSell) ActionCard("Histórico de la etapa anterior", "Registros comerciales de un solo producto", Icons.Default.List) { navigate("legacy") }
        pe.edu.unmsm.siderurgica.notifications.NotificationSetup(profile.uid, profile.canManageProducts)
        SectionTitle("Seguridad del dispositivo")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Activar biometría", style = MaterialTheme.typography.titleMedium)
                    Text(if (biometricAvailable) "Protege la sesión al reabrir o volver a la app." else "Configura una huella compatible en Ajustes del teléfono.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = biometric, onCheckedChange = onBiometric, enabled = biometricAvailable || biometric)
            }
        }
        SectionTitle("Cuenta")
        ActionCard("Actualizar permisos", "Consultar el perfil vigente en Firebase", Icons.Default.Refresh) { vm.refresh() }
        ActionCard("Cerrar sesión", "Salir de esta cuenta en el dispositivo", Icons.Default.ExitToApp) { logout = true }
        Text("Siderúrgica Perú · Android y Firebase", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("¿Cerrar sesión?") }, text = { Text("Podrás volver a ingresar con tu correo y contraseña.") },
        confirmButton = { TextButton(onClick = { logout = false; vm.logout() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancelar") } })
}
