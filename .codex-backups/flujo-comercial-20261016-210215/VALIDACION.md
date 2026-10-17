# Validación — Firebase 0.6.0

## Resultado comprobado

- Android `:app:assembleDebug`: BUILD SUCCESSFUL, JDK 21 y SDK 36.
- Android `:app:testDebugUnitTest`: **23 pruebas, cero fallos y cero errores**.
- Incluye reglas de negocio comerciales, importes y redondeo, RUC/OCR, Retrofit con MockWebServer, persistencia Room entre cierres/reaperturas, aislamiento por usuario, conservación de comandos comerciales y sustitución de notificaciones repetidas con la misma identidad.
- Emuladores Firestore/Storage, proyecto exclusivamente local `demo-siderurgica`: **27 pruebas aprobadas**. Se conservan las 16 anteriores y se agregan cotizaciones multilínea, conversión, descuento de stock al confirmar, concurrencia sin sobreventa, atomicidad, compras y recepción, tokens privados y PDF.
- Emisor Kotlin/JVM `cloud-notifier`: `test installDist` BUILD SUCCESSFUL; **1 prueba aprobada** de umbral, cambio de stock y rearme. Compila con Firebase Admin SDK, pero no se enviaron mensajes reales.
- Configuración Firebase/Auth de la entrega anterior conservada. No se copió ninguna credencial administrativa al APK.
- Proyecto anterior conservado en su carpeta. Los esquemas comerciales nuevos conviven con el histórico; no se ejecutó migración remota.

## Límites de esta verificación

No se publicaron reglas en tu Firebase, no se configuró facturación ni bucket real, no se desplegó Cloud Run/Scheduler, no se consultó Decolecta con token real, no se envió FCM a un teléfono real y no se ejecutó una demo Android con cuentas reales. Las pruebas de permisos y concurrencia utilizan clientes de prueba en emuladores; no sustituyen el recorrido de aceptación en tu teléfono. La visualización/compartición del PDF requiere una app Android compatible. Cámara, OCR, biometría y los envíos WorkManager completos contra Firebase real también necesitan prueba del equipo.

## Reproducir

Desde la raíz del proyecto, con JDK 21:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
gradlew.bat -p cloud-notifier test installDist
npm --prefix rules-tests ci
firebase emulators:exec --only firestore,storage --project demo-siderurgica "npm --prefix rules-tests test"
```

Se usó Firebase CLI 14.19.1 para la comprobación local. En Linux/macOS usa `./gradlew`. No apuntes las pruebas a un proyecto de producción: el entorno de pruebas borra los datos de su emulador entre escenarios.
