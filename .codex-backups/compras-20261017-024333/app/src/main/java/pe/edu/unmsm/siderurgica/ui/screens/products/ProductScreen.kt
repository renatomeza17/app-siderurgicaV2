package pe.edu.unmsm.siderurgica.ui.screens.products

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable fun ProductScreen(canManage: Boolean, onBack: () -> Unit, vm: ProductViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("0") }
    var minimum by rememberSaveable { mutableStateOf("10") }
    LaunchedEffect(state.saved) { if (state.saved) { form = false; name = ""; category = ""; price = ""; vm.dismissSaved() } }
    Page("Productos e inventario", "El stock mostrado corresponde a la última consulta confirmada.") {
        
        Notice(state.message)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = vm::refresh, enabled = !state.busy) { Text("Actualizar") }
        if (canManage) Button(onClick = { form = !form }, enabled = !state.busy) { Text(if (form) "Ocultar formulario" else "Nuevo producto") }
        if (form && canManage) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Producto") })
            OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("Categoría") })
            OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Precio S/") })
            OutlinedTextField(stock, { stock = it }, Modifier.fillMaxWidth(), label = { Text("Stock inicial") })
            OutlinedTextField(minimum, { minimum = it }, Modifier.fillMaxWidth(), label = { Text("Stock mínimo") })
            Button(onClick = { vm.save(name, category, price, stock, minimum) }, enabled = !state.busy) { Text("Registrar producto") }
        }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar producto o categoría") }, singleLine = true, shape = MaterialTheme.shapes.medium)
        val products = state.products.filter { it.name.contains(search, true) || it.category.contains(search, true) }
        Text("${products.size} productos", style = MaterialTheme.typography.labelLarge)
        if (products.isEmpty() && !state.busy) EmptyCard("Sin productos para mostrar", "Registra un producto o ajusta la búsqueda.")
        products.forEach { product ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text(product.category)
                    Text("${money(product.priceCents)} · ${product.stock} unidades")
                    StatusBadge(when { product.stock == 0L -> "Agotado"; product.stock <= product.minimumStock -> "Stock bajo"; else -> "Stock normal" }, product.stock <= product.minimumStock)
                }
            }
        }
    }
}
