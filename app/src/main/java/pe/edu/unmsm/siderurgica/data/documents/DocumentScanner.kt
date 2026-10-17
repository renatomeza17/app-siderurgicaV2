package pe.edu.unmsm.siderurgica.data.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pe.edu.unmsm.siderurgica.domain.rules.InvoiceParser
import pe.edu.unmsm.siderurgica.domain.rules.ExtractedInvoice
import java.io.File

data class ScanResult(val imagePath: String, val invoice: ExtractedInvoice, val codes: String, val rawText: String)
object DocumentScanner {
    suspend fun scan(context: Context, uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "No se pudo leer la imagen. Prueba otra fotografía." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("No se pudo abrir la imagen.")
        val exif = context.contentResolver.openInputStream(uri).use { stream -> stream?.let { androidx.exifinterface.media.ExifInterface(it) } }
        val transform = android.graphics.Matrix().apply {
            if(exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        if(bitmap !== decoded) decoded.recycle()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val barcode = BarcodeScanning.getClient()
        try {
            // OCR and saved JPEG share the same orientation and bounded memory usage.
            val input = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(input).await().text
            val codes = barcode.process(input).await().mapNotNull { it.rawValue }.joinToString("\n")
            val file = File(context.filesDir, "documentos/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            ScanResult(file.absolutePath, InvoiceParser.parse(text), codes, text)
        } finally { bitmap.recycle(); recognizer.close(); barcode.close() }
    }
}
