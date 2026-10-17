package pe.edu.unmsm.siderurgica.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.domain.model.AuthAccount
import pe.edu.unmsm.siderurgica.domain.model.UserProfile
import pe.edu.unmsm.siderurgica.domain.repository.AuthRepository
import pe.edu.unmsm.siderurgica.domain.repository.ProfileRepository
import pe.edu.unmsm.siderurgica.domain.rules.InputRules
import pe.edu.unmsm.siderurgica.ui.common.userMessage
import pe.edu.unmsm.siderurgica.data.local.QueueRepository

enum class SessionStage { LOADING, SIGNED_OUT, VERIFY_EMAIL, PROFILE_ERROR, READY }
data class SessionState(
    val stage: SessionStage = SessionStage.LOADING,
    val account: AuthAccount? = null,
    val profile: UserProfile? = null,
    val busy: Boolean = false,
    val message: String = ""
)
class SessionViewModel(private val auth: AuthRepository, private val profiles: ProfileRepository, private val queue: QueueRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SessionState())
    val state = mutable.asStateFlow()
    private var verificationSentFor: String? = null
    private var sessionVersion = 0
    init {
        viewModelScope.launch {
            auth.accounts.collectLatest { account ->
                sessionVersion++
                mutable.value = SessionState(account = account)
                if (account == null) {
                    verificationSentFor = null
                    mutable.value = SessionState(stage = SessionStage.SIGNED_OUT)
                } else if (!account.verified) {
                    mutable.value = SessionState(stage = SessionStage.VERIFY_EMAIL, account = account)
                    if (verificationSentFor != account.uid) {
                        verificationSentFor = account.uid
                        try {
                            auth.sendVerification()
                            if (mutable.value.account?.uid == account.uid) mutable.value = mutable.value.copy(message = "Enviamos un enlace de verificación. Revisa también spam.")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            if (mutable.value.account?.uid == account.uid) mutable.value = mutable.value.copy(message = userMessage(e))
                        }
                    }
                } else loadProfile(account)
            }
        }
    }
    private suspend fun loadProfile(account: AuthAccount) {
        if (mutable.value.account?.uid != account.uid) return
        mutable.value = SessionState(account = account)
        try {
            val profile = profiles.getOrCreate(account)
            if (mutable.value.account?.uid == account.uid) mutable.value = SessionState(SessionStage.READY, account, profile, message = if (profile.cached) "Perfil guardado: los permisos y precios se comprobarán en línea al enviar." else "").also { queue.schedule(account.uid); if (profile.canManageProducts) queue.inventory(account.uid) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (mutable.value.account?.uid == account.uid) mutable.value = SessionState(SessionStage.PROFILE_ERROR, account, message = userMessage(e))
        }
    }
    fun refresh() = action {
        val account = auth.refreshAccount()
        if (account != null && account.uid != mutable.value.account?.uid) return@action
        if (account == null) mutable.value = SessionState(stage = SessionStage.SIGNED_OUT)
        else if (!account.verified) mutable.value = SessionState(SessionStage.VERIFY_EMAIL, account, message = "El correo aún no aparece verificado.")
        else loadProfile(account)
    }
    fun resendVerification() = action {
        auth.sendVerification()
        mutable.value = mutable.value.copy(message = "Enlace reenviado. Revisa tu correo.")
    }
    fun updateName(name: String) = action {
        val account = mutable.value.account ?: error("Inicia sesión.")
        profiles.updateName(account.uid, InputRules.name(name))
        loadProfile(account)
    }
    fun logout() { mutable.value.account?.uid?.let(queue::cancel); sessionVersion++; auth.logout(); mutable.value = SessionState(stage = SessionStage.SIGNED_OUT) }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        val version = sessionVersion
        mutable.value = mutable.value.copy(busy = true, message = "")
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) { if (e is CancellationException) throw e; if (version == sessionVersion) mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { if (version == sessionVersion) mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
