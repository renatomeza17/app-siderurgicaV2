package pe.edu.unmsm.siderurgica

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pe.edu.unmsm.siderurgica.di.appModule

class SiderurgicaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@SiderurgicaApp); modules(appModule) }
    }
}
