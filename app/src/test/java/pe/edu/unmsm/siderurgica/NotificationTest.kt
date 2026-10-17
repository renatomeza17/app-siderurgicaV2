package pe.edu.unmsm.siderurgica

import android.app.Application
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pe.edu.unmsm.siderurgica.notifications.AppNotifications

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class NotificationTest {
    @Test fun retryUpdatesSameNotificationInsteadOfCreatingDuplicates() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        AppNotifications.show(context, "operation-one", "Error de sincronización", "Revisa la operación")
        AppNotifications.show(context, "operation-one", "Sincronización completada", "Confirmado")
        val notifications = manager.activeNotifications
        assertEquals(1, notifications.size)
        assertEquals("Sincronización completada", notifications.single().notification.extras.getString("android.title"))
    }
}
