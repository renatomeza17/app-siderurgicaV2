package pe.edu.unmsm.siderurgica.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pe.edu.unmsm.siderurgica.MainActivity

object AppNotifications {
    fun show(context:Context,key:String,title:String,body:String) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("operaciones","Sincronización e inventario",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try{NotificationManagerCompat.from(context).notify(key.hashCode(),NotificationCompat.Builder(context,"operaciones")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(intent).setAutoCancel(true).build())}catch(_:SecurityException){}
    }
}
