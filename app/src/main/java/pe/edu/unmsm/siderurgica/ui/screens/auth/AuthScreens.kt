package pe.edu.unmsm.siderurgica.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import pe.edu.unmsm.siderurgica.ui.common.*

@Composable fun LoginScreen(onRegister: () -> Unit, vm: LoginViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    // Credentials are ephemeral; do not persist passwords in saved state or Firestore.
    var password by remember { mutableStateOf("") }
    Page("Bienvenido de nuevo", "Ingresa para continuar con tu operación.") {
        BrandBanner()
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo electrónico") }, singleLine = true, enabled = !state.busy)
        PasswordField(password, { password = it }, enabled = !state.busy)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Notice(state.message)
        Button(onClick = { vm.submit(email, password) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Ingresar") }
        OutlinedButton(onClick = { vm.clear(); onRegister() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
        TextButton(onClick = { vm.resetPassword(email) }, enabled = !state.busy) { Text("Recuperar contraseña") }
        Text("Puedes utilizar los correos ya registrados en este proyecto Firebase.")
    }
}
@Composable fun RegisterScreen(onBack: () -> Unit, vm: RegisterViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Page("Crea tu acceso", "Verifica tu correo después del registro. El administrador asignará tu acceso empresarial.") {
        BrandBanner()
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true, enabled = !state.busy)
        PasswordField(password, { password = it }, enabled = !state.busy)
        PasswordField(confirmation, { confirmation = it }, "Repetir contraseña", !state.busy)
        Notice(state.message)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { vm.submit(email, password, confirmation) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Registrar") }
        TextButton(onClick = onBack, enabled = !state.busy) { Text("Volver al ingreso") }
    }
}
@Composable fun VerificationScreen(state: SessionState, vm: SessionViewModel) {
    Page("Verifica tu correo", "Enviamos el enlace a ${state.account?.email.orEmpty()}.") {
        Text("Abre el enlace de Firebase y vuelve aquí. Si tu cuenta ya existía, también necesita correo verificado para acceder a los datos.")
        Notice(state.message)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = vm::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Ya verifiqué mi correo") }
        OutlinedButton(onClick = vm::resendVerification, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Reenviar enlace") }
        TextButton(onClick = vm::logout) { Text("Cerrar sesión") }
    }
}
