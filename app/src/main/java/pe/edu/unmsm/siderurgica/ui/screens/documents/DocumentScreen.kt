package pe.edu.unmsm.siderurgica.ui.screens.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.ui.common.*
import java.io.File

@Composable fun DocumentScreen(vm: DocumentViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var capture by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) capture?.let(vm::process) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::process) }
    var ruc by remember(state.path) { mutableStateOf(state.ruc) }
    var number by remember(state.path) { mutableStateOf(state.number) }
    var date by remember(state.path) { mutableStateOf(state.date) }
    var amount by remember(state.path) { mutableStateOf(state.amount) }
    Page("Documentos y códigos", "Fotografía, reconoce, revisa y guarda.") {
        Notice(state.message)
        Notice(error)
        Notice("El OCR se procesa en el dispositivo. Los campos extraídos pueden contener errores: revísalos antes de confirmar.")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = {
            val file = File(context.cacheDir, "capturas/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
            capture = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            runCatching { camera.launch(capture!!) }.onFailure { error = "No hay cámara disponible. Puedes elegir una imagen." }
        }, enabled = !state.busy && state.path.isBlank(), modifier = Modifier.fillMaxWidth()) { Text("Fotografiar factura o código") }
        OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !state.busy && state.path.isBlank(), modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen") }
        if (state.path.isNotBlank()) {
            SectionTitle("Revisar captura")
            val bitmap = remember(state.path) { android.graphics.BitmapFactory.decodeFile(state.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) }
            if (bitmap != null) Image(bitmap.asImageBitmap(), "Imagen capturada", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
            OutlinedTextField(ruc, { ruc = it }, Modifier.fillMaxWidth(), label = { Text("RUC") })
            OutlinedTextField(number, { number = it }, Modifier.fillMaxWidth(), label = { Text("Número de documento") })
            OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("Fecha dd/mm/aaaa") })
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Monto total S/") })
            if (state.code.isNotBlank()) Text("Código: ${state.code}")
            var showText by remember(state.path) { mutableStateOf(false) }
            TextButton(onClick = { showText = !showText }) { Text("Ver texto reconocido") }
            if (showText) Text(state.text.ifBlank { "No se reconoció texto." }, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.save(ruc.trim(), number.trim(), date.trim(), amount, false) }, enabled = !state.busy) { Text("Confirmar factura local") }
            if (state.code.isNotBlank()) OutlinedButton(onClick = { vm.save("", "", "", "", true) }, enabled = !state.busy) { Text("Guardar solo código") }
            TextButton(onClick = vm::discard, enabled = !state.busy) { Text("Descartar captura") }
        }
        SectionTitle("Documentos guardados")
        if (state.documents.isEmpty()) EmptyCard("Sin documentos", "Captura una imagen y revisa los campos.")
        state.documents.forEach { document -> OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(document.number, style = MaterialTheme.typography.titleLarge)
                if (document.ruc.isNotBlank()) Text("RUC ${document.ruc}")
                Text(document.date)
                if (document.amountCents > 0) Text(money(document.amountCents))
                if (document.code.isNotBlank()) Text("Código: ${document.code}")
                StatusBadge(if (document.storagePath == null) "En dispositivo" else "En Firebase")
                if (document.storagePath == null) ConfirmButton("Enviar imagen a Firebase", "Se subirá la imagen y sus campos revisados cuando haya conexión. Requiere configurar Storage.", !state.busy) { vm.upload(document.id) }
            }
        } }
    }
}
