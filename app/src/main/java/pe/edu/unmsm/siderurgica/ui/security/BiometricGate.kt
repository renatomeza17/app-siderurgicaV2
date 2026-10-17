package pe.edu.unmsm.siderurgica.ui.security

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable fun BiometricGate(activity: FragmentActivity, uid: String, logout: () -> Unit,
    content: @Composable (Boolean, Boolean, (Boolean) -> Unit) -> Unit) {
    val settings = remember(activity) { activity.getSharedPreferences("biometric-preferences", 0) }
    var enabled by remember(uid) { mutableStateOf(settings.getBoolean(uid, false)) }
    var unlocked by remember(uid) { mutableStateOf(!enabled) }
    var available by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, activity, enabled) {
        fun check() { available = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS }
        check()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && enabled) unlocked = false
            if (event == Lifecycle.Event.ON_RESUME) check()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun authenticate(activation: Boolean) {
        if (!available) { message = "Configura una huella o biometría compatible en Ajustes del teléfono."; return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (activation) { settings.edit().putBoolean(uid, true).apply(); enabled = true }
                unlocked = true; message = ""
            }
            override fun onAuthenticationError(code: Int, error: CharSequence) { message = "No se completó la verificación. Puedes reintentar o ingresar con contraseña." }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Siderúrgica Perú")
            .setSubtitle(if (activation) "Confirmar activación de biometría" else "Desbloquear tu sesión")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancelar").build())
    }
    BackHandler(enabled = !unlocked) { }
    Box(Modifier.fillMaxSize()) {
        // Keep camera launchers and drafts alive behind the lock, but hide them from accessibility.
        Box(Modifier.fillMaxSize().alpha(if (unlocked) 1f else 0f).semantics { if (!unlocked) invisibleToUser() }) {
            content(enabled, available) { value ->
                if (value) authenticate(true)
                else { settings.edit().putBoolean(uid, false).apply(); enabled = false; unlocked = true }
            }
        }
        if (!unlocked) Surface(onClick = {}, modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Tu sesión está protegida", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text("Confirma tu identidad para continuar con la cuenta guardada.")
                if (message.isNotBlank()) Text(message)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { authenticate(false) }, enabled = available, modifier = Modifier.fillMaxWidth()) { Text("Desbloquear con biometría") }
                TextButton(onClick = {
                    settings.edit().putBoolean(uid, false).apply()
                    logout()
                }) { Text("Ingresar con correo y contraseña") }
            }
        }
    }
}
