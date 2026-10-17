package pe.edu.unmsm.siderurgica.firebase

import com.google.firebase.firestore.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.data.local.*
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository
import pe.edu.unmsm.siderurgica.domain.rules.BusinessRules

class FirestoreBusinessRepository(private val services: FirebaseServices, private val cache: DeviceCache) : BusinessRepository {
    private val db get() = services.database
    private val gson = Gson()
    private fun owner() = services.auth.currentUser?.uid ?: error("Inicia sesión.")
    private fun collection(kind: String) = when(kind) { "quotes" -> "cotizaciones_v2"; "orders" -> "pedidos_v2"; "sales" -> "ventas_v2"; "purchases" -> "compras_v2"; else -> error("Listado inválido.") }
    private suspend inline fun <reified T> cached(key: String, crossinline load: suspend () -> List<T>): List<T> {
        val uid = owner()
        val type = object : TypeToken<List<T>>() {}.type
        if (!cache.online) return cache.read<List<T>>(uid, key, type) ?: emptyList()
        return try { load().also { cache.save(uid, key, it) } }
        catch(e: Exception) { if (e is CancellationException || !networkFailure(e)) throw e; cache.read<List<T>>(uid,key,type) ?: throw e }
    }
    override suspend fun records(kind: String): List<BusinessRecord> = cached("v2-$kind") {
        db.collection(collection(kind)).get(Source.SERVER).await().documents.map { d ->
            gson.fromJson(gson.toJson(d.data.orEmpty().filterKeys { it !in listOf("createdAt", "updatedAt") }), BusinessRecord::class.java)
                .copy(id=d.id, createdAt=d.getTimestamp("createdAt")?.toDate()?.time ?: 0)
        }.sortedByDescending { it.createdAt }
    }
    override suspend fun clients(): List<BusinessClient> = cached("v2-clients") {
        db.collection("clientes_v2").get(Source.SERVER).await().documents.map { BusinessClient(it.id,it.getString("name").orEmpty(),it.getString("ruc").orEmpty()) }.sortedBy { it.name }
    }
    override suspend fun movements(): List<StockMovement> = cached("v2-movements") {
        db.collection("movimientos_v2").get(Source.SERVER).await().documents.map { d ->
            gson.fromJson(gson.toJson(d.data.orEmpty().filterKeys { it != "createdAt" }), StockMovement::class.java)
                .copy(id=d.id, createdAt=d.getTimestamp("createdAt")?.toDate()?.time ?: 0)
        }.sortedByDescending { it.createdAt }
    }
    override suspend fun execute(id: String, owner: String, command: BusinessCommand) {
        require(owner() == owner) { "La operación pertenece a otra cuenta." }
        val audit = db.collection("operaciones_v2").document(id)
        db.runTransaction { tx ->
            val done = tx.get(audit)
            val fingerprint = gson.toJson(command)
            if (done.exists()) {
                require(done.getString("owner") == owner && done.getString("payload") == fingerprint) { "Identificador de operación reutilizado." }
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
                    "MOVEMENT" -> manual(tx,id,owner,command)
                    else -> error("Operación comercial desconocida.")
                }
                tx.set(audit, mapOf("owner" to owner, "action" to command.action, "target" to command.target,
                    "payload" to fingerprint, "createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    private fun product(d: DocumentSnapshot) = Product(d.id,d.getString("nombre").orEmpty(),d.getString("categoria").orEmpty(),d.getLong("precioCentimos") ?: 0,d.getLong("stock") ?: 0,d.getLong("stockMinimo") ?: 0)
    private fun create(tx: Transaction,id: String,owner: String,c: BusinessCommand) {
        val purchase = c.action == "PURCHASE_CREATE"
        val order = c.action == "ORDER_CREATE"
        val kind = if(purchase) "compras_v2" else if(order) "pedidos_v2" else "cotizaciones_v2"
        val targetId = if(order && c.reference.isNotBlank()) c.reference else id
        val ref = db.collection(kind).document(targetId)
        require(!tx.get(ref).exists()) { "El documento ya fue creado." }
        val quote = if(order && c.reference.isNotBlank()) tx.get(db.collection("cotizaciones_v2").document(c.reference)) else null
        val client = if(purchase) null else tx.get(db.collection("clientes_v2").document(c.clientId))
        val supplier = if(purchase) tx.get(db.collection("proveedores").document(c.supplierRuc)) else null
        if(purchase) require(supplier?.exists() == true) { "Selecciona un proveedor registrado y sincronizado." }
        else {
            require(client?.exists() == true) { "Sincroniza primero el cliente seleccionado." }
            require(client?.getString("name") == c.client.trim() && client.getString("ruc") == c.ruc) { "Los datos del cliente cambiaron." }
            BusinessRules.client(c.client,c.ruc)
        }
        val snapshots = c.lines.map { tx.get(db.collection("productos").document(it.productId)) }
        require(snapshots.all { it.exists() }) { "Producto no encontrado." }
        val lines = if(quote != null) {
            require(quote.getString("status") == "APROBADA") { "Primero aprueba la cotización." }
            require(quote.getString("clientId") == c.clientId) { "Cliente distinto a la cotización." }
            decodeLines(quote)
        } else BusinessRules.lines(c.lines,snapshots.map(::product),purchase)
        require(lines.size in 1..3 && lines.map { it.productId } == c.lines.map { it.productId }) { "Detalle inconsistente." }
        if(order) lines.forEach { l -> require(snapshots.first { it.id==l.productId }.getLong("stock")!! >= l.quantity) { "Stock insuficiente para ${l.name}." } }
        val amounts = BusinessRules.total(lines)
        if(purchase) require(c.budgetCents >= amounts.third && c.budgetCents <= 400_000_000_000_000L) { "El total supera el presupuesto." }
        val data = mapOf("owner" to owner,"client" to (if(purchase) supplier!!.getString("nombre").orEmpty() else c.client.trim()),
            "ruc" to (if(purchase) c.supplierRuc else c.ruc),"clientId" to c.clientId,
            "lines" to lines.map(::lineMap),"status" to "PENDIENTE", "subtotalCents" to amounts.first,"taxCents" to amounts.second,
            "totalCents" to amounts.third,"reference" to c.reference,"supplierRuc" to c.supplierRuc,"budgetCents" to c.budgetCents,
            "lastOperation" to id,"createdAt" to FieldValue.serverTimestamp())
        tx.set(ref,data)
        if(quote != null) tx.update(quote.reference,mapOf("status" to "CONVERTIDA","lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()))
    }
    private fun state(tx: Transaction,id: String,owner: String,c: BusinessCommand) {
        val quote = c.action == "QUOTE_STATE"
        val ref = db.collection(if(quote) "cotizaciones_v2" else "pedidos_v2").document(c.target)
        val d = tx.get(ref)
        require(d.exists() && d.getString("status") == "PENDIENTE") { "El documento ya no está pendiente. Actualiza el listado." }
        require(c.state in (if(quote) listOf("APROBADA","RECHAZADA") else listOf("APROBADO","RECHAZADO"))) { "Estado inválido." }
        if(!quote && c.state=="APROBADO") decodeLines(d).forEach { l ->
            val p=tx.get(db.collection("productos").document(l.productId))
            require((p.getLong("stock") ?: 0)>=l.quantity) { "Stock insuficiente para ${l.name}." }
        }
        tx.update(ref,mapOf("status" to c.state,"lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()))
    }
    private fun fulfill(tx: Transaction,id: String,owner: String,c: BusinessCommand) {
        val purchase = c.action == "PURCHASE_RECEIVE"
        val ref = db.collection(if(purchase) "compras_v2" else "pedidos_v2").document(c.target)
        val d=tx.get(ref)
        require(d.exists()) { "Documento no encontrado." }
        require(d.getString("status") == (if(purchase) "PENDIENTE" else "APROBADO")) { "La operación ya fue completada o cambió de estado." }
        val saleRef = db.collection("ventas_v2").document(c.target)
        if(!purchase) require(!tx.get(saleRef).exists()) { "La venta ya existe." }
        val lines=decodeLines(d)
        val products=lines.map { tx.get(db.collection("productos").document(it.productId)) }
        val movements=lines.mapIndexed { i,l -> movement(id,l.productId,l.name,if(purchase) "COMPRA" else "VENTA",products[i].getLong("stock") ?: error("Stock inexistente."),l.quantity,-1,if(purchase) "Recepción de compra" else "Confirmación de venta",owner,c.target) }
        if(!purchase) tx.set(saleRef,d.data!!.filterKeys { it !in listOf("updatedAt","lastOperation","createdAt") } + mapOf("status" to "CONFIRMADA","reference" to c.target,"lastOperation" to id,"createdAt" to FieldValue.serverTimestamp()))
        tx.update(ref,mapOf("status" to (if(purchase) "RECIBIDA" else "FACTURADO"),"lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()))
        movements.forEach { m -> writeMovement(tx,id,m) }
    }
    private fun manual(tx: Transaction,id: String,owner: String,c: BusinessCommand) {
        require(c.type in listOf("ENTRADA","SALIDA","AJUSTE") && c.reason.trim().length in 5..240) { "Indica tipo y motivo (5–240 caracteres)." }
        val p=tx.get(db.collection("productos").document(c.target))
        require(p.exists()) { "Producto no encontrado." }
        writeMovement(tx,id,movement(id,p.id,p.getString("nombre").orEmpty(),c.type,p.getLong("stock")!!,c.quantity,c.expectedStock,c.reason.trim(),owner,""))
    }
    private fun movement(id:String,p:String,name:String,type:String,before:Long,quantity:Long,expected:Long,reason:String,owner:String,source:String): Map<String,Any> = mapOf(
        "productId" to p,"productName" to name,"type" to type,"quantity" to quantity,"beforeStock" to before,
        "afterStock" to BusinessRules.stock(type,before,quantity,expected),"reason" to reason,"actor" to owner,"source" to source,
        "operationId" to id,"createdAt" to FieldValue.serverTimestamp())
    private fun writeMovement(tx:Transaction,id:String,m:Map<String,Any>) {
        val key=id+"_"+m["productId"]
        tx.set(db.collection("movimientos_v2").document(key),m)
        tx.update(db.collection("productos").document(m["productId"] as String),mapOf("stock" to m["afterStock"],"lastMovement" to key,"updatedAt" to FieldValue.serverTimestamp()))
    }
    private fun decodeLines(d:DocumentSnapshot): List<BusinessLine> = gson.fromJson(gson.toJson(d.get("lines")),object:TypeToken<List<BusinessLine>>(){}.type)
    private fun lineMap(l:BusinessLine) = mapOf("productId" to l.productId,"name" to l.name,"quantity" to l.quantity,"unitCents" to l.unitCents,"discountPercent" to l.discountPercent,"grossCents" to l.grossCents,"netCents" to l.netCents)
}
