package pe.edu.unmsm.siderurgica.ui.screens.business

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.domain.repository.*
import pe.edu.unmsm.siderurgica.domain.rules.BusinessRules
import pe.edu.unmsm.siderurgica.domain.rules.PurchaseRules
import pe.edu.unmsm.siderurgica.data.local.QueueRepository
import pe.edu.unmsm.siderurgica.data.remote.RucLookup
import pe.edu.unmsm.siderurgica.firebase.FirestoreSupplierRepository
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class BusinessState(val products:List<Product> = emptyList(),val clients:List<BusinessClient> = emptyList(),
    val suppliers:List<Supplier> = emptyList(),val records:List<BusinessRecord> = emptyList(),
    val movements:List<StockMovement> = emptyList(),val busy:Boolean = false,val message:String = "",val saved:Int = 0,val lookupName:String = "", val role:String = "", val budget:PurchaseBudget = PurchaseBudget(), val period:String = java.time.YearMonth.now().toString())
class BusinessViewModel(private val repo:BusinessRepository,private val commerce:CommerceRepository,
    private val suppliers:FirestoreSupplierRepository,private val queue:QueueRepository,private val lookup:RucLookup):ViewModel() {
    private val mutable=MutableStateFlow(BusinessState())
    val state=mutable.asStateFlow()
    fun refresh(kind:String)=action {
        val role=repo.currentRole()
        val budget=if(kind=="purchases") repo.purchaseBudget(mutable.value.period) else mutable.value.budget
        val products=commerce.products()
        val clients=if(kind in listOf("quotes","orders","sales")) repo.clients() else emptyList()
        val providers=if(kind=="purchases") suppliers.list() else emptyList()
        val records=if(kind=="movements") emptyList() else repo.records(kind)
        val movements=if(kind=="movements") repo.movements() else emptyList()
        mutable.value=mutable.value.copy(products=products,clients=clients,suppliers=providers,records=records,movements=movements,role=role,budget=budget)
    }
    fun changePeriod(period:String)=action { PurchaseRules.period(period); val b=repo.purchaseBudget(period); mutable.value=mutable.value.copy(period=period,budget=b) }
    fun clearMessage() { mutable.value=mutable.value.copy(message="") }
    fun lookupClient(ruc:String)=action {
        mutable.value=mutable.value.copy(lookupName=lookup.lookup(ruc).name,message="Datos consultados. Revisa la razón social antes de registrar el cliente.")
    }
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
    private fun action(block:suspend ()->Unit) {
        if(mutable.value.busy)return
        mutable.value=mutable.value.copy(busy=true,message="")
        viewModelScope.launch { try { block() } catch(e:Exception) { if(e is CancellationException)throw e;mutable.value=mutable.value.copy(message=userMessage(e)) } finally {mutable.value=mutable.value.copy(busy=false)} }
    }
}
