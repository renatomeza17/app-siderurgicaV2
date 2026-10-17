# Emisor de alertas FCM — Kotlin/JVM

FCM necesita un entorno confiable para enviar mensajes. Este componente pequeño se ejecuta en Cloud Run del mismo proyecto Google/Firebase; no depende del ERP Render, Neon o Spring Boot. Android sigue comunicándose directamente con Authentication, Firestore y Storage. No es un endpoint de negocio que deba consumir Retrofit.

Android registra el token privado del dispositivo en `usuarios/{uid}/dispositivos/{installationId}`. Un servicio privado revisa productos, umbrales y roles ADMIN/ALMACEN activos usando Firebase Admin SDK. Envía mensajes de datos dirigidos por token; no utiliza topics públicos. El contenido visible no incluye datos empresariales. Los clientes no pueden leer tokens de otras cuentas ni escribir los estados de envío.

## Preparación y despliegue (no ejecutados por Codex)

Requiere proyecto con facturación habilitada, gcloud configurado, Docker y permisos de administración apropiados. Revisa el coste de Cloud Run/Scheduler/Artifact Registry. No publiques el servicio con acceso anónimo.

1. Compila desde la carpeta del proyecto Android: `./gradlew -p cloud-notifier test installDist` (Windows: `gradlew.bat`). JDK 21.
2. Crea dos cuentas de servicio: una de ejecución del emisor y otra de invocación del Scheduler. No descargues claves JSON.
3. A la cuenta de ejecución asigna `roles/datastore.user` y `roles/firebasecloudmessaging.admin` en este proyecto. Es un componente administrativo: nunca incluyas su SDK o credenciales en el APK.
4. Habilita las APIs Cloud Run, Cloud Scheduler, Artifact Registry y Firebase Cloud Messaging HTTP v1.
5. Crea un repositorio Docker en Artifact Registry y autentica Docker. Construye este Dockerfile y sube la imagen a tu repositorio. La carpeta `build/install` debe haberse generado en el paso 1.
6. Despliega la imagen con `gcloud run deploy siderurgica-stock-alerts --image IMAGEN --region REGION --service-account CUENTA_EJECUCION --set-env-vars GOOGLE_CLOUD_PROJECT=TU_PROJECT_ID --no-allow-unauthenticated --max-instances 1 --concurrency 1 --timeout 540`.
7. Concede `roles/run.invoker` sobre ese servicio solo a la cuenta de invocación. Crea un job Cloud Scheduler `*/15 * * * *`, zona `America/Lima`, POST a `URL_SERVICIO/review`, autenticación OIDC con esa cuenta y audiencia `URL_SERVICIO`. Configura al menos un reintento y deadline de 540 segundos.
8. Abre la app como ADMIN/ALMACEN, habilita notificaciones, activa la revisión y espera el registro del token. Ejecuta el job manualmente y verifica una alerta con stock bajo y otra después de reponer/bajar nuevamente. Prueba también con la app en segundo plano y cerrando sesión.

Se envía una alerta por cambio de stock/mínimo mientras esté bajo; volver a normal rearma el aviso. Una concesión de cinco minutos evita ejecuciones simultáneas del mismo producto. Los fallos se reintentan; FCM no garantiza entrega exactamente una vez y Android agrupa por producto. La revisión local WorkManager también muestra aviso de stock; ese aviso local NO se presenta como una entrega FCM.

El emisor usa credenciales del entorno (ADC). Cloud Run valida OIDC antes de entregar peticiones a la aplicación. No ejecutes este contenedor expuesto a internet sin IAM. Los productos se procesan por páginas de 100. Para inventarios muy grandes, divide el trabajo para mantener cada ejecución dentro del timeout.

Documentación: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
https://cloud.google.com/run/docs/triggering/using-scheduler
