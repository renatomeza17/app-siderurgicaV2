package pe.edu.unmsm.siderurgica.ui.screens.business

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.rules.BusinessRules
import pe.edu.unmsm.siderurgica.ui.common.*

internal fun commercialStatus(kind:String,status:String):String=when {
    kind=="quotes" -> when(status){"PENDIENTE"->"Esperando respuesta";"APROBADA"->"Aceptada por el cliente";"RECHAZADA"->"Rechazada por el cliente";"CONVERTIDA"->"Pedido creado";else->status}
    kind=="orders" -> when(status){"PENDIENTE"->"Pendiente de validación";"APROBADO"->"Pedido validado";"RECHAZADO"->"Pedido rechazado";"FACTURADO"->"Venta registrada";else->status}
    status=="CONFIRMADA" -> "Venta confirmada"
    else -> status
}
private data class DecisionAction(val record:BusinessRecord,val quote:Boolean,val accepted:Boolean)

@Composable internal fun CommercialScreen(kind:String,navigate:(String)->Unit,vm:BusinessViewModel){
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable(kind){mutableStateOf("list")}
    var selectedId by rememberSaveable(kind){mutableStateOf("")}
    var search by rememberSaveable(kind){mutableStateOf("")}
    var filter by rememberSaveable(kind){mutableStateOf("Todos")}
    var decision by remember(kind){mutableStateOf<DecisionAction?>(null)}
    var seenSaved by rememberSaveable(kind){mutableIntStateOf(state.saved)}
    val formState=rememberSaveableStateHolder()
    LaunchedEffect(kind){vm.refresh(kind)}
    LaunchedEffect(state.saved){
        if(state.saved!=seenSaved){seenSaved=state.saved;page=if(page=="client")"form" else "list";selectedId=""}
    }
    fun back(){page=if(page=="client")"form" else "list";selectedId="";vm.clearMessage()}
    BackHandler(page!="list"){back()}
    val title=when(kind){"quotes"->"Cotizaciones";"orders"->"Pedidos";else->"Ventas"}
    when(page){
        "form" -> Page(if(kind=="orders")"Nuevo pedido directo" else "Nueva cotización","Selecciona un cliente y prepara el detalle. Los precios vienen del catálogo."){
            OutlinedButton(onClick={back()},enabled=!state.busy){Text("Volver al listado")}
            Notice(state.message)
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick={vm.refresh(kind)},enabled=!state.busy){Text("Actualizar clientes y productos")}
            formState.SaveableStateProvider("form-$kind"){
                RecordForm(kind,state,vm,onCreateClient={page="client";vm.clearMessage()})
            }
        }
        "client" -> ClientRegistrationPage(state,vm,onBack={back()})
        "detail" -> {
            val record=state.records.firstOrNull{it.id==selectedId}
            Page("Detalle de ${if(kind=="quotes")"cotización" else if(kind=="orders")"pedido" else "venta"}"){
                OutlinedButton(onClick={back()},enabled=!state.busy){Text("Volver al listado")}
                Notice(state.message)
                if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick={vm.refresh(kind)},enabled=!state.busy){Text("Actualizar estado")}
                if(record==null)EmptyCard("Registro no disponible","Vuelve al listado y actualiza la consulta.")
                else{
                    RecordSummary(kind,record)
                    when(kind){
                        "quotes"->{
                            Notice("Esta es una propuesta comercial. Compartir el PDF no registra la respuesta del cliente ni acredita un pago.")
                            QuotePdfActions(record)
                            if(record.status=="PENDIENTE"){
                                Button(onClick={decision=DecisionAction(record,true,true)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Registrar aceptación del cliente")}
                                OutlinedButton(onClick={decision=DecisionAction(record,true,false)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Registrar rechazo del cliente")}
                            }
                            if(record.status=="APROBADA")ConfirmButton("Crear pedido desde esta cotización","Se conservarán los productos, descuentos e impuestos aceptados. No se registra un pago.",!state.busy){
                                vm.submit(BusinessCommand("ORDER_CREATE",client=record.client,ruc=record.ruc,clientId=record.clientId,reference=record.id,
                                    lines=record.lines.map{BusinessLineDraft(it.productId,it.quantity,it.unitCents,it.discountPercent)}))
                            }
                            if(record.status=="CONVERTIDA")TextButton(onClick={navigate("orders")}){Text("Ir a pedidos")}
                        }
                        "orders"->{
                            Notice("La validación comprueba que el pedido puede atenderse. No confirma un cobro ni reserva existencias. El stock se descuenta al confirmar la venta.")
                            if(record.status=="PENDIENTE"){
                                Button(onClick={decision=DecisionAction(record,false,true)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Validar pedido")}
                                OutlinedButton(onClick={decision=DecisionAction(record,false,false)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Rechazar pedido")}
                            }
                            if(record.status=="APROBADO")ConfirmButton("Confirmar venta","Confirma la operación comercial autorizada. Se comprobará y descontará el stock de todos los productos. Esta acción no verifica pagos bancarios.",!state.busy){vm.submit(BusinessCommand("SALE_CREATE",record.id))}
                            if(record.status=="FACTURADO")TextButton(onClick={navigate("sales")}){Text("Ver ventas y comprobantes")}
                        }
                        "sales"->ReceiptActions(record)
                    }
                }
            }
        }
        else -> Page(title,when(kind){"quotes"->"Propuestas preparadas para tus clientes.";"orders"->"Solicitudes de compra y validación operativa.";else->"Operaciones confirmadas y sus comprobantes."}){
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("quotes" to "Cotizaciones","orders" to "Pedidos","sales" to "Ventas").forEach{(route,label)->FilterChip(kind==route,{navigate(route)},label={Text(label)})}
            }
            Notice(state.message)
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(kind!="sales")Button(onClick={page="form";vm.clearMessage()},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(if(kind=="quotes")"Nueva cotización" else "Nuevo pedido directo")}
            OutlinedButton(onClick={vm.refresh(kind)},enabled=!state.busy){Text("Actualizar listado")}
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Buscar cliente, producto o código")},singleLine=true)
            val statuses=when(kind){"quotes"->listOf("Todos","PENDIENTE","APROBADA","RECHAZADA","CONVERTIDA");"orders"->listOf("Todos","PENDIENTE","APROBADO","RECHAZADO","FACTURADO");else->listOf("Todos","CONFIRMADA")}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){statuses.forEach{f->FilterChip(filter==f,{filter=f},label={Text(if(f=="Todos")f else commercialStatus(kind,f))})}}
            val records=state.records.filter{r->(filter=="Todos"||r.status==filter)&&(r.client.contains(search,true)||r.id.contains(search,true)||r.lines.any{it.name.contains(search,true)})}
            Text("${records.size} registros")
            if(records.isEmpty()&&!state.busy)EmptyCard("Sin resultados","Crea un registro o cambia la búsqueda. Si enviaste una operación, espera su confirmación en Borradores y actualiza.")
            records.forEach{record->OutlinedCard(onClick={selectedId=record.id;page="detail";vm.clearMessage()},modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(record.client,style=MaterialTheme.typography.titleLarge)
                    StatusBadge(commercialStatus(kind,record.status))
                    Text(record.lines.joinToString(" · "){"${it.name} × ${it.quantity}"})
                    Text(money(record.totalCents),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                    Text("${record.id.take(12)} · ${date(record.createdAt)}",style=MaterialTheme.typography.bodySmall)
                    Text("Abrir detalle",color=MaterialTheme.colorScheme.primary)
                }
            }}
        }
    }
    decision?.let{action->DecisionDialog(action,state.busy,onDismiss={decision=null},onConfirm={reason,channel->
        vm.submit(BusinessCommand(if(action.quote)"QUOTE_STATE" else "ORDER_STATE",action.record.id,
            state=if(action.quote){if(action.accepted)"APROBADA" else "RECHAZADA"}else{if(action.accepted)"APROBADO" else "RECHAZADO"},
            reason=reason,responseChannel=channel));decision=null
    })}
}

private fun date(time:Long)=if(time<=0)"Sin fecha disponible" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))
@Composable private fun RecordSummary(kind:String,record:BusinessRecord){
    SectionTitle(record.client)
    Text("RUC: ${record.ruc.ifBlank{"No indicado"}}")
    StatusBadge(commercialStatus(kind,record.status))
    Text("Referencia: ${record.id}\nFecha: ${date(record.createdAt)}",style=MaterialTheme.typography.bodySmall)
    SectionTitle("Productos")
    record.lines.forEach{line->OutlinedCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(line.name,style=MaterialTheme.typography.titleMedium)
            Text("${line.quantity} unidades × ${money(line.unitCents)}")
            Text("Descuento acordado: ${line.discountPercent} % · Neto: ${money(line.netCents)}")
        }
    }}
    Text("Subtotal neto: ${money(record.subtotalCents)}\nIGV 18 %: ${money(record.taxCents)}\nTotal: ${money(record.totalCents)}",style=MaterialTheme.typography.titleLarge)
    if(record.decisionBy.isNotBlank()){
        SectionTitle("Decisión registrada")
        Text("Operador: ${record.decisionBy}\nFecha: ${date(record.decisionAt)}\nCanal: ${record.responseChannel}\nSustento: ${record.decisionReason}")
    }
}

@Composable private fun DecisionDialog(action:DecisionAction,busy:Boolean,onDismiss:()->Unit,onConfirm:(String,String)->Unit){
    var reason by rememberSaveable(action.record.id,action.accepted){mutableStateOf("")}
    var channel by rememberSaveable(action.record.id,action.accepted){mutableStateOf(if(action.quote)"" else "INTERNO")}
    var error by remember{mutableStateOf("")}
    val title=if(action.quote){if(action.accepted)"Registrar aceptación del cliente" else "Registrar rechazo del cliente"}else{if(action.accepted)"Validar pedido" else "Rechazar pedido"}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(if(action.quote)"Registra lo que respondió el cliente por un canal externo. No se verifican pagos." else "Registra el sustento operativo. No constituye comprobación de un pago.")
            if(action.quote){
                Text("Canal de respuesta")
                Row(Modifier.horizontalScroll(rememberScrollState())){BusinessRules.responseChannels.forEach{c->FilterChip(channel==c,{channel=c},label={Text(c.lowercase().replaceFirstChar{it.uppercase()})})}}
            }
            OutlinedTextField(reason,{reason=it.take(240)},Modifier.fillMaxWidth(),label={Text(if(action.quote)"Respuesta y referencia del cliente" else "Motivo de la decisión")},minLines=3)
            Text("Quedarán registrados tu usuario y la fecha de confirmación en Firebase.",style=MaterialTheme.typography.bodySmall)
            Notice(error)
        }
    },confirmButton={TextButton(onClick={
        val invalid=runCatching{BusinessRules.decision(reason,channel,action.quote)}.exceptionOrNull()
        if(invalid==null)onConfirm(reason.trim(),channel)else error=invalid.message.orEmpty()
    },enabled=!busy){Text("Registrar decisión")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}

@Composable private fun ClientRegistrationPage(state:BusinessState,vm:BusinessViewModel,onBack:()->Unit){
    var name by rememberSaveable{mutableStateOf("")}
    var ruc by rememberSaveable{mutableStateOf("")}
    var api by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(state.lookupName){if(state.lookupName.isNotBlank())name=state.lookupName}
    Page("Registrar cliente","Sus datos estarán disponibles para cotizaciones y pedidos."){
        OutlinedButton(onClick=onBack,enabled=!state.busy){Text("Volver al formulario")}
        Notice(state.message)
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(ruc,{ruc=it.filter(Char::isDigit).take(11)},Modifier.fillMaxWidth(),label={Text("RUC opcional")})
        TextButton(onClick={api=!api}){Text("Consultar razón social por RUC")}
        if(api){OutlinedButton(onClick={vm.lookupClient(ruc)},enabled=!state.busy){Text("Consultar RUC")}}
        OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Nombre o razón social")})
        Button(onClick={vm.submit(BusinessCommand("CLIENT_CREATE",client=name.trim(),ruc=ruc.trim()))},enabled=!state.busy){Text("Guardar cliente")}
        Text("Después de su confirmación en Borradores, actualiza clientes en el formulario para seleccionarlo.")
    }
}
