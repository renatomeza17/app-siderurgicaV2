package pe.edu.unmsm.siderurgica.domain.rules

import pe.edu.unmsm.siderurgica.domain.model.SupplierDraft
object SupplierRules {
    fun validate(d: SupplierDraft) {
        require(InputRules.validRuc(d.ruc)) { "Ingresa un RUC válido de 11 dígitos." }
        require(d.name.trim().length in 2..160) { "Completa la razón social (2 a 160 caracteres)." }
        require(d.address.length <= 240 && d.contact.length <= 80 && d.phone.length <= 30) { "Revisa la longitud de los datos del proveedor." }
    }
}
