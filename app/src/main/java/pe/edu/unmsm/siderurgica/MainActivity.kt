package pe.edu.unmsm.siderurgica

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.edu.unmsm.siderurgica.navigation.AppNavigation
import pe.edu.unmsm.siderurgica.ui.theme.SteelTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SteelTheme { AppNavigation(this@MainActivity) } }
    }
}
