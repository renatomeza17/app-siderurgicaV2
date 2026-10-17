package pe.edu.unmsm.siderurgica.domain.repository

import pe.edu.unmsm.siderurgica.domain.model.AuthAccount
import pe.edu.unmsm.siderurgica.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getOrCreate(account: AuthAccount): UserProfile
    suspend fun updateName(uid: String, name: String)
}
