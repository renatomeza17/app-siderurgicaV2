package pe.edu.unmsm.siderurgica.ui.screens.quotes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.domain.model.QuoteDraft
import pe.edu.unmsm.siderurgica.domain.rules.QuoteRules
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable fun QuoteScreen(onBack: () -> Unit, onOrders: () -> Unit = {}, onSales: () -> Unit = {}, vm: QuoteViewModel = koinViewModel(), readOnly: Boolean = true) {
    val state by vm.state.collectAsStateWithLifecycle()
    var client by rememberSaveable { mutableStateOf("") }
    var ruc by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) { form = false; client = ""; ruc = ""; quantity = "1"; vm.dismissSaved() } }
    Page("Cotizaciones", "Histórico anterior: un producto por cotización. Usa Ventas para las operaciones nuevas.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("Cotizaciones") })
            FilterChip(selected = false, onClick = onOrders, label = { Text("Pedidos") })
            FilterChip(selected = false, onClick = onSales, label = { Text("Ventas") })
        }
        
        Notice(state.message)
        Notice("Puedes consultar datos guardados. Las aprobaciones y ventas requieren confirmación en línea.")
        if (state.retryPending) Notice("Hay un envío sin confirmar. Reintentar conserva los mismos datos mientras esta pantalla permanece abierta. Antes de salir o repetir tras reiniciar, revisa el listado.")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = vm::refresh, enabled = !state.busy) { Text("Actualizar") }
        Notice("Histórico de consulta. Registra las operaciones nuevas desde la pestaña Ventas.")
        if (form) {
            val editable = !state.busy && !state.retryPending
            OutlinedTextField(client, { client = it }, Modifier.fillMaxWidth(), label = { Text("Cliente") }, enabled = editable)
            OutlinedTextField(ruc, { ruc = it }, Modifier.fillMaxWidth(), label = { Text("RUC opcional") }, enabled = editable)
            Text("Selecciona un producto")
            if (state.products.isEmpty()) Text("Actualiza o solicita al almacén registrar productos.")
            state.products.forEach { p ->
                FilterChip(selected = selectedId == p.id, onClick = { selectedId = p.id }, enabled = editable,
                    label = { Text("${p.name} · ${money(p.priceCents)}") })
            }
            OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Cantidad") }, enabled = editable)
            val product = state.products.firstOrNull { it.id == selectedId }
            val amount = runCatching { QuoteRules.amounts(quantity.toLong(), product?.priceCents ?: 0) }.getOrNull()
            if (amount != null) Text("Subtotal ${money(amount.subtotalCents)} · Descuento ${amount.discountPercent}% · Total ${money(amount.totalCents)}")
            Text("Se confirmará con el precio del producto vigente en Firestore. No se modifica stock al cotizar.")
            Button(onClick = { vm.save(QuoteDraft(selectedId, client, ruc.trim(), quantity.toLongOrNull() ?: 0)) }, enabled = !state.busy) {
                Text("Guardar y sincronizar cotización")
            }
            OutlinedButton(onClick = { vm.save(QuoteDraft(selectedId, client, ruc.trim(), quantity.toLongOrNull() ?: 0), false) }, enabled = !state.busy) { Text("Guardar solo borrador") }
            Notice("El envío requiere conexión. Si el precio cambió, la operación quedará con error para que revises la propuesta.")
        }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar cliente o producto") }, singleLine = true, shape = MaterialTheme.shapes.medium)
        val quotes = state.quotes.filter { it.client.contains(search, true) || it.productName.contains(search, true) }
        Text("${quotes.size} cotizaciones", style = MaterialTheme.typography.labelLarge)
        if (quotes.isEmpty() && !state.busy) EmptyCard("Aún no hay cotizaciones", "Prepara una propuesta o ajusta tu búsqueda.")
        quotes.forEach { quote ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(quote.client, style = MaterialTheme.typography.titleMedium)
                    Text("${quote.productName} · ${quote.quantity} unidades")
                    Text(money(quote.totalCents), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    StatusBadge(quote.status)
                    Text("Descuento ${quote.discountPercent}% · ${quote.id.take(8)}")
                    if (!readOnly && quote.status == "PENDIENTE") {
                        ConfirmButton("Aprobar cotización", "Se aprobará la propuesta; aún no se modifica stock.", !state.busy) { vm.approve(quote.id) }
                        ConfirmButton("Rechazar cotización", "Esta cotización dejará de continuar hacia un pedido.", !state.busy) { vm.reject(quote.id) }
                    }
                    if (!readOnly && quote.status == "APROBADA") ConfirmButton("Crear pedido", "Se conserva el descuento acordado y se agrega IGV de 18 %. El stock se valida al aprobar el pedido.", !state.busy) { vm.convert(quote.id) }
                }
            }
        }
    }
}
