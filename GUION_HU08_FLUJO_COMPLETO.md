# Guion técnico HU08 — ejecución y respaldo de recepción

Los fragmentos son exactos de la versión actual. El punto de entrada es Compose; no hay controlador Spring. Para la demo sigue `GUIA_HU08_COMPRAS_Y_PRUEBAS.md`.

## 1. Crear la orden

“Seleccionamos los productos y registramos el precio acordado con el proveedor, no el precio de venta. El formulario construye el comando con proveedor, cantidades, entrega prevista y referencia.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessScreen.kt:124`

```kotlin
    fun command():BusinessCommand{val c=s.clients.firstOrNull{it.id==clientId};return BusinessCommand(if(purchase)"PURCHASE_CREATE" else if(kind=="orders")"ORDER_CREATE" else "QUOTE_CREATE",client=c?.name.orEmpty(),ruc=c?.ruc.orEmpty(),clientId=clientId,lines=lines.toList(),supplierRuc=supplier,budgetPeriod=if(purchase)s.period else "",deliveryDate=if(purchase)delivery else "",supplierQuote=if(purchase)supplierQuote.trim() else "")}
    Button(onClick={vm.submit(command())},enabled=!s.busy&&lines.isNotEmpty()){Text(if(purchase)"Solicitar aprobación" else "Guardar y sincronizar")}
    OutlinedButton(onClick={vm.submit(command(),false)},enabled=!s.busy&&lines.isNotEmpty()){Text("Guardar solo borrador")}
}
```

## 2. Guardar y sincronizar

“El ViewModel valida el comando y lo guarda localmente. Guardar no es todavía confirmar la recepción; WorkManager gestionará el envío cuando haya red.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessViewModel.kt:40`

```kotlin
    fun submit(c:BusinessCommand,send:Boolean=true,receiptPhoto:String="")=action {
        when(c.action) {
            "CLIENT_CREATE" -> BusinessRules.client(c.client,c.ruc)
            "QUOTE_CREATE","ORDER_CREATE" -> {
                BusinessRules.client(c.client,c.ruc)
                require(c.clientId.isNotBlank()) { "Selecciona un cliente registrado." }
                if(c.reference.isBlank()) BusinessRules.lines(c.lines,mutable.value.products)
            }
            "PURCHASE_CREATE" -> {
                require(c.supplierRuc.isNotBlank()) { "Selecciona un proveedor." }
                BusinessRules.lines(c.lines,mutable.value.products,true)
                PurchaseRules.metadata(c.budgetPeriod,c.deliveryDate,c.supplierQuote)
            }
            "BUDGET_SET" -> { PurchaseRules.period(c.target); PurchaseRules.available(c.budgetCents,mutable.value.budget.reservedCents,mutable.value.budget.spentCents) }
            "PURCHASE_STATE" -> PurchaseRules.reason(c.reason)
            "PURCHASE_RECEIVE" -> PurchaseRules.receipt(c.receiptDocument,c.receiptNotes)
            "QUOTE_STATE", "ORDER_STATE" -> BusinessRules.decision(c.reason, c.responseChannel, c.action == "QUOTE_STATE")
            "MOVEMENT" -> {
                require(c.reason.trim().length in 5..240) { "Indica un motivo de al menos cinco caracteres." }
                val p=mutable.value.products.first { it.id==c.target }
                BusinessRules.stock(c.type,p.stock,c.quantity,c.expectedStock)
            }
        }
        val label=mapOf("CLIENT_CREATE" to "Registrar cliente","QUOTE_CREATE" to "Crear cotización","QUOTE_STATE" to "Registrar respuesta del cliente",
            "ORDER_CREATE" to "Crear pedido","ORDER_STATE" to "Validar pedido","SALE_CREATE" to "Confirmar venta",
            "BUDGET_SET" to "Configurar presupuesto", "PURCHASE_STATE" to "Autorizar/cancelar compra", "PURCHASE_CREATE" to "Crear orden de compra","PURCHASE_RECEIVE" to "Recibir compra","MOVEMENT" to "Movimiento de stock")[c.action] ?: "Operación"
        if(c.action=="PURCHASE_RECEIVE" && send) queue.purchaseReceipt(c,receiptPhoto)
        else queue.save("BUSINESS",label+" · "+c.client.ifBlank { c.target.take(12) },c,send)
        mutable.value=mutable.value.copy(saved=mutable.value.saved+1,message=if(c.action.startsWith("PURCHASE") || c.action=="BUDGET_SET") { if(send) "Cambios guardados para enviar. Actualiza en unos momentos para consultar el resultado." else "Borrador guardado. Puedes enviarlo desde Pendientes." } else if(send) "Operación en cola. Su estado definitivo se confirma en Borradores y sincronización; actualiza después el listado." else "Borrador guardado en SQLite, sin enviar.")
    }
```

## 3. Evitar duplicados

“Antes de aplicar un comando consultamos su identificador. Si ya está confirmado y corresponde al mismo usuario y contenido, no repetimos sus efectos.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:49`

```kotlin
    override suspend fun execute(id: String, owner: String, command: BusinessCommand) {
        require(owner() == owner) { "La operación pertenece a otra cuenta." }
        val audit = db.collection("operaciones_v2").document(id)
        db.runTransaction { tx ->
            val done = tx.get(audit)
            val fingerprint = gson.toJson(command)
            if (done.exists()) {
                require(done.getString("owner") == owner && gson.fromJson(done.getString("payload"), BusinessCommand::class.java) == command) { "Identificador de operación reutilizado." }
            } else {
                when(command.action) {
                    "CLIENT_CREATE" -> {
                        BusinessRules.client(command.client, command.ruc)
                        val ref = db.collection("clientes_v2").document(if(command.ruc.isBlank()) id else command.ruc)
                        val old = tx.get(ref)
                        require(!old.exists()) { "Ya existe un cliente con ese RUC." }
                        tx.set(ref, mapOf("name" to command.client.trim(), "ruc" to command.ruc, "owner" to owner, "createdAt" to FieldValue.serverTimestamp()))
                    }
                    "QUOTE_CREATE", "ORDER_CREATE", "PURCHASE_CREATE" -> create(tx,id,owner,command)
                    "QUOTE_STATE", "ORDER_STATE" -> state(tx,id,owner,command)
                    "SALE_CREATE", "PURCHASE_RECEIVE" -> fulfill(tx,id,owner,command)
                    "BUDGET_SET" -> budget(tx,id,owner,command)
                    "PURCHASE_STATE" -> purchaseState(tx,id,owner,command)
                    "MOVEMENT" -> manual(tx,id,owner,command)
                    else -> error("Operación comercial desconocida.")
                }
                tx.set(audit, mapOf("owner" to owner, "action" to command.action, "target" to command.target,
                    "payload" to fingerprint, "createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
```

## 4. Aprobar con presupuesto

“Al aprobar, Administración reserva el importe en una transacción que vuelve a validar el disponible. El stock aún no cambia. Si se cancela la aprobada, liberamos esa reserva.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:164`

```kotlin
    private fun purchaseState(tx:Transaction,id:String,owner:String,c:BusinessCommand) {
        PurchaseRules.reason(c.reason)
        val ref=db.collection("compras_v2").document(c.target); val d=tx.get(ref)
        val status=d.getString("status")
        require(d.exists() && ((status=="PENDIENTE" && c.state in listOf("APROBADA","RECHAZADA","CANCELADA")) || (status=="APROBADA" && c.state=="CANCELADA"))) { "Transición de compra no permitida. Actualiza." }
        require(tx.get(db.collection("usuarios").document(owner)).getString("rol")=="ADMIN") { "Solo ADMIN autoriza o cancela compras." }
        if(c.state=="APROBADA" || status=="APROBADA") {
            val period=d.getString("budgetPeriod")?:error("Orden histórica: crea una nueva orden con período.")
            val bRef=db.collection("presupuestos_compras").document(period);val b=tx.get(bRef)
            val total=b.getLong("totalCents")?:0L;val reserved=b.getLong("reservedCents")?:0L;val spent=b.getLong("spentCents")?:0L
            val amount=d.getLong("totalCents")!!
            if(c.state=="APROBADA") require(PurchaseRules.available(total,reserved,spent)>=amount) { "Presupuesto insuficiente. Solicita disponibilidad a administración." }
            val next=reserved+if(c.state=="APROBADA") amount else -amount
            PurchaseRules.available(total,next,spent)
            tx.update(bRef,mapOf("reservedCents" to next,"lastOperation" to id,"lastOrder" to c.target,"updatedAt" to FieldValue.serverTimestamp()))
        }
        tx.update(ref,mapOf("status" to c.state,"decisionBy" to owner,"decisionAt" to FieldValue.serverTimestamp(),"decisionReason" to c.reason.trim(),"responseChannel" to "INTERNO","lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()))
    }
```

## 5. Recibir y respaldar sin salir del formulario

“Cuando llega la mercadería comprobamos cada cantidad. Registramos el documento de entrega y, opcionalmente, tomamos una foto en esta misma pantalla. La lectura automática solo sugiere el número si el campo está vacío; lo revisamos antes de confirmar. No extraemos automáticamente productos ni precios de una cotización.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:121`

```kotlin
@Composable private fun PurchaseReceptionForm(r:BusinessRecord,s:BusinessState,vm:BusinessViewModel) {
    var document by rememberSaveable(r.id){mutableStateOf("")}
    var notes by rememberSaveable(r.id){mutableStateOf("")}
    var checked by rememberSaveable(r.id){mutableStateOf(arrayListOf<String>())}
    var photo by rememberSaveable(r.id){mutableStateOf("")}
    var scanning by remember{mutableStateOf(false)}
    Text("Verifica físicamente cada producto y su cantidad completa. No confirmes entregas parciales o productos con observaciones pendientes.")
    r.lines.forEach { l-> Row { Checkbox(l.productId in checked,{yes->checked=ArrayList(if(yes)(checked+l.productId).distinct() else checked-l.productId)});Text("Recibí conforme ${l.quantity} de ${l.name}",Modifier.padding(top=12.dp)) } }
    OutlinedTextField(document,{document=it},Modifier.fillMaxWidth(),label={Text("Número de factura o guía del proveedor")})
    OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("Observaciones / referencia documental (opcional)")})
    PurchasePhotoCapture(photo,onPhoto={path,number->photo=path;if(document.isBlank()&&number.isNotBlank())document=number},onBusy={scanning=it})
    ConfirmButton("Confirmar recepción completa","Se sumarán las cantidades verificadas al inventario cuando se confirme el envío. La fotografía, si la incluyes, se enviará por separado.",!s.busy&&!scanning&&checked.size==r.lines.size){vm.submit(BusinessCommand("PURCHASE_RECEIVE",r.id,receiptDocument=document.trim(),receiptNotes=notes.trim()),receiptPhoto=photo)}
}

```

## 6. Conservar recepción y foto juntas localmente

“Al confirmar guardamos recepción y fotografía como dos tareas relacionadas en una inserción local transaccional. Si cerramos la app, ambas conservan su identidad y vínculo. La segunda no es una nueva entrada de inventario.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/data/local/QueueRepository.kt:21`

```kotlin
    suspend fun purchaseReceipt(command:pe.edu.unmsm.siderurgica.domain.model.BusinessCommand,imagePath:String) {
        require(command.action=="PURCHASE_RECEIVE")
        val uid=owner();val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
        val operations=mutableListOf(PendingOperation(id,uid,"BUSINESS","Recepción de compra",Gson().toJson(command),"PENDIENTE",now))
        if(imagePath.isNotBlank()) operations.add(PendingOperation(UUID.randomUUID().toString(),uid,"PURCHASE_PHOTO","Foto de la entrega",Gson().toJson(pe.edu.unmsm.siderurgica.domain.model.PurchasePhoto(command.target,imagePath,id)),"PENDIENTE",now+1))
        // A Room @Insert of a list is transactional: receipt and optional photo survive together.
        dao.insertOperations(operations)
        schedule(uid)
    }
```

## 7. Ejecutar la cola

“WorkManager procesa cada tarea según su tipo. La recepción se confirma como operación comercial y la fotografía tiene su propio envío. Los estados de envío permiten reintentar solo lo que falló.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:28`

```kotlin
                when (operation.kind) {
                    "BUSINESS" -> koin.get<pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository>().execute(operation.id, owner, Gson().fromJson(operation.payload, BusinessCommand::class.java))
                    "RECEIPT" -> { val c = Gson().fromJson(operation.payload, BusinessCommand::class.java); koin.get<pe.edu.unmsm.siderurgica.data.documents.ReceiptRepository>().upload(c.target, c.receiptType, owner) }
                    "QUOTE" -> koin.get<CommerceRepository>().createQuote(operation.id, Gson().fromJson(operation.payload, QuoteDraft::class.java), owner)
                    "SUPPLIER" -> koin.get<FirestoreSupplierRepository>().create(operation.id, Gson().fromJson(operation.payload, SupplierDraft::class.java), owner)
                    "PURCHASE_PHOTO" -> koin.get<PurchaseEvidenceRepository>().upload(operation.id,owner,Gson().fromJson(operation.payload,PurchasePhoto::class.java))
                    "DOCUMENT" -> koin.get<DocumentRepository>().upload(operation.id, owner)
                    else -> error("Tipo de operación desconocido.")
                }
                dao.status(operation.id, owner, "ENVIADO", "Confirmado por Firebase.", operation.attempts + 1)
                if (services.auth.currentUser?.uid == owner) pe.edu.unmsm.siderurgica.notifications.AppNotifications.show(applicationContext, operation.id, "Sincronización completada", "La operación fue confirmada. Consulta Borradores.")
```

## 8. Actualizar inventario atómicamente

“La orden debe estar aprobada. Leemos los productos y guardamos recepción, consumo de reserva, estado de orden y movimientos juntos. Si una validación falla, ninguna línea modifica stock.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:129`

```kotlin
    private fun fulfill(tx: Transaction,id: String,owner: String,c: BusinessCommand) {
        val purchase = c.action == "PURCHASE_RECEIVE"
        val ref = db.collection(if(purchase) "compras_v2" else "pedidos_v2").document(c.target)
        val d=tx.get(ref)
        require(d.exists()) { "Documento no encontrado." }
        require(d.getString("status") == (if(purchase) "APROBADA" else "APROBADO")) { "La operación ya fue completada o cambió de estado." }
        val budgetRef=if(purchase) db.collection("presupuestos_compras").document(d.getString("budgetPeriod")?:error("Orden histórica: crea una orden nueva con período.")) else null
        val budget=budgetRef?.let { tx.get(it) }
        if(purchase) PurchaseRules.receipt(c.receiptDocument,c.receiptNotes)
        val saleRef = db.collection("ventas_v2").document(c.target)
        if(!purchase) require(!tx.get(saleRef).exists()) { "La venta ya existe." }
        val lines=decodeLines(d)
        val products=lines.map { tx.get(db.collection("productos").document(it.productId)) }
        val movements=lines.mapIndexed { i,l -> movement(id,l.productId,l.name,if(purchase) "COMPRA" else "VENTA",products[i].getLong("stock") ?: error("Stock inexistente."),l.quantity,-1,if(purchase) "Recepción de compra" else "Confirmación de venta",owner,c.target) }
        if(purchase) {
            val reserved=budget!!.getLong("reservedCents")?:0
            val spent=budget.getLong("spentCents")?:0
            val total=d.getLong("totalCents")!!
            require(reserved>=total) { "La reserva presupuestal no está disponible." }
            tx.update(budgetRef!!,mapOf("reservedCents" to reserved-total,"spentCents" to spent+total,"lastOperation" to id,"lastOrder" to c.target,"updatedAt" to FieldValue.serverTimestamp()))
            tx.set(db.collection("recepciones_v2").document(c.target),mapOf("orderId" to c.target,"operationId" to id,"receivedBy" to owner,"createdAt" to FieldValue.serverTimestamp(),"document" to c.receiptDocument.trim(),"notes" to c.receiptNotes.trim(),"lines" to lines.map(::lineMap)))
        }
        if(!purchase) tx.set(saleRef,d.data!!.filterKeys { it !in listOf("updatedAt","lastOperation","createdAt") } + mapOf("status" to "CONFIRMADA","reference" to c.target,"lastOperation" to id,"createdAt" to FieldValue.serverTimestamp()))
        tx.update(ref,mapOf("status" to (if(purchase) "RECIBIDA" else "FACTURADO"),"lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()) + if(purchase) mapOf("receivedBy" to owner,"receivedAt" to FieldValue.serverTimestamp(),"receiptDocument" to c.receiptDocument.trim(),"receiptNotes" to c.receiptNotes.trim()) else emptyMap())
        movements.forEach { m -> writeMovement(tx,id,m) }
    }
```

## 9. Subir el respaldo sin repetir stock

“Con la recepción confirmada comprobamos que la imagen pertenece a esa operación y a quien recibió. La subimos a Storage y guardamos su vínculo. Este método no modifica productos. Si falla o se repite el envío, no repetimos la compra.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/PurchaseEvidenceRepository.kt:14`

```kotlin
    suspend fun upload(id:String,owner:String,c:PurchasePhoto) {
        require(services.auth.currentUser?.uid==owner){"Inicia sesión con la cuenta que registró la recepción."}
        val dependency=dao.operation(c.receiptOperation,owner)
        check(dependency==null || dependency.status=="ENVIADO"){"Primero confirma la recepción pendiente. Luego reintenta la foto; no registres otra entrada de stock."}
        val order=services.database.collection("recepciones_v2").document(c.orderId)
        val receipt=order.get(Source.SERVER).await()
        require(receipt.getString("receivedBy")==owner && receipt.getString("operationId")==c.receiptOperation){"La foto no corresponde a esta recepción."}
        val meta=order.collection("adjuntos").document(id)
        if(meta.get(Source.SERVER).await().exists())return
        val file=File(c.imagePath).canonicalFile
        val directory=File(context.filesDir,"documentos").canonicalFile
        require(file.toPath().startsWith(directory.toPath()) && file.isFile && file.length() in 1 until 10*1024*1024){"La fotografía no está disponible o supera 10 MB. La recepción conserva su registro."}
        val path="compras/${c.orderId}/$id.jpg"
        val storage=FirebaseStorage.getInstance().apply{maxUploadRetryTimeMillis=60_000;maxOperationRetryTimeMillis=30_000}
        storage.reference.child(path).putFile(Uri.fromFile(file),StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        check(services.auth.currentUser?.uid==owner){"La sesión cambió. Reintenta la foto con la cuenta original."}
        services.database.runTransaction { tx->
            if(!tx.get(meta).exists()) tx.set(meta,mapOf("owner" to owner,"storagePath" to path,"receiptOperation" to c.receiptOperation,"createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }
```

## 10. Cierre y verificación

“Volvemos al detalle y vemos la compra recibida, su documento y la foto cuando termina su envío. En inventario comprobamos el aumento y el movimiento con la orden de origen. Así distinguimos la recepción física del respaldo documental y conservamos la trazabilidad incluso con pérdida de conexión.”

Abre `firestore.rules` en `compras_v2`, `recepciones_v2`, sus `adjuntos`, presupuesto y movimientos. En `storage.rules`, la ruta `compras/{orderId}/{fileName}` limita subida al receptor y lectura a ADMIN/ALMACEN. Ocultar botones no reemplaza estas comprobaciones.

La cotización del proveedor se registra manualmente por referencia; la fotografía es opcional y se usa en la entrega. El límite actual es 3 productos. Las entregas parciales y pagos quedan fuera de este avance.
