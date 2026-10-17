package pe.edu.unmsm.siderurgica.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.domain.model.AuthAccount
import pe.edu.unmsm.siderurgica.domain.repository.AuthRepository

class FirebaseAuthRepository(private val services: FirebaseServices) : AuthRepository {
    override val accounts: Flow<AuthAccount?> = callbackFlow {
        if (!services.configured) {
            trySend(null)
            close()
        } else {
            val auth = services.auth
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.account()) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }
    }
    override suspend fun login(email: String, password: String) {
        services.auth.signInWithEmailAndPassword(email, password).await()
    }
    override suspend fun register(email: String, password: String) {
        services.auth.createUserWithEmailAndPassword(email, password).await()
        // Verification is a separate recoverable action after account creation.
        // If it fails, the account still exists and the verification screen can retry.
    }
    override suspend fun refreshAccount(): AuthAccount? {
        val user = services.auth.currentUser ?: return null
        user.reload().await()
        val current = services.auth.currentUser ?: return null
        current.getIdToken(true).await()
        return current.account()
    }
    override suspend fun sendVerification() {
        val user = services.auth.currentUser ?: error("Ingresa antes de solicitar la verificación.")
        user.sendEmailVerification().await()
    }
    override suspend fun resetPassword(email: String) { services.auth.sendPasswordResetEmail(email).await() }
    override fun logout() { if (services.configured) services.auth.signOut() }
    private fun FirebaseUser.account() = AuthAccount(uid, email.orEmpty(), isEmailVerified)
}
