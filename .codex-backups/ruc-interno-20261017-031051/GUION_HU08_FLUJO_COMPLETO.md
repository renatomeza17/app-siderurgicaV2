# Guion técnico HU08 — de reposición a recepción e inventario

Fragmentos extraídos de las fuentes actuales, en orden de ejecución. No hay un controlador Spring: el punto de entrada es Compose, que delega al ViewModel y al repositorio mediante la cola local.

## 1. Partimos de la necesidad de reposición

“Comenzamos en inventario. Aquí vemos las existencias confirmadas y podemos filtrar productos agotados o con stock bajo. Cuando necesitamos reponer, entramos a compras. No sumamos unidades con una entrada manual: vamos a dejar el documento que explica su origen.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/products/ProductScreen.kt:46`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
        val products = state.products.filter { (it.name.contains(search,true)||it.category.contains(search,true)) && when(stockFilter){"Bajo"->it.stock>0 && it.stock<=it.minimumStock;"Agotado"->it.stock==0L;else->true} }
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
```

## 2. Registramos lo acordado con el proveedor

“Seleccionamos proveedor, producto, cantidad y precio de compra sin IGV. Ese precio viene de la cotización o acuerdo externo; la aplicación no lo obtiene del precio de venta. Al guardar construimos un comando, con período, entrega estimada y referencia del acuerdo. Crear la orden todavía no cambia stock.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessScreen.kt:125`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
    fun command():BusinessCommand{val c=s.clients.firstOrNull{it.id==clientId};return BusinessCommand(if(purchase)"PURCHASE_CREATE" else if(kind=="orders")"ORDER_CREATE" else "QUOTE_CREATE",client=c?.name.orEmpty(),ruc=c?.ruc.orEmpty(),clientId=clientId,lines=lines.toList(),supplierRuc=supplier,budgetPeriod=if(purchase)s.period else "",deliveryDate=if(purchase)delivery else "",supplierQuote=if(purchase)supplierQuote.trim() else "")}
    Button(onClick={vm.submit(command())},enabled=!s.busy&&lines.isNotEmpty()){Text("Guardar y sincronizar")}
    OutlinedButton(onClick={vm.submit(command(),false)},enabled=!s.busy&&lines.isNotEmpty()){Text("Guardar solo borrador")}
```

## 3. Validamos antes de guardar

“El formulario delega al ViewModel. Aquí verificamos proveedor, detalle y metadatos. Los precios y cantidades se validan antes de permitir que la operación pase a la cola.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessViewModel.kt:48`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
            "PURCHASE_CREATE" -> {
                require(c.supplierRuc.isNotBlank()) { "Selecciona un proveedor." }
                BusinessRules.lines(c.lines,mutable.value.products,true)
                PurchaseRules.metadata(c.budgetPeriod,c.deliveryDate,c.supplierQuote)
            }
            "BUDGET_SET" -> { PurchaseRules.period(c.target); PurchaseRules.available(c.budgetCents,mutable.value.budget.reservedCents,mutable.value.budget.spentCents) }
            "PURCHASE_STATE" -> PurchaseRules.reason(c.reason)
            "PURCHASE_RECEIVE" -> PurchaseRules.receipt(c.receiptDocument,c.receiptNotes)
```

## 4. Guardamos primero en el dispositivo

“El ViewModel delega a esta cola. Guardamos el comando en Room, que utiliza SQLite, asociado al usuario y a un identificador estable. Si lo enviamos, programamos WorkManager. Por eso cerrar la pantalla o perder internet no equivale a perder el registro.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/data/local/QueueRepository.kt:15`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
    suspend fun save(kind: String, title: String, value: Any, send: Boolean, id: String = UUID.randomUUID().toString()): String {
        val uid = owner()
        dao.insert(PendingOperation(id, uid, kind, title, Gson().toJson(value), if (send) "PENDIENTE" else "BORRADOR", System.currentTimeMillis()))
        if (send) schedule(uid)
        return id
    }
```

## 5. Sincronizamos con corutinas cuando hay red

“El trabajo exige conexión y conserva su política de reintentos. Cuando se ejecuta, recupera el comando y llama al repositorio. ENVIADO solo se establece después de la confirmación remota. El estado de sincronización no reemplaza al estado comercial de la orden.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:28`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
                when (operation.kind) {
                    "BUSINESS" -> koin.get<pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository>().execute(operation.id, owner, Gson().fromJson(operation.payload, BusinessCommand::class.java))
                    "RECEIPT" -> { val c = Gson().fromJson(operation.payload, BusinessCommand::class.java); koin.get<pe.edu.unmsm.siderurgica.data.documents.ReceiptRepository>().upload(c.target, c.receiptType, owner) }
                    "QUOTE" -> koin.get<CommerceRepository>().createQuote(operation.id, Gson().fromJson(operation.payload, QuoteDraft::class.java), owner)
                    "SUPPLIER" -> koin.get<FirestoreSupplierRepository>().create(operation.id, Gson().fromJson(operation.payload, SupplierDraft::class.java), owner)
                    "DOCUMENT" -> koin.get<DocumentRepository>().upload(operation.id, owner)
                    else -> error("Tipo de operación desconocido.")
                }
                dao.status(operation.id, owner, "ENVIADO", "Confirmado por Firebase.", operation.attempts + 1)
                if (services.auth.currentUser?.uid == owner) pe.edu.unmsm.siderurgica.notifications.AppNotifications.show(applicationContext, operation.id, "Sincronización completada", "La operación fue confirmada. Consulta Borradores.")
```

## 6. Evitamos aplicar dos veces una operación

“Entramos al repositorio Firestore. Primero comprobamos si este identificador ya está confirmado. Si existe, validamos que pertenezca al mismo usuario y al mismo comando. Si es nuevo, ejecutamos la acción y registramos su auditoría dentro de la transacción.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:52`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
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

## 7. Autorizamos con presupuesto compartido

“Cuando ADMIN autoriza, recuperamos la orden y el presupuesto de su período. Comprobamos el disponible y reservamos el importe. Si dos usuarios compiten por el mismo presupuesto, Firestore vuelve a evaluar la transacción con los datos vigentes. Al cancelar una aprobada, liberamos esa reserva. Una compra recibida ya no admite esa cancelación.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:164`. Fragmento exacto; consulta el archivo para el contexto completo.

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

## 8. Comprobamos lo que llegó

“Almacén abre una pantalla distinta para la recepción. Verificamos físicamente cada línea completa y anotamos el documento del proveedor. Este avance trabaja con entregas completas: no confirmamos todo si faltan unidades. Tampoco estamos registrando el pago.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:103`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
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
```

## 9. Aplicamos recepción, presupuesto e inventario juntos

“La recepción exige una orden aprobada. Leemos los productos antes de escribir. Transformamos la reserva en importe recibido, conservando el mismo disponible. Guardamos una recepción con identidad propia ligada a la orden, su documento, usuario y fecha. Finalmente cambiamos el estado y escribimos todas las entradas. Si alguna validación falla, no se aplica una recepción parcial.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:129`. Fragmento exacto; consulta el archivo para el contexto completo.

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

## 10. Cerramos viendo el movimiento de stock

“Este es el punto exacto donde persistimos cada movimiento y el nuevo stock. Ambos quedan asociados a la misma operación. En inventario consultamos el antes, el después y la orden de origen. Con esto demostramos la conexión entre HU08 y HU07 sin registrar otra entrada manual.”

Fuente: `app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:192`. Fragmento exacto; consulta el archivo para el contexto completo.

```kotlin
    private fun writeMovement(tx:Transaction,id:String,m:Map<String,Any>) {
        val key=id+"_"+m["productId"]
        tx.set(db.collection("movimientos_v2").document(key),m)
        tx.update(db.collection("productos").document(m["productId"] as String),mapOf("stock" to m["afterStock"],"lastMovement" to key,"updatedAt" to FieldValue.serverTimestamp()))
    }
```

## 11. Verificamos la seguridad y cerramos la historia

“Ocultar un botón no es suficiente. Las reglas de Firestore vuelven a comprobar que solo ADMIN autorice y que la recepción incluya presupuesto, recepción documental y movimientos coherentes. El control se aplica aunque alguien intente escribir directamente desde otro cliente. El resultado es una compra trazable: sabemos a quién solicitamos, qué autorizamos, qué recibimos y cómo cambió el inventario.”

Abre `firestore.rules` en los bloques `compras_v2`, `presupuestos_compras`, `recepciones_v2`, `movimientos_v2` y `v2Stock`. No confundas el PDF de orden de compra con una factura emitida por el proveedor: el PDF expresa nuestra solicitud y no acredita pago ni recepción.

La prueba completa y los límites están en `GUIA_HU08_COMPRAS_Y_PRUEBAS.md`. La consulta RUC por Retrofit es opcional. La recepción guarda referencia de factura/guía; Documentos/OCR y su imagen en Storage siguen siendo un flujo separado, sin adjunto automático a la orden. No se registran pagos, préstamos ni fabricación.
