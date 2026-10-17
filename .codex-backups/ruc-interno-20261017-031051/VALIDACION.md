# Verificación de compras HU08 y conexión con inventario HU07 — 17/10/2026

- Android: **31 pruebas aprobadas**, sin fallos; compilación y APK debug correctos con JDK 21. Incluyen presupuesto, metadatos, PDF solo para orden aprobada y persistencia de los comandos nuevos al reabrir SQLite.
- Firestore/Storage: **34 pruebas aprobadas** en emuladores locales. Incluyen recepción de tres productos, autorización y reserva atómicas, bloqueo por capital insuficiente, roles, cancelación, inmutabilidad documental, reintentos y aprobaciones simultáneas sin sobrecomprometer presupuesto. También pasan las regresiones comerciales existentes.
- Evidencia: `verificacion-compras.log`, `verificacion-reglas-compras.log` y `app/build/test-results/testDebugUnitTest`.
- No se ejecutó una prueba visual en teléfono, ni se compartió un PDF con aplicaciones externas. No se desplegaron reglas ni se escribieron datos en el proyecto Firebase real.
- Debes publicar el nuevo `firestore.rules` antes de probar. `storage.rules` no cambió. No se cambiaron credenciales ni google-services.json.
- Se actualizó únicamente este proyecto. Respaldo de fuentes anteriores en `.codex-backups/compras-*`; las pruebas de reglas de compras se reemplazaron por el recorrido nuevo. No se creó ZIP.
- Alcance: recepción completa con referencia documental textual; adjuntos automáticos, parciales, pagos, préstamos, fabricación y despliegue FCM quedan fuera de esta actualización. Las pendientes antiguas sin período requieren recreación; ver guía.

Guías: `GUIA_HU08_COMPRAS_Y_PRUEBAS.md` y `GUION_HU08_FLUJO_COMPLETO.md`.

---

## Historial de verificaciones anteriores

# Verificación de la actualización del flujo comercial

- Compilación Android y generación del APK debug: correctas con JDK 21.
- **28 pruebas Android/SQLite aprobadas**, sin fallos: incluyen límites de descuentos, rechazo de descuentos manuales, sustento/canal de decisiones, compatibilidad de comandos antiguos y contenido del PDF de cotización sin recalcular importes históricos.
- **31 pruebas de Firestore/Storage en emuladores aprobadas**, sin fallos: incluyen aceptación con operador/fecha/canal/observación, descuentos nuevos obligatorios, conversión de cotizaciones anteriores y recorrido de tres productos desde cotización hasta venta.
- Las reglas se optimizaron para que el recorrido de tres productos respete el límite de expresiones de Firestore, manteniendo las validaciones.
- No se desplegaron reglas remotas ni se ejecutaron operaciones contra tu Firebase real. No se cambió google-services.json, Authentication, el bucket ni las credenciales.
- No se ejecutó una prueba visual en tu teléfono ni envío de PDF por WhatsApp/correo. Esa aceptación queda para la prueba del equipo.
- No se creó ZIP nuevo ni otra copia de la aplicación: las fuentes se actualizaron en esta carpeta. El respaldo de archivos anteriores está en `.codex-backups`.

**Antes de probar:** publica el `firestore.rules` de esta carpeta en tu proyecto Firebase y ejecuta la app desde Android Studio. No necesitas cambiar `storage.rules` para este ajuste. Revisa `CAMBIOS_FLUJO_COMERCIAL.md`.

---

## Referencia de la entrega anterior

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
