package pe.edu.unmsm.siderurgica.ui.screens.business
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.edu.unmsm.siderurgica.data.documents.DocumentScanner
import pe.edu.unmsm.siderurgica.firebase.PurchaseEvidenceRepository
import pe.edu.unmsm.siderurgica.ui.common.*
import java.io.File

@Composable internal fun PurchasePhotoCapture(path:String,onPhoto:(String,String)->Unit,onBusy:(Boolean)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var capture by rememberSaveable{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    fun scan(uri:android.net.Uri){busy=true;onBusy(true);scope.launch {
        try {val result=DocumentScanner.scan(context,uri);onPhoto(result.imagePath,result.invoice.number);message=if(result.invoice.number.isBlank())"Foto preparada. Escribe el número del documento." else "Número sugerido por lectura de imagen. Revísalo antes de confirmar."}
        catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}
        finally{busy=false;onBusy(false)}
    }}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok&&capture.isNotBlank())scan(android.net.Uri.parse(capture))}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let(::scan)}
    OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Foto del documento · opcional",style=MaterialTheme.typography.titleMedium)
        Text("Conserva una imagen de la guía o factura como respaldo de esta entrega. Puedes continuar solo con el número.")
        if(path.isNotBlank()) {
            PurchaseImage(path)
            TextButton(onClick={onPhoto("","");message=""},enabled=!busy){Text("Quitar foto")}
        }
        OutlinedButton(onClick={
            val file=File(context.cacheDir,"capturas/${java.util.UUID.randomUUID()}.jpg").apply{parentFile?.mkdirs()}
            capture=FileProvider.getUriForFile(context,"${context.packageName}.files",file).toString()
            runCatching{camera.launch(android.net.Uri.parse(capture))}.onFailure{message="No se pudo abrir la cámara. Puedes elegir una imagen."}
        },enabled=!busy){Text(if(path.isBlank())"Tomar foto de guía o factura" else "Volver a tomar foto")}
        TextButton(onClick={gallery.launch("image/*")},enabled=!busy){Text("Elegir una imagen")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Notice(message)
    }}
}
@Composable private fun PurchaseImage(path:String){
    val bitmap=remember(path){android.graphics.BitmapFactory.decodeFile(path,android.graphics.BitmapFactory.Options().apply{inSampleSize=2})}
    if(bitmap!=null)Image(bitmap.asImageBitmap(),"Documento de entrega",Modifier.fillMaxWidth().height(200.dp),contentScale=ContentScale.Fit)
}
@Composable internal fun PurchaseEvidenceButton(orderId:String){
    val repository:PurchaseEvidenceRepository=koinInject();val scope=rememberCoroutineScope()
    var path by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    OutlinedButton(onClick={busy=true;message="";scope.launch{try{path=repository.download(orderId).absolutePath}catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}finally{busy=false}}},enabled=!busy){Text(if(busy)"Buscando fotografía…" else "Consultar foto de la entrega")}
    Notice(message)
    if(path.isNotBlank())AlertDialog(onDismissRequest={path=""},title={Text("Documento de entrega")},text={PurchaseImage(path)},confirmButton={TextButton(onClick={path=""}){Text("Cerrar")}})
}
