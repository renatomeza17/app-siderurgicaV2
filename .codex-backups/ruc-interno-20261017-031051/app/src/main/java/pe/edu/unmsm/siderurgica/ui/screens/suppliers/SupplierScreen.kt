package pe.edu.unmsm.siderurgica.ui.screens.suppliers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.domain.model.SupplierDraft
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable fun SupplierScreen(vm: SupplierViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var form by rememberSaveable { mutableStateOf(false) }
    var ruc by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    // Per-user provider token is transient: never persisted, logged or sent to Firestore.
    var token by remember { mutableStateOf("") }
    var apiOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.lookup) { state.lookup?.let { ruc = it.ruc; name = it.name; address = it.address } }
    LaunchedEffect(state.saved) { if (state.saved) { form = false; ruc = ""; name = ""; address = ""; contact = ""; phone = ""; vm.dismissSaved() } }
    Page("Proveedores", "Registro manual o consulta opcional por RUC.") {
        Notice(state.message)
        Notice("Los datos pueden provenir de la última consulta guardada. El RUC y los permisos se comprobarán en Firebase al enviar.")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = vm::refresh, enabled = !state.busy) { Text("Actualizar proveedores") }
        Button(onClick = { form = !form }, enabled = !state.busy) { Text(if (form) "Ocultar formulario" else "Registrar proveedor") }
        if (form) {
            OutlinedTextField(ruc, { ruc = it.filter(Char::isDigit).take(11) }, Modifier.fillMaxWidth(), label = { Text("RUC") })
            TextButton(onClick = { apiOpen = !apiOpen }) { Text("Consultar razón social por API") }
            if (apiOpen) {
                Notice("Servicio externo Decolecta. Necesitas tu propio token; úsalo solo para esta prueba y no lo compartas. Puedes continuar con registro manual.")
                PasswordField(token, { token = it }, "Token personal de Decolecta", !state.busy)
                OutlinedButton(onClick = { vm.lookup(ruc, token) }, enabled = !state.busy) { Text("Consultar RUC") }
                state.lookup?.let { Text("Estado ${it.status} · Condición ${it.condition}") }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Razón social") })
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Dirección") })
            OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), label = { Text("Contacto") })
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono") })
            Button(onClick = { vm.save(SupplierDraft(ruc, name.trim(), address.trim(), contact.trim(), phone.trim()), true) }, enabled = !state.busy) { Text("Guardar y sincronizar") }
            OutlinedButton(onClick = { vm.save(SupplierDraft(ruc, name.trim(), address.trim(), contact.trim(), phone.trim()), false) }, enabled = !state.busy) { Text("Guardar solo borrador") }
        }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar por razón social o RUC") })
        val suppliers = state.suppliers.filter { it.name.contains(search, true) || it.ruc.contains(search) }
        if (suppliers.isEmpty() && !state.busy) EmptyCard("Sin proveedores", "Registra un proveedor o ajusta la búsqueda.")
        suppliers.forEach { supplier -> OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(supplier.name, style = MaterialTheme.typography.titleLarge)
                Text("RUC ${supplier.ruc}")
                if (supplier.address.isNotBlank()) Text(supplier.address)
                if (supplier.contact.isNotBlank()) Text(supplier.contact)
                if (supplier.phone.isNotBlank()) Text(supplier.phone)
                StatusBadge("Registrado en Firebase")
            }
        } }
    }
}
