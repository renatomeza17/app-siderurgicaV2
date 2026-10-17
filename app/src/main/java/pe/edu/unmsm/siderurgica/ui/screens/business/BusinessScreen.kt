package pe.edu.unmsm.siderurgica.ui.screens.business

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
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.rules.BusinessRules
import pe.edu.unmsm.siderurgica.ui.common.*

private fun cents(value:String)=runCatching { java.math.BigDecimal(value.replace(',','.')).movePointRight(2).longValueExact() }.getOrDefault(0)

@Composable fun BusinessScreen(kind:String,navigate:(String)->Unit,vm:BusinessViewModel=koinViewModel()) {
    if (kind in listOf("quotes", "orders", "sales")) CommercialScreen(kind, navigate, vm)
    else if(kind=="purchases") PurchaseScreen(navigate,vm)
    else OperationsScreen(navigate, vm)
}
@Composable private fun OperationsScreen(navigate:(String)->Unit,vm:BusinessViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var form by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refresh("movements") }
    LaunchedEffect(s.saved) { form=false }
    Page("Movimientos de inventario","Compras recibidas y ventas confirmadas generan movimientos automáticamente. Las entradas manuales son excepcionales.") {
        Notice(s.message)
        if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(form) {
            OutlinedButton(onClick={form=false}) { Text("Volver a movimientos") }
            MovementForm(s,vm)
        } else {
            OutlinedButton(onClick={vm.refresh("movements")},enabled=!s.busy) { Text("Actualizar movimientos") }
            TextButton(onClick={navigate("purchases")}) { Text("Reponer mediante orden de compra") }
            Button(onClick={form=true},enabled=!s.busy) { Text("Registrar movimiento excepcional") }
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Buscar producto, motivo o documento de origen")})
            val moves=s.movements.filter{it.productName.contains(search,true)||it.reason.contains(search,true)||it.id.contains(search,true)||it.source.contains(search,true)}
            if(moves.isEmpty()&&!s.busy) EmptyCard("Sin movimientos","Las recepciones y ventas confirmadas aparecerán aquí. No repitas manualmente esas operaciones.")
            moves.forEach { m->OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(m.productName,style=MaterialTheme.typography.titleMedium)
                StatusBadge(m.type)
                Text("Antes: ${m.beforeStock} · Después: ${m.afterStock} · Cantidad: ${m.quantity}")
                Text(m.reason)
                if(m.source.isNotBlank()) Text("Origen: ${if(m.type=="COMPRA") "Orden de compra" else "Pedido/venta"} ${m.source}")
                Text("Operador: ${m.actor} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.createdAt))}")
            } } }
        }
    }
}
@Composable internal fun RecordForm(kind:String,s:BusinessState,vm:BusinessViewModel,onCreateClient:(()->Unit)?=null){
    val purchase=kind=="purchases"
    var clientId by rememberSaveable(kind){mutableStateOf("")}
    var supplier by rememberSaveable(kind){mutableStateOf("")}
    var name by rememberSaveable(kind){mutableStateOf("")}
    var ruc by rememberSaveable(kind){mutableStateOf("")}
    var newClient by rememberSaveable(kind){mutableStateOf(false)}
    var selected by rememberSaveable(kind){mutableStateOf("")}
    var quantity by rememberSaveable(kind){mutableStateOf("1")}
    var price by rememberSaveable(kind){mutableStateOf("")}

    var delivery by rememberSaveable(kind){mutableStateOf(java.time.LocalDate.now().plusDays(7).toString())}
    var supplierQuote by rememberSaveable(kind){mutableStateOf("")}

    val lineSaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<BusinessLineDraft>, String>(
        save = { values -> values.map { com.google.gson.Gson().toJson(it) } },
        restore = { values -> values.map { com.google.gson.Gson().fromJson(it, BusinessLineDraft::class.java) }.toMutableStateList() })
    val lines=rememberSaveable(kind,saver=lineSaver){mutableStateListOf<BusinessLineDraft>()}
    var error by remember{mutableStateOf("")}
    LaunchedEffect(s.lookupName){if(s.lookupName.isNotBlank())name=s.lookupName}
    Text(if(purchase)"Selecciona proveedor" else "Selecciona cliente",style=MaterialTheme.typography.titleMedium)

    if(purchase){
        SearchableSelector("Proveedor",supplier,s.suppliers.map{SelectorOption(it.ruc,it.name,"RUC ${it.ruc}",it.address)},
            onSelect={supplier=it},enabled=!s.busy,emptyText="Registra y sincroniza un proveedor desde Más → Proveedores y actualiza la consulta.")
    }
    else {

        SearchableSelector("Cliente",clientId,s.clients.map{SelectorOption(it.id,it.name,if(it.ruc.isBlank())"Sin RUC registrado" else "RUC ${it.ruc}")},
            onSelect={clientId=it},enabled=!s.busy,emptyText="Registra un cliente, espera su sincronización y actualiza el formulario.")
        TextButton(onClick={if(onCreateClient!=null)onCreateClient() else newClient=!newClient}){Text("Registrar otro cliente")}
        if(newClient){
            OutlinedTextField(ruc,{ruc=it},Modifier.fillMaxWidth(),label={Text("RUC opcional")})
            OutlinedButton(onClick={vm.lookupClient(ruc.trim())},enabled=!s.busy){Text("Consultar RUC")}
            OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Nombre o razón social")})
            Button(onClick={vm.submit(BusinessCommand("CLIENT_CREATE",client=name.trim(),ruc=ruc.trim()))},enabled=!s.busy){Text("Guardar cliente y sincronizar")}
            Text("Actualiza la lista cuando el cliente figure ENVIADO en Borradores.")
        }
    }
    Text(if(purchase) "Productos de la compra (${lines.size}/3)" else "Detalle: hasta tres productos distintos por operación")
    if(purchase)Text("Registra cantidades y precios de la cotización recibida. Este avance admite hasta 3 productos por orden; para más productos, crea otra orden.",style=MaterialTheme.typography.bodySmall)

    SearchableSelector("Producto",selected,s.products.map{SelectorOption(it.id,it.name,if(purchase) "${it.category} · stock ${it.stock}" else "${it.category} · venta ${money(it.priceCents)} · stock ${it.stock}")},
        onSelect={id->selected=id;price=if(purchase) "" else java.math.BigDecimal.valueOf(s.products.first{it.id==id}.priceCents,2).toPlainString()},
        enabled=!s.busy,emptyText="No hay productos cargados. Actualiza el catálogo o registra productos desde Stock.")
    OutlinedTextField(quantity,{quantity=it},Modifier.fillMaxWidth(),label={Text("Cantidad entera")})
    if(purchase)OutlinedTextField(price,{price=it},Modifier.fillMaxWidth(),label={Text("Precio de compra unitario SIN IGV S/")})

    else {
        val automatic=BusinessRules.automaticDiscount(quantity.toLongOrNull() ?: 0)
        Text("Descuento automático por cantidad: $automatic %",style=MaterialTheme.typography.labelLarge)
        Text("10–49 unidades: 5 % · 50–99: 7 % · desde 100: 10 %. Se aplica por producto.",style=MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(onClick={
        error=runCatching{
            val p=s.products.first{it.id==selected}
            val next=BusinessLineDraft(p.id,quantity.toLongOrNull()?:0,if(purchase)cents(price)else p.priceCents,if(purchase)0 else BusinessRules.automaticDiscount(quantity.toLongOrNull()?:0))
            BusinessRules.lines(lines+next,s.products,purchase);lines.add(next)
        }.exceptionOrNull()?.message.orEmpty()
    },enabled=!s.busy&&(!purchase||lines.size<3)){Text("Agregar producto")}
    Notice(error)
    lines.toList().forEachIndexed{i,l->val p=s.products.firstOrNull{it.id==l.productId};Row{Text(if(purchase) "${p?.name} × ${l.quantity} · compra ${money(l.priceCents)} sin IGV" else "${p?.name} × ${l.quantity} · ${l.discountPercent} %",Modifier.weight(1f));TextButton(onClick={lines.removeAt(i)}){Text("Quitar")}}}
    val totals=runCatching{BusinessRules.total(BusinessRules.lines(lines,s.products,purchase))}.getOrNull()
    if(totals!=null)Text("Subtotal ${money(totals.first)}\nIGV 18 % ${money(totals.second)}\nTotal ${money(totals.third)}",style=MaterialTheme.typography.titleMedium)
    if(purchase) {
        Text("El precio es el acordado en la cotización del proveedor. Agregamos IGV al total.")
        OutlinedTextField(supplierQuote,{supplierQuote=it},Modifier.fillMaxWidth(),label={Text("Referencia de cotización o acuerdo del proveedor")})
        OutlinedTextField(delivery,{delivery=it},Modifier.fillMaxWidth(),label={Text("Entrega estimada AAAA-MM-DD")})
        Text("La compra se enviará para aprobación.")
    }
    fun command():BusinessCommand{val c=s.clients.firstOrNull{it.id==clientId};return BusinessCommand(if(purchase)"PURCHASE_CREATE" else if(kind=="orders")"ORDER_CREATE" else "QUOTE_CREATE",client=c?.name.orEmpty(),ruc=c?.ruc.orEmpty(),clientId=clientId,lines=lines.toList(),supplierRuc=supplier,budgetPeriod=if(purchase)s.period else "",deliveryDate=if(purchase)delivery else "",supplierQuote=if(purchase)supplierQuote.trim() else "")}
    Button(onClick={vm.submit(command())},enabled=!s.busy&&lines.isNotEmpty()){Text(if(purchase)"Solicitar aprobación" else "Guardar y sincronizar")}
    OutlinedButton(onClick={vm.submit(command(),false)},enabled=!s.busy&&lines.isNotEmpty()){Text("Guardar solo borrador")}
}
@Composable private fun MovementForm(s:BusinessState,vm:BusinessViewModel){
    var selected by rememberSaveable{mutableStateOf("")};var type by rememberSaveable{mutableStateOf("ENTRADA")}
    var qty by rememberSaveable{mutableStateOf("")};var reason by rememberSaveable{mutableStateOf("")}

    SearchableSelector("Producto",selected,s.products.map{SelectorOption(it.id,it.name,"${it.category} · stock ${it.stock}")},
        onSelect={selected=it},enabled=!s.busy,emptyText="No hay productos cargados. Actualiza el inventario.")
    Row(Modifier.horizontalScroll(rememberScrollState())){listOf("ENTRADA","SALIDA","AJUSTE").forEach{t->FilterChip(type==t,{type=t},label={Text(t)})}}
    OutlinedTextField(qty,{qty=it},Modifier.fillMaxWidth(),label={Text(if(type=="AJUSTE")"Stock final contado" else "Cantidad")})
    OutlinedTextField(reason,{reason=it},Modifier.fillMaxWidth(),label={Text("Motivo / referencia de sustento")})
    Notice("Un ajuste usa el stock que estás viendo como referencia. Si cambió antes de sincronizar, se rechazará para evitar sobrescribir movimientos posteriores.")
    fun command()=BusinessCommand("MOVEMENT",selected,type=type,quantity=qty.toLongOrNull()?:-1,expectedStock=s.products.firstOrNull{it.id==selected}?.stock?:-1,reason=reason)
    Button(onClick={vm.submit(command())},enabled=!s.busy&&selected.isNotBlank()){Text("Confirmar y sincronizar movimiento")}
    OutlinedButton(onClick={vm.submit(command(),false)},enabled=!s.busy&&selected.isNotBlank()){Text("Guardar borrador")}
}
