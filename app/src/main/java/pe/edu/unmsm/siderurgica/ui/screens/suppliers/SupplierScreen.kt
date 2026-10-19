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

@Composable
fun SupplierScreen(
    vm: SupplierViewModel = koinViewModel()
) {
    // 1. Estado reactivo del ViewModel
    val state by vm.state.collectAsStateWithLifecycle()

    // 2. Estados locales del formulario y búsqueda (persisten ante giros de pantalla)
    var form by rememberSaveable { mutableStateOf(false) }
    var ruc by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }

    // Estado local para la sección desplegable de consulta RUC
    var apiOpen by remember { mutableStateOf(false) }

    // 3. Efecto: Autocompletar campos cuando la API de RUC devuelve datos
    LaunchedEffect(state.lookup) {
        state.lookup?.let {
            ruc = it.ruc
            name = it.name
            address = it.address
        }
    }

    // 4. Efecto: Limpiar campos y cerrar formulario cuando el guardado es exitoso
    LaunchedEffect(state.saved) {
        if (state.saved) {
            form = false
            ruc = ""
            name = ""
            address = ""
            contact = ""
            phone = ""
            vm.dismissSaved()
        }
    }

    // 5. Estructura visual de la pantalla
    Page(
        title = "Proveedores",
        subtitle = "Registro manual o consulta opcional por RUC."
    ) {
        // Mensajes y alertas del estado
        Notice(state.message)
        Notice("Los datos pueden provenir de la última consulta guardada. El RUC y los permisos se comprobarán en Firebase al enviar.")

        // Indicador de carga
        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Acciones principales
        OutlinedButton(
            onClick = vm::refresh,
            enabled = !state.busy
        ) {
            Text("Actualizar proveedores")
        }

        Button(
            onClick = { form = !form },
            enabled = !state.busy
        ) {
            Text(if (form) "Ocultar formulario" else "Registrar proveedor")
        }

        // Formulario condicional
        if (form) {
            OutlinedTextField(
                value = ruc,
                onValueChange = { input ->
                    ruc = input.filter(Char::isDigit).take(11)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("RUC") }
            )

            TextButton(onClick = { apiOpen = !apiOpen }) {
                Text("Consultar razón social por RUC")
            }

            if (apiOpen) {
                Notice("Consulta los datos de la empresa por RUC y revísalos antes de guardar. Requiere conexión.")

                OutlinedButton(
                    onClick = { vm.lookup(ruc) },
                    enabled = !state.busy
                ) {
                    Text("Consultar RUC")
                }

                state.lookup?.let {
                    Text("Estado ${it.status} · Condición ${it.condition}")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Razón social") }
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dirección") }
            )

            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Contacto") }
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Teléfono") }
            )

            Button(
                onClick = {
                    vm.save(
                        draft = SupplierDraft(
                            ruc = ruc,
                            name = name.trim(),
                            address = address.trim(),
                            contact = contact.trim(),
                            phone = phone.trim()
                        ),
                        send = true
                    )
                },
                enabled = !state.busy
            ) {
                Text("Guardar y sincronizar")
            }

            OutlinedButton(
                onClick = {
                    vm.save(
                        draft = SupplierDraft(
                            ruc = ruc,
                            name = name.trim(),
                            address = address.trim(),
                            contact = contact.trim(),
                            phone = phone.trim()
                        ),
                        send = false
                    )
                },
                enabled = !state.busy
            ) {
                Text("Guardar solo borrador")
            }
        }

        // Filtro y búsqueda
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar por razón social o RUC") }
        )

        val filteredSuppliers = state.suppliers.filter { supplier ->
            supplier.name.contains(search, ignoreCase = true) || supplier.ruc.contains(search)
        }

        if (filteredSuppliers.isEmpty() && !state.busy) {
            EmptyCard(
                "Sin proveedores",
                "Registra un proveedor o ajusta la búsqueda."
            )
        }

        // Listado de tarjetas de proveedores
        filteredSuppliers.forEach { supplier ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = supplier.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text("RUC ${supplier.ruc}")

                    if (supplier.address.isNotBlank()) {
                        Text(supplier.address)
                    }
                    if (supplier.contact.isNotBlank()) {
                        Text(supplier.contact)
                    }
                    if (supplier.phone.isNotBlank()) {
                        Text(supplier.phone)
                    }

                    StatusBadge("Registrado en Firebase")
                }
            }
        }
    }
}