package pe.edu.unmsm.siderurgica.ui.screens.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable fun SalesScreen(showSales: Boolean, navigate: (String) -> Unit, vm: SalesViewModel = koinViewModel(), readOnly: Boolean = true) {
    val state by vm.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable(showSales) { mutableStateOf("Todos") }
    Page(if (showSales) "Ventas registradas" else "Tus pedidos", if (showSales) "Ventas generadas desde pedidos aprobados." else "Gestiona la aprobación, stock y facturación.") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(false, { navigate("quotes") }, label = { Text("Cotizaciones") })
            FilterChip(!showSales, { navigate("orders") }, label = { Text("Pedidos") })
            FilterChip(showSales, { navigate("sales") }, label = { Text("Ventas") })
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Notice(state.message)
        OutlinedButton(onClick = vm::refresh, enabled = !state.busy) { Text("Actualizar listados") }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar cliente o producto") }, singleLine = true)
        if (showSales) {
            val items = state.sales.filter { it.client.contains(search, true) || it.productName.contains(search, true) }
            Text("${items.size} ventas", style = MaterialTheme.typography.labelLarge)
            if (items.isEmpty() && !state.busy) EmptyCard("Aún no hay ventas", "Genera una venta desde un pedido aprobado.")
            items.forEach { sale -> OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VEN-${sale.id.take(8)}", style = MaterialTheme.typography.labelLarge)
                    StatusBadge(sale.status)
                    Text(sale.client, style = MaterialTheme.typography.titleLarge)
                    Text("${sale.productName} · ${sale.quantity} unidades")
                    Text("Subtotal: ${money(sale.subtotalCents)}")
                    Text("IGV: ${money(sale.taxCents)}")
                    Text(money(sale.totalCents), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Registro del formato anterior. Los comprobantes de operaciones nuevas están disponibles en Ventas.", style = MaterialTheme.typography.bodySmall)
                }
            } }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "PENDIENTE", "APROBADO", "FACTURADO").forEach { value ->
                    FilterChip(filter == value, { filter = value }, label = { Text(value.lowercase()) })
                }
            }
            val items = state.orders.filter { (it.client.contains(search, true) || it.productName.contains(search, true)) && (filter == "Todos" || it.status == filter) }
            Text("${items.size} pedidos", style = MaterialTheme.typography.labelLarge)
            if (items.isEmpty() && !state.busy) EmptyCard("Sin pedidos para mostrar", "Aprueba una cotización y utiliza Crear pedido.")
            items.forEach { order -> OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PED-${order.id.take(8)}", style = MaterialTheme.typography.labelLarge)
                    StatusBadge(order.status)
                    Text(order.client, style = MaterialTheme.typography.titleLarge)
                    Text("${order.productName} · ${order.quantity} unidades")
                    Text("Subtotal después del descuento: ${money(order.subtotalCents)}")
                    Text("IGV (18 %): ${money(order.taxCents)}")
                    Text(money(order.totalCents), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    if (!readOnly && order.status == "PENDIENTE") {
                        ConfirmButton("Aprobar pedido", "Se comprobará el stock vigente y se descontarán ${order.quantity} unidades. La operación requiere conexión.", !state.busy) { vm.approve(order.id) }
                        ConfirmButton("Rechazar pedido", "Se cerrará este pedido sin descontar stock.", !state.busy) { vm.stop(order.id, "RECHAZADO") }
                        ConfirmButton("Anular pedido", "Se anulará este pedido pendiente sin descontar stock.", !state.busy) { vm.stop(order.id, "ANULADO") }
                    }
                    if (!readOnly && order.status == "APROBADO") ConfirmButton("Generar venta", "Se registrará una venta por ${money(order.totalCents)} y el pedido quedará FACTURADO. No se vuelve a descontar stock.", !state.busy) { vm.sell(order.id) }
                }
            } }
        }
    }
}
