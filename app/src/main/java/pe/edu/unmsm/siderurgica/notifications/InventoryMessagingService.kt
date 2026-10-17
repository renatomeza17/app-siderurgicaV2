package pe.edu.unmsm.siderurgica.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.work.*
import pe.edu.unmsm.siderurgica.worker.InventoryReviewWorker

class InventoryMessagingService:FirebaseMessagingService() {
    override fun onMessageReceived(message:RemoteMessage){
        val owner=FirebaseAuth.getInstance().currentUser?.uid?:return
        if(message.data["owner"]!=owner)return
        // Do not show payload after logout, or after the current local role was revoked.
        val prefs=getSharedPreferences("inventory-notifications",MODE_PRIVATE)
        if(prefs.getString("authorizedOwner",null)!=owner)return
        AppNotifications.show(this,"stock-${message.data["productId"]}","Alerta de inventario","Hay un producto con stock bajo. Abre Inventario para consultar los datos vigentes.")
    }
    override fun onNewToken(token:String){
        val owner=FirebaseAuth.getInstance().currentUser?.uid?:return
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<InventoryReviewWorker>()
            .setInputData(workDataOf("owner" to owner)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
