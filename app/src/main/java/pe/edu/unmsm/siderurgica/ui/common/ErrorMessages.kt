package pe.edu.unmsm.siderurgica.ui.common

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

fun userMessage(error: Throwable): String = when (error) {
    is retrofit2.HttpException -> when(error.code()) {
        401, 403 -> "No se autorizó la consulta. Revisa tu sesión o contacta al administrador."
        404, 422 -> "No se encontró una empresa válida para ese RUC."
        429 -> "Se alcanzó el límite de consultas. Intenta más tarde o completa los datos manualmente."
        else -> "La API externa no pudo responder. Puedes registrar el proveedor manualmente."
    }
    is android.content.ActivityNotFoundException -> "No hay una aplicación compatible para abrir este archivo. Puedes guardarlo o compartirlo con otra aplicación."
    is com.google.firebase.storage.StorageException -> "No se pudo acceder al archivo en Storage. Revisa que exista, la configuración, los permisos y la conexión. Los archivos locales se conservan."
    is FirebaseNetworkException -> "No hay conexión. Revisa la red y vuelve a intentar."
    is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos."
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ese correo ya tiene una cuenta. Ingresa o recupera tu contraseña."
        "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple la política de Firebase."
        "ERROR_USER_DISABLED" -> "La cuenta está deshabilitada. Contacta al administrador."
        "ERROR_INVALID_EMAIL" -> "Revisa el formato del correo."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Habilita Correo/contraseña en Firebase Authentication."
        else -> "No pudimos completar el acceso. Revisa el correo y la contraseña."
    }
    is FirebaseFirestoreException -> when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore no autorizó la operación. Revisa tu rol y las reglas publicadas."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "No se pudo conectar a Firestore. Revisa la red y que hayas creado la base de datos."
        else -> "No se pudo completar la operación en Firestore. Vuelve a intentar."
    }
    is IllegalArgumentException, is IllegalStateException -> error.message ?: "Revisa los datos ingresados."
    else -> "No se pudo completar la operación. Vuelve a intentar."
}
