package pe.edu.unmsm.siderurgica.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseServices(context: Context) {
    val configured: Boolean = FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    val auth: FirebaseAuth by lazy {
        check(configured) { "Agrega app/google-services.json de tu proyecto Firebase y sincroniza Gradle." }
        FirebaseAuth.getInstance()
    }
    val database: FirebaseFirestore by lazy {
        check(configured) { "Firebase no está configurado." }
        FirebaseFirestore.getInstance()
    }
}
