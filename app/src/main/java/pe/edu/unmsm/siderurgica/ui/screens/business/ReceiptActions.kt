package pe.edu.unmsm.siderurgica.ui.screens.business

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.edu.unmsm.siderurgica.data.documents.ReceiptRepository
import pe.edu.unmsm.siderurgica.data.local.QueueRepository
import pe.edu.unmsm.siderurgica.domain.model.*
import pe.edu.unmsm.siderurgica.ui.common.*
import java.io.File

@Composable fun ReceiptActions(sale:BusinessRecord){
    val context=LocalContext.current
    val repo:ReceiptRepository=koinInject();val queue:QueueRepository=koinInject()
    val scope=rememberCoroutineScope()
    var type by remember{mutableStateOf("BOLETA")};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var local by remember(sale.id,type){mutableStateOf<File?>(null)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        if(uri!=null)scope.launch{
            try{val file=local?:error("Genera el PDF primero.");kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{output->file.inputStream().use{it.copyTo(output)}}?:error("No se pudo abrir el destino.")};message="PDF guardado."}
            catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}
        }
    }
    fun run(block:suspend ()->Unit){if(busy)return;busy=true;scope.launch{try{block()}catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}finally{busy=false}}}
    Text("Comprobante académico; sin emisión tributaria.")
    Row{listOf("BOLETA","FACTURA").forEach{t->FilterChip(type==t,{type=t},label={Text(t)},enabled=!busy)}}
    Button(onClick={run{local=repo.generate(sale,type);message="PDF generado. Puedes previsualizarlo, guardarlo o compartirlo."}},enabled=!busy){Text("Generar PDF")}
    if(local?.exists()==true){
        OutlinedButton(onClick={run{
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",local!!)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }},enabled=!busy){Text("Vista previa PDF")}
        OutlinedButton(onClick={save.launch("${sale.id}_$type.pdf")},enabled=!busy){Text("Guardar PDF en el dispositivo")}
        OutlinedButton(onClick={run{
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",local!!)
            val intent=Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent,"Compartir comprobante"))
        }},enabled=!busy){Text("Compartir con Android")}
    }
    OutlinedButton(onClick={run{queue.save("RECEIPT","PDF ${sale.id.take(8)}",BusinessCommand("RECEIPT_UPLOAD",sale.id,receiptType=type),true);message="Subida de PDF en cola. Revisa Borradores."}},enabled=!busy){Text("Subir PDF a Firebase Storage")}
    TextButton(onClick={run{local=repo.download(sale.id,type);message="PDF descargado de Storage."}},enabled=!busy){Text("Descargar copia de Storage")}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    Notice(message)
}
