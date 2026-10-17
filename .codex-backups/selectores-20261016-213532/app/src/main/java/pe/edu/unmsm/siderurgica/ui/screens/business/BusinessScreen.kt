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
    else OperationsScreen(kind, navigate, vm)
}
@Composable private fun OperationsScreen(kind:String,navigate:(String)->Unit,vm:BusinessViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var form by rememberSaveable(kind){mutableStateOf(false)}
    var search by rememberSaveable(kind){mutableStateOf("")}
    var filter by rememberSaveable(kind){mutableStateOf("Todos")}
    var detail by remember(kind){mutableStateOf<BusinessRecord?>(null)}
    LaunchedEffect(kind){vm.refresh(kind)}
    LaunchedEffect(s.saved){form=false}
    val title=when(kind){"quotes"->"Cotizaciones";"orders"->"Pedidos";"sales"->"Ventas y comprobantes";"purchases"->"Compras y recepción";else->"Movimientos de inventario"}
    Page(title,"Datos guardados disponibles sin conexión. Cada envío se valida nuevamente en Firebase.") {
        if(kind in listOf("quotes","orders","sales")) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("quotes" to "Cotizaciones","orders" to "Pedidos","sales" to "Ventas").forEach{(route,label)->FilterChip(kind==route,{navigate(route)},label={Text(label)})}
        }
        Notice(s.message)
        if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick={vm.refresh(kind)},enabled=!s.busy){Text("Actualizar desde Firebase")}
        if(kind!="sales")Button(onClick={form=!form},enabled=!s.busy){Text(if(form)"Cerrar formulario" else when(kind){"movements"->"Registrar movimiento";"purchases"->"Nueva orden de compra";"orders"->"Nuevo pedido directo";else->"Nueva cotización"})}
        if(form) {
            if(kind=="movements")MovementForm(s,vm)
            else RecordForm(kind,s,vm)
        }
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Buscar cliente, proveedor, producto o código")})
        if(kind=="movements") {
            val moves=s.movements.filter{it.productName.contains(search,true)||it.reason.contains(search,true)||it.id.contains(search,true)}
            if(moves.isEmpty()&&!s.busy)EmptyCard("Sin movimientos","Registra una entrada, salida o ajuste, o confirma una venta/recepción.")
            moves.forEach{m->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(m.productName,style=MaterialTheme.typography.titleMedium);StatusBadge(m.type)
                Text("Antes: ${m.beforeStock} · Después: ${m.afterStock} · Cantidad: ${m.quantity}")
                Text(m.reason);Text("Operador: ${m.actor.take(10)} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.createdAt))}")
            }}}
        } else {
            val statuses=when(kind){"quotes"->listOf("Todos","PENDIENTE","APROBADA","RECHAZADA","CONVERTIDA");"orders"->listOf("Todos","PENDIENTE","APROBADO","RECHAZADO","FACTURADO");"purchases"->listOf("Todos","PENDIENTE","RECIBIDA");else->listOf("Todos","CONFIRMADA")}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){statuses.forEach{f->FilterChip(filter==f,{filter=f},label={Text(f)})}}
            val records=s.records.filter{r->(filter=="Todos"||r.status==filter)&&(r.client.contains(search,true)||r.lines.any{it.name.contains(search,true)}||r.id.contains(search,true))}
            Text("${records.size} registros")
            if(records.isEmpty()&&!s.busy)EmptyCard("Sin resultados","Actualiza el listado después de sincronizar o cambia los filtros.")
            records.forEach{r->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(r.client,style=MaterialTheme.typography.titleLarge);StatusBadge(r.status)
                Text(r.lines.joinToString(" · "){"${it.name} × ${it.quantity}"})
                Text(money(r.totalCents),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                Text("${r.id.take(12)} · ${java.text.DateFormat.getDateInstance().format(java.util.Date(r.createdAt))}")
                TextButton(onClick={detail=r}){Text("Ver detalle e impuestos")}
                if(kind=="quotes"&&r.status=="PENDIENTE"){
                    ConfirmButton("Aprobar cotización","Se enviará la aprobación a la cola; no modifica inventario.",!s.busy){vm.submit(BusinessCommand("QUOTE_STATE",r.id,state="APROBADA"))}
                    ConfirmButton("Rechazar cotización","Se enviará el rechazo de esta propuesta.",!s.busy){vm.submit(BusinessCommand("QUOTE_STATE",r.id,state="RECHAZADA"))}
                }
                if(kind=="quotes"&&r.status=="APROBADA")ConfirmButton("Crear pedido","Conserva precios, descuentos e IGV de la propuesta aceptada.",!s.busy){
                    vm.submit(BusinessCommand("ORDER_CREATE",client=r.client,ruc=r.ruc,clientId=r.clientId,reference=r.id,lines=r.lines.map{BusinessLineDraft(it.productId,it.quantity,it.unitCents,it.discountPercent)}))
                }
                if(kind=="orders"&&r.status=="PENDIENTE"){
                    ConfirmButton("Aprobar pedido","Valida stock vigente. El descuento definitivo de inventario ocurre al confirmar la venta.",!s.busy){vm.submit(BusinessCommand("ORDER_STATE",r.id,state="APROBADO"))}
                    ConfirmButton("Rechazar pedido","El pedido rechazado no modifica inventario.",!s.busy){vm.submit(BusinessCommand("ORDER_STATE",r.id,state="RECHAZADO"))}
                }
                if(kind=="orders"&&r.status=="APROBADO")ConfirmButton("Confirmar venta","Valida y descuenta stock de todos los productos en una sola transacción. Si no alcanza, no registra una venta parcial.",!s.busy){vm.submit(BusinessCommand("SALE_CREATE",r.id))}
                if(kind=="purchases"&&r.status=="PENDIENTE")ConfirmButton("Registrar recepción completa","Confirma que recibiste todos los productos; incrementa inventario una sola vez. Puedes dejar el envío pendiente sin conexión.",!s.busy){vm.submit(BusinessCommand("PURCHASE_RECEIVE",r.id))}
                if(kind=="sales")ReceiptActions(r)
            }}}
        }
    }
    detail?.let{r->AlertDialog(onDismissRequest={detail=null},title={Text("Detalle ${r.id.take(10)}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("${r.client} · RUC: ${r.ruc.ifBlank{"—"}}")
        r.lines.forEach{Text("${it.name}: ${it.quantity} × ${money(it.unitCents)}; descuento ${it.discountPercent} %; neto ${money(it.netCents)}")}
        Text("Subtotal neto: ${money(r.subtotalCents)}\nIGV 18 %: ${money(r.taxCents)}\nTotal: ${money(r.totalCents)}")
        if(kind=="purchases")Text("Presupuesto: ${money(r.budgetCents)}")
    }},confirmButton={TextButton(onClick={detail=null}){Text("Cerrar")}})}
}
@Composable internal fun RecordForm(kind:String,s:BusinessState,vm:BusinessViewModel,onCreateClient:(()->Unit)?=null){
    val purchase=kind=="purchases"
    var clientId by rememberSaveable(kind){mutableStateOf("")}
    var supplier by rememberSaveable(kind){mutableStateOf("")}
    var name by rememberSaveable(kind){mutableStateOf("")}
    var ruc by rememberSaveable(kind){mutableStateOf("")}
    var token by remember{mutableStateOf("")}
    var newClient by rememberSaveable(kind){mutableStateOf(false)}
    var selected by rememberSaveable(kind){mutableStateOf("")}
    var quantity by rememberSaveable(kind){mutableStateOf("1")}
    var price by rememberSaveable(kind){mutableStateOf("")}

    var budget by rememberSaveable(kind){mutableStateOf("")}

    val lineSaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<BusinessLineDraft>, String>(
        save = { values -> values.map { com.google.gson.Gson().toJson(it) } },
        restore = { values -> values.map { com.google.gson.Gson().fromJson(it, BusinessLineDraft::class.java) }.toMutableStateList() })
    val lines=rememberSaveable(kind,saver=lineSaver){mutableStateListOf<BusinessLineDraft>()}
    var error by remember{mutableStateOf("")}
    LaunchedEffect(s.lookupName){if(s.lookupName.isNotBlank())name=s.lookupName}
    Text(if(purchase)"Selecciona proveedor" else "Selecciona cliente",style=MaterialTheme.typography.titleMedium)
    if(purchase){s.suppliers.forEach{p->FilterChip(supplier==p.ruc,{supplier=p.ruc},label={Text("${p.name} · ${p.ruc}")})};if(s.suppliers.isEmpty())Text("Registra y sincroniza un proveedor primero, desde Más → Proveedores.")}
    else {
        s.clients.forEach{c->FilterChip(clientId==c.id,{clientId=c.id},label={Text("${c.name} · ${c.ruc}")})}
        TextButton(onClick={if(onCreateClient!=null)onCreateClient() else newClient=!newClient}){Text("Registrar otro cliente")}
        if(newClient){
            OutlinedTextField(ruc,{ruc=it},Modifier.fillMaxWidth(),label={Text("RUC opcional")})
            PasswordField(token,{token=it},"Token personal Decolecta (solo consulta opcional)")
            OutlinedButton(onClick={vm.lookupClient(ruc.trim(),token)},enabled=!s.busy){Text("Consultar RUC con Retrofit")}
            OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Nombre o razón social")})
            Button(onClick={vm.submit(BusinessCommand("CLIENT_CREATE",client=name.trim(),ruc=ruc.trim()))},enabled=!s.busy){Text("Guardar cliente y sincronizar")}
            Text("Actualiza la lista cuando el cliente figure ENVIADO en Borradores. El token no se guarda.")
        }
    }
    Text("Detalle: hasta tres productos distintos por operación")
    s.products.forEach{p->FilterChip(selected==p.id,{selected=p.id;price=java.math.BigDecimal.valueOf(p.priceCents,2).toPlainString()},label={Text("${p.name} · ${money(p.priceCents)} · stock ${p.stock}")})}
    OutlinedTextField(quantity,{quantity=it},Modifier.fillMaxWidth(),label={Text("Cantidad entera")})
    if(purchase)OutlinedTextField(price,{price=it},Modifier.fillMaxWidth(),label={Text("Precio de compra unitario S/")})

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
    },enabled=!s.busy){Text("Agregar producto")}
    Notice(error)
    lines.toList().forEachIndexed{i,l->val p=s.products.firstOrNull{it.id==l.productId};Row{Text("${p?.name} × ${l.quantity} · ${l.discountPercent} %",Modifier.weight(1f));TextButton(onClick={lines.removeAt(i)}){Text("Quitar")}}}
    val totals=runCatching{BusinessRules.total(BusinessRules.lines(lines,s.products,purchase))}.getOrNull()
    if(totals!=null)Text("Subtotal ${money(totals.first)}\nIGV 18 % ${money(totals.second)}\nTotal ${money(totals.third)}",style=MaterialTheme.typography.titleMedium)
    if(purchase)OutlinedTextField(budget,{budget=it},Modifier.fillMaxWidth(),label={Text("Presupuesto máximo S/ (incluye IGV)")})
    fun command():BusinessCommand{val c=s.clients.firstOrNull{it.id==clientId};return BusinessCommand(if(purchase)"PURCHASE_CREATE" else if(kind=="orders")"ORDER_CREATE" else "QUOTE_CREATE",client=c?.name.orEmpty(),ruc=c?.ruc.orEmpty(),clientId=clientId,lines=lines.toList(),supplierRuc=supplier,budgetCents=cents(budget))}
    Button(onClick={vm.submit(command())},enabled=!s.busy&&lines.isNotEmpty()){Text("Guardar y sincronizar")}
    OutlinedButton(onClick={vm.submit(command(),false)},enabled=!s.busy&&lines.isNotEmpty()){Text("Guardar solo borrador")}
}
@Composable private fun MovementForm(s:BusinessState,vm:BusinessViewModel){
    var selected by rememberSaveable{mutableStateOf("")};var type by rememberSaveable{mutableStateOf("ENTRADA")}
    var qty by rememberSaveable{mutableStateOf("")};var reason by rememberSaveable{mutableStateOf("")}
    s.products.forEach{p->FilterChip(selected==p.id,{selected=p.id},label={Text("${p.name} · stock ${p.stock}")})}
    Row(Modifier.horizontalScroll(rememberScrollState())){listOf("ENTRADA","SALIDA","AJUSTE").forEach{t->FilterChip(type==t,{type=t},label={Text(t)})}}
    OutlinedTextField(qty,{qty=it},Modifier.fillMaxWidth(),label={Text(if(type=="AJUSTE")"Stock final contado" else "Cantidad")})
    OutlinedTextField(reason,{reason=it},Modifier.fillMaxWidth(),label={Text("Motivo / referencia de sustento")})
    Notice("Un ajuste usa el stock que estás viendo como referencia. Si cambió antes de sincronizar, se rechazará para evitar sobrescribir movimientos posteriores.")
    fun command()=BusinessCommand("MOVEMENT",selected,type=type,quantity=qty.toLongOrNull()?:-1,expectedStock=s.products.firstOrNull{it.id==selected}?.stock?:-1,reason=reason)
    Button(onClick={vm.submit(command())},enabled=!s.busy&&selected.isNotBlank()){Text("Confirmar y sincronizar movimiento")}
    OutlinedButton(onClick={vm.submit(command(),false)},enabled=!s.busy&&selected.isNotBlank()){Text("Guardar borrador")}
}
