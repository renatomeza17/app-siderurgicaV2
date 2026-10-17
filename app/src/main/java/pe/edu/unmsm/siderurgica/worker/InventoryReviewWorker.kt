package pe.edu.unmsm.siderurgica.worker

import android.content.Context
import androidx.work.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.koin.core.context.GlobalContext
import pe.edu.unmsm.siderurgica.data.local.*
import pe.edu.unmsm.siderurgica.firebase.FirebaseServices
import pe.edu.unmsm.siderurgica.domain.repository.CommerceRepository
import pe.edu.unmsm.siderurgica.notifications.AppNotifications
import java.util.UUID
import java.util.concurrent.TimeUnit

class InventoryReviewWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val koin=GlobalContext.get();val services=koin.get<FirebaseServices>()
        val owner=inputData.getString("owner")?:return Result.failure()
        if(!services.configured||services.auth.currentUser?.uid!=owner)return Result.success()
        val prefs=applicationContext.getSharedPreferences("inventory-notifications",Context.MODE_PRIVATE)
        return try{
            val profile=services.database.collection("usuarios").document(owner).get(Source.SERVER).await()
            if(profile.getBoolean("activo")!=true || profile.getString("rol") !in listOf("ADMIN","ALMACEN")){
                prefs.edit().remove("authorizedOwner").apply();return Result.success()
            }
            if(services.auth.currentUser?.uid!=owner)return Result.success()
            prefs.edit().putString("authorizedOwner",owner).apply()
            val token=FirebaseMessaging.getInstance().token.await()
            val installation=prefs.getString("installation",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("installation",it).apply()}
            services.database.collection("usuarios").document(owner).collection("dispositivos").document(installation)
                .set(mapOf("token" to token,"updatedAt" to FieldValue.serverTimestamp())).await()
            val products=koin.get<CommerceRepository>().products()
            if(services.auth.currentUser?.uid!=owner)return Result.success()
            val critical=products.filter{it.stock<=it.minimumStock}
            val signature=critical.sortedBy{it.id}.joinToString("|"){"${it.id}:${it.stock}"}
            val key="last-$owner"
            if(critical.isNotEmpty() && signature!=prefs.getString(key,null)) AppNotifications.show(applicationContext,"inventory-$owner","Stock bajo","${critical.size} productos requieren revisión. Consulta Inventario.")
            prefs.edit().putString(key,signature).apply()
            if(koin.get<LocalDao>().ready(owner).isNotEmpty())koin.get<QueueRepository>().schedule(owner)
            Result.success()
        }catch(e:Exception){if(e is CancellationException)throw e;if(networkFailure(e)&&runAttemptCount<7)Result.retry()else Result.failure()}
    }
    companion object{
        fun schedule(context:Context,owner:String){
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("inventory-$owner",ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<InventoryReviewWorker>(15,TimeUnit.MINUTES).setInputData(workDataOf("owner" to owner))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
        }
    }
}
