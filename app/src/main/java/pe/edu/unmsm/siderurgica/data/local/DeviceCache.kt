package pe.edu.unmsm.siderurgica.data.local

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DeviceCache(private val context: android.content.Context, private val dao: LocalDao) {
    val online: Boolean get() {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    suspend fun save(owner: String, kind: String, value: Any) { dao.cache(CacheEntry(owner, kind, Gson().toJson(value), System.currentTimeMillis())) }
    suspend fun <T> read(owner: String, kind: String, type: java.lang.reflect.Type): T? = dao.cache(owner, kind)?.let { Gson().fromJson(it.json, type) }
}
fun networkFailure(e: Exception) = e is FirebaseNetworkException || e is java.io.IOException ||
    (e is com.google.firebase.storage.StorageException && e.errorCode in setOf(
        com.google.firebase.storage.StorageException.ERROR_RETRY_LIMIT_EXCEEDED,
        com.google.firebase.storage.StorageException.ERROR_UNKNOWN)) ||
    (e is FirebaseFirestoreException && e.code in setOf(FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED, FirebaseFirestoreException.Code.ABORTED))
