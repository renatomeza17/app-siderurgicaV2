package pe.edu.unmsm.siderurgica.ui.screens.documents

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.data.documents.DocumentScanner
import pe.edu.unmsm.siderurgica.data.local.LocalDocument
import pe.edu.unmsm.siderurgica.firebase.DocumentRepository
import pe.edu.unmsm.siderurgica.domain.rules.DocumentRules
import pe.edu.unmsm.siderurgica.ui.common.userMessage

data class DocumentState(val documents: List<LocalDocument> = emptyList(), val path: String = "", val ruc: String = "", val number: String = "",
    val date: String = "", val amount: String = "", val code: String = "", val text: String = "", val busy: Boolean = false, val message: String = "")
class DocumentViewModel(private val context: Context, private val repo: DocumentRepository) : ViewModel() {
    private val mutable = MutableStateFlow(DocumentState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { repo.observe().collect { mutable.value = mutable.value.copy(documents = it) } } }
    fun process(uri: Uri) = action {
        val result = DocumentScanner.scan(context, uri)
        val parts = result.invoice.date.replace('-', '/').replace('.', '/').split('/')
        val date = if (parts.size == 3) "${parts[0].padStart(2, '0')}/${parts[1].padStart(2, '0')}/${parts[2]}" else result.invoice.date
        mutable.value = mutable.value.copy(path = result.imagePath, ruc = result.invoice.ruc, number = result.invoice.number,
            date = date, amount = result.invoice.amount, code = result.codes.take(4000), text = result.rawText.take(8000))
    }
    fun save(ruc: String, number: String, date: String, amount: String, codeOnly: Boolean) = action {
        val s = mutable.value
        repo.save(s.path, ruc, number, date, if (codeOnly) 0 else DocumentRules.amount(amount), s.code, codeOnly)
        mutable.value = mutable.value.copy(path = "", text = "", message = "Documento revisado y guardado localmente.")
    }
    fun discard() {
        if (!mutable.value.busy) { java.io.File(mutable.value.path).delete(); mutable.value = mutable.value.copy(path = "") }
    }
    fun upload(id: String) = action { repo.enqueue(id); mutable.value = mutable.value.copy(message = "Imagen pendiente de envío. Puedes revisar su estado en Borradores y sincronización.") }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "")
        viewModelScope.launch {
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
