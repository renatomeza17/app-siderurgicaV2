package pe.edu.unmsm.siderurgica.notifications
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import pe.edu.unmsm.siderurgica.worker.InventoryReviewWorker
import pe.edu.unmsm.siderurgica.ui.common.Notice

@Composable fun NotificationSetup(owner:String,warehouse:Boolean){
    val context=LocalContext.current
    var message by remember{mutableStateOf("")}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->message=if(granted)"Notificaciones habilitadas." else "Puedes consultar el resultado de sincronización dentro de Borradores."}
    Text("Notificaciones de sincronización e inventario")
    OutlinedButton(onClick={if(Build.VERSION.SDK_INT>=33)request.launch(Manifest.permission.POST_NOTIFICATIONS)else message="Revisa el permiso de notificaciones en Ajustes."}){Text("Habilitar notificaciones")}
    if(warehouse)OutlinedButton(onClick={InventoryReviewWorker.schedule(context,owner);message="Revisión periódica programada. Android decide el momento de ejecución; FCM requiere desplegar el emisor incluido."}){Text("Activar revisión periódica de inventario")}
    Notice(message)
}
