package pe.edu.unmsm.siderurgica.ui.screens.business

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable internal fun PurchaseScreen(navigate:(String)->Unit,vm:BusinessViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("list") }
    var selected by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    LaunchedEffect(Unit) { vm.refresh("purchases") }
    LaunchedEffect(s.saved) { if(s.saved>0) { page="list"; selected="" } }
    BackHandler(page!="list") { page="list" }
    val r=s.records.firstOrNull{it.id==selected}
    Page(when(page){"new"->"Nueva orden de compra";"budget"->"Presupuesto de compras";"receive"->"Recibir mercadería";"detail"->"Detalle de orden";else->"Órdenes de compra"},
        "Reposición de productos para distribución. Crear o aprobar una orden no incrementa stock.") {
        Notice(s.message)
        if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(page!="list") OutlinedButton(onClick={page="list"}){Text("Volver a órdenes")}
        when(page) {
            "new" -> RecordForm("purchases",s,vm)
            "budget" -> PurchaseBudgetForm(s,vm)
            "detail","receive" -> if(r!=null) {
                Text("OC ${r.id}",style=MaterialTheme.typography.titleMedium)
                Text("${r.client} · RUC ${r.supplierRuc}")
                StatusBadge(purchaseStatus(r.status))
                Text("Cotización/acuerdo: ${r.supplierQuote.ifBlank{"Histórica: sin referencia"}}")
                Text("Entrega estimada: ${r.deliveryDate.ifBlank{"No registrada"}} · Período ${r.budgetPeriod.ifBlank{"Histórico"}}")
                r.lines.forEach { Text("${it.name}: ${it.quantity} × ${money(it.unitCents)} sin IGV = ${money(it.netCents)}") }
                Text("Subtotal ${money(r.subtotalCents)} · IGV ${money(r.taxCents)}\nTotal ${money(r.totalCents)}",style=MaterialTheme.typography.titleLarge)
                if(r.decisionBy.isNotBlank()) Text("Decisión: ${r.decisionReason} · Usuario ${r.decisionBy} · ${purchaseDate(r.decisionAt)}")
                if(r.status=="RECIBIDA") Text("Recepción: ${r.receiptDocument.ifBlank{"Registro histórico"}}\n${r.receiptNotes}\nRecibió: ${r.receivedBy} · ${purchaseDate(r.receivedAt)}")
                if(page=="receive") PurchaseReceptionForm(r,s,vm)
                else {
                    if(r.budgetPeriod.isBlank() && r.status=="PENDIENTE") Notice("Orden histórica sin período: puedes cancelarla y crear una nueva. No se recibe sin autorización.")
                    if(s.role=="ADMIN" && r.status in listOf("PENDIENTE","APROBADA")) PurchaseDecisionForm(r,s,vm)
                    if(r.status=="APROBADA") {
                        PurchasePdfActions(r)
                        Button(onClick={page="receive"},enabled=!s.busy){Text("Comprobar y recibir mercadería")}
                        Text("Coordina y comparte la orden con el proveedor por un canal externo. No acredita pago.")
                    }
                }
            } else Text("Actualiza y selecciona una orden.")
            else -> {
                OutlinedButton(onClick={vm.refresh("purchases")},enabled=!s.busy){Text("Actualizar órdenes y presupuesto")}
                Text("${s.period}: disponible ${money(s.budget.availableCents)} · reservado ${money(s.budget.reservedCents)}",style=MaterialTheme.typography.titleMedium)
                Button(onClick={page="new"},enabled=!s.busy){Text("Nueva orden de compra")}
                if(s.role=="ADMIN") OutlinedButton(onClick={page="budget"}){Text("Administrar presupuesto")}
                TextButton(onClick={navigate("suppliers")}){Text("Gestionar proveedores")}
                TextButton(onClick={navigate("documents")}){Text("Capturar documento con OCR")}
                TextButton(onClick={navigate("movements")}){Text("Consultar movimientos de inventario")}
                Text("Pendiente → autorización ADMIN → recepción ALMACÉN/ADMIN → stock actualizado.")
                Text("Una operación en cola no está confirmada: revisa Borradores y actualiza el listado.")
                OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Buscar proveedor, producto o código")})
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Todos","PENDIENTE","APROBADA","RECIBIDA","RECHAZADA","CANCELADA").forEach { f->FilterChip(filter==f,{filter=f},label={Text(if(f=="Todos")f else purchaseStatus(f))}) }
                }
                val records=s.records.filter{(filter=="Todos"||it.status==filter)&&(it.client.contains(search,true)||it.id.contains(search,true)||it.lines.any{l->l.name.contains(search,true)})}
                if(records.isEmpty()&&!s.busy) EmptyCard("Sin órdenes","Registra una orden o actualiza después de sincronizar.")
                records.forEach { order->OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(order.client,style=MaterialTheme.typography.titleMedium)
                    StatusBadge(purchaseStatus(order.status))
                    Text(order.lines.joinToString(" · "){"${it.name} × ${it.quantity}"})
                    Text("${money(order.totalCents)} · OC ${order.id.take(12)}")
                    TextButton(onClick={selected=order.id;page="detail"}){Text("Ver orden y acciones")}
                } } }
            }
        }
    }
}
internal fun purchaseStatus(s:String)=when(s){"PENDIENTE"->"Pendiente de autorización";"APROBADA"->"Aprobada para compra";"RECIBIDA"->"Mercadería recibida";"RECHAZADA"->"Rechazada";"CANCELADA"->"Cancelada";else->s}
private fun purchaseDate(value:Long)=if(value>0)java.text.DateFormat.getDateTimeInstance().format(java.util.Date(value))else "Sin fecha"
@Composable private fun PurchaseBudgetForm(s:BusinessState,vm:BusinessViewModel) {
    var period by rememberSaveable(s.period){mutableStateOf(s.period)}
    var amount by rememberSaveable(s.budget){mutableStateOf(java.math.BigDecimal.valueOf(s.budget.totalCents,2).toPlainString())}
    OutlinedTextField(period,{period=it},Modifier.fillMaxWidth(),label={Text("Período AAAA-MM")})
    OutlinedButton(onClick={vm.changePeriod(period)},enabled=!s.busy){Text("Consultar período")}
    Text("Consultado: ${s.period}\nTotal ${money(s.budget.totalCents)}\nReservado ${money(s.budget.reservedCents)}\nRecibido ${money(s.budget.spentCents)}\nDisponible ${money(s.budget.availableCents)}")
    Text("Límite de compras autorizado; no representa saldo bancario ni registro de pagos. No se reduce por debajo de lo reservado y recibido.")
    OutlinedTextField(amount,{amount=it},Modifier.fillMaxWidth(),label={Text("Total autorizado del período S/ (incluye IGV)")})
    Button(onClick={val n=runCatching{java.math.BigDecimal(amount.replace(',','.')).movePointRight(2).longValueExact()}.getOrDefault(-1);vm.submit(BusinessCommand("BUDGET_SET",target=s.period,budgetCents=n))},enabled=!s.busy&&period==s.period){Text("Guardar presupuesto y sincronizar")}
}
@Composable private fun PurchaseDecisionForm(r:BusinessRecord,s:BusinessState,vm:BusinessViewModel) {
    var reason by rememberSaveable(r.id){mutableStateOf("")}
    OutlinedTextField(reason,{reason=it},Modifier.fillMaxWidth(),label={Text("Sustento de autorización, rechazo o cancelación")})
    if(r.status=="PENDIENTE" && r.budgetPeriod.isNotBlank()) {
        ConfirmButton("Autorizar y reservar presupuesto","Firebase validará el disponible del período ${r.budgetPeriod}. No registra pago ni aumenta stock.",!s.busy){vm.submit(BusinessCommand("PURCHASE_STATE",r.id,state="APROBADA",reason=reason))}
        ConfirmButton("Rechazar orden","No modifica inventario ni reserva presupuesto.",!s.busy){vm.submit(BusinessCommand("PURCHASE_STATE",r.id,state="RECHAZADA",reason=reason))}
    }
    ConfirmButton("Cancelar orden","Si estaba autorizada, libera su reserva una sola vez. No borra el historial.",!s.busy){vm.submit(BusinessCommand("PURCHASE_STATE",r.id,state="CANCELADA",reason=reason))}
}
@Composable private fun PurchaseReceptionForm(r:BusinessRecord,s:BusinessState,vm:BusinessViewModel) {
    var document by rememberSaveable(r.id){mutableStateOf("")}
    var notes by rememberSaveable(r.id){mutableStateOf("")}
    val checked=remember(r.id){mutableStateListOf<String>()}
    Text("Verifica físicamente cada producto y su cantidad completa. No confirmes entregas parciales o productos con observaciones pendientes.")
    r.lines.forEach { l-> Row { Checkbox(l.productId in checked,{yes->if(yes)checked.add(l.productId)else checked.remove(l.productId)});Text("Recibí conforme ${l.quantity} de ${l.name}",Modifier.padding(top=12.dp)) } }
    OutlinedTextField(document,{document=it},Modifier.fillMaxWidth(),label={Text("Número de factura o guía del proveedor")})
    OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("Observaciones / referencia documental (opcional)")})
    Text("Para capturar el documento usa Documentos/OCR. Este registro guarda su referencia; no adjunta automáticamente archivos.")
    ConfirmButton("Confirmar recepción completa","Se guardarán recepción, orden, presupuesto y movimientos en una transacción. Si no hay red, queda pendiente; el stock cambia cuando Firebase confirma.",!s.busy&&checked.size==r.lines.size){vm.submit(BusinessCommand("PURCHASE_RECEIVE",r.id,receiptDocument=document.trim(),receiptNotes=notes.trim()))}
}
