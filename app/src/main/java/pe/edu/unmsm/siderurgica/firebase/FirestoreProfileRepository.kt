package pe.edu.unmsm.siderurgica.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import pe.edu.unmsm.siderurgica.domain.model.AuthAccount
import pe.edu.unmsm.siderurgica.domain.model.Role
import pe.edu.unmsm.siderurgica.domain.model.UserProfile
import pe.edu.unmsm.siderurgica.domain.repository.ProfileRepository
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import pe.edu.unmsm.siderurgica.data.local.*

class FirestoreProfileRepository(private val services: FirebaseServices, private val cache: DeviceCache) : ProfileRepository {
    override suspend fun getOrCreate(account: AuthAccount): UserProfile {
        require(account.verified) { "Verifica tu correo antes de cargar el perfil." }
        if (!cache.online) return cache.read<UserProfile>(account.uid, "profile", UserProfile::class.java)?.copy(cached = true)
            ?: error("Sin conexión y sin perfil guardado. Ingresa una vez con internet.")
        return try { loadRemote(account) } catch (e: Exception) {
            if (e is CancellationException || !networkFailure(e)) throw e
            cache.read<UserProfile>(account.uid, "profile", UserProfile::class.java)?.copy(cached = true) ?: throw e
        }
    }
    private suspend fun loadRemote(account: AuthAccount): UserProfile {
        val ref = services.database.collection("usuarios").document(account.uid)
        services.database.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (!snapshot.exists()) tx.set(ref, mapOf(
                "uid" to account.uid, "email" to account.email,
                "nombre" to account.email.substringBefore('@').take(80),
                "rol" to "PENDIENTE", "activo" to true,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
        // Server source prevents cached roles from being treated as current authorization.
        val profile = ref.get(Source.SERVER).await()
        val role = Role.entries.firstOrNull { it.name == profile.getString("rol") }
            ?: error("El perfil tiene un rol inválido. Solicita revisión al administrador.")
        require(profile.getString("uid") == account.uid) { "El perfil no corresponde a la cuenta." }
        return UserProfile(account.uid, account.email, profile.getString("nombre").orEmpty(), role,
            profile.getBoolean("activo") == true).also { cache.save(account.uid, "profile", it) }
    }
    override suspend fun updateName(uid: String, name: String) {
        services.database.collection("usuarios").document(uid).update(mapOf(
            "nombre" to name, "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }
}
