package pe.edu.unmsm.siderurgica.ui.screens.drafts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.ui.common.*
import com.google.gson.Gson
import pe.edu.unmsm.siderurgica.domain.model.QuoteDraft
import pe.edu.unmsm.siderurgica.domain.model.SupplierDraft
import pe.edu.unmsm.siderurgica.domain.rules.QuoteRules

@Composable fun DraftScreen(vm: DraftViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Page("Borradores y sincronización", "Operaciones locales de tu cuenta.") {
        Notice(state.message)
        Notice("BORRADOR no se envía automáticamente. PENDIENTE espera conexión; ENVIADO confirma recepción. Los errores requieren revisión y reintento.")
        OutlinedButton(onClick = vm::sync) { Text("Sincronizar pendientes") }
        if (state.operations.isEmpty()) EmptyCard("No hay operaciones locales", "Guarda una cotización o proveedor, o envía una imagen desde Documentos.")
        state.operations.forEach { operation -> OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(operation.status, operation.status == "ERROR")
                Text(operation.title, style = MaterialTheme.typography.titleLarge)
                Text(when(operation.kind) { "QUOTE" -> "Cotización anterior"; "SUPPLIER" -> "Proveedor"; "BUSINESS" -> "Operación comercial / inventario"; "RECEIPT" -> "Comprobante PDF"; else -> "Imagen y documento" })
                if (operation.kind in listOf("BUSINESS", "RECEIPT")) {
                    val command = runCatching { Gson().fromJson(operation.payload, pe.edu.unmsm.siderurgica.domain.model.BusinessCommand::class.java) }.getOrNull()
                    if (command != null) {
                        if (command.client.isNotBlank()) Text("Cliente: ${command.client} · ${command.ruc}")
                        if (command.supplierRuc.isNotBlank()) Text("Proveedor: ${command.supplierRuc} · presupuesto ${money(command.budgetCents)}")
                        command.lines.forEach { line -> Text("Producto ${line.productId.take(12)} · ${line.quantity} × ${money(line.priceCents)} · descuento ${line.discountPercent} %") }
                        if (command.state.isNotBlank()) {
                            val kind = if (command.action == "QUOTE_STATE") "quotes" else "orders"
                            Text("Decisión: ${pe.edu.unmsm.siderurgica.ui.screens.business.commercialStatus(kind, command.state)}")
                            if (command.reason.isNotBlank()) Text("Sustento: ${command.reason}")
                            if (command.responseChannel.isNotBlank()) Text("Canal: ${command.responseChannel}")
                        }
                        if (command.type.isNotBlank()) Text("${command.type} · cantidad ${command.quantity} · ${command.reason}")
                        if (command.target.isNotBlank()) Text("Referencia: ${command.target}")
                        if (operation.kind == "RECEIPT") Text("Tipo: ${command.receiptType}")
                    }
                }
                if (operation.kind == "QUOTE") {
                    val draft = runCatching { Gson().fromJson(operation.payload, QuoteDraft::class.java) }.getOrNull()
                    if (draft != null) {
                        Text("${draft.quantity} unidades · RUC ${draft.ruc.ifBlank { "No indicado" }}")
                        val amount = runCatching { QuoteRules.amounts(draft.quantity, draft.expectedPriceCents ?: 0) }.getOrNull()
                        if (amount != null) Text("Total esperado: ${money(amount.totalCents)} sin IGV")
                    }
                }
                if (operation.kind == "SUPPLIER") {
                    val draft = runCatching { Gson().fromJson(operation.payload, SupplierDraft::class.java) }.getOrNull()
                    if (draft != null) { Text("RUC ${draft.ruc}"); if (draft.address.isNotBlank()) Text(draft.address) }
                }
                Text("Intentos: ${operation.attempts}")
                Text(operation.message, style = MaterialTheme.typography.bodySmall)
                if (operation.status in listOf("BORRADOR", "ERROR")) ConfirmButton("Enviar o reintentar", "Se enviarán estos datos a Firebase conservando el mismo identificador. Revisa los mensajes de error antes de reenviar.", !state.busy) { vm.send(operation.id) }
                if (operation.status in listOf("BORRADOR", "ERROR", "ENVIADO")) ConfirmButton("Eliminar registro local", "No se elimina ningún dato confirmado en Firebase. Si ya hubo un envío, comprueba su resultado antes de crear otra operación.", !state.busy) { vm.delete(operation.id) }
            }
        } }
    }
}
