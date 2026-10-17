package pe.edu.unmsm.siderurgica.domain.model

data class SupplierDraft(val ruc: String, val name: String, val address: String, val contact: String, val phone: String)
data class Supplier(val ruc: String, val name: String, val address: String, val contact: String, val phone: String)
data class RucResult(val ruc: String, val name: String, val address: String, val status: String, val condition: String)
