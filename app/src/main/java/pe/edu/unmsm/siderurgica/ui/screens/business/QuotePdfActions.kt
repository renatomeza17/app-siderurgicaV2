package pe.edu.unmsm.siderurgica.ui.screens.business

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import pe.edu.unmsm.siderurgica.data.documents.QuotePdfRepository
import pe.edu.unmsm.siderurgica.domain.model.BusinessRecord
import pe.edu.unmsm.siderurgica.ui.common.*
import java.io.File

@Composable internal fun QuotePdfActions(quote:BusinessRecord){
    val context=LocalContext.current
    val repository:QuotePdfRepository=koinInject()
    val scope=rememberCoroutineScope()
    var busy by remember(quote.id){mutableStateOf(false)}
    var file by remember(quote.id){mutableStateOf<File?>(null)}
    var message by remember(quote.id){mutableStateOf("")}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        if(uri!=null)scope.launch{
            try{val source=file?:error("Genera la cotización primero.")
                withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{out->source.inputStream().use{it.copyTo(out)}}?:error("No se pudo guardar el archivo.")}
                message="Cotización guardada en la ubicación elegida."
            }catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}
        }
    }
    fun perform(action:suspend()->Unit){if(busy)return;busy=true;scope.launch{try{action()}catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}finally{busy=false}}}
    SectionTitle("Propuesta para el cliente")
    Button(onClick={perform{file=repository.generate(quote);message="PDF preparado con los importes guardados. Puedes revisarlo y compartirlo."}},enabled=!busy){Text("Generar PDF de cotización")}
    if(file?.exists()==true){
        OutlinedButton(onClick={perform{
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file!!)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }},enabled=!busy){Text("Ver PDF de cotización")}
        OutlinedButton(onClick={save.launch("COT-${quote.id}.pdf")},enabled=!busy){Text("Guardar PDF")}
        OutlinedButton(onClick={perform{
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file!!)
            val intent=Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri)
                .apply { clipData=ClipData.newRawUri("Cotización",uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent,"Compartir cotización con el cliente"))
        }},enabled=!busy){Text("Compartir cotización")}
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    Notice(message)
}
