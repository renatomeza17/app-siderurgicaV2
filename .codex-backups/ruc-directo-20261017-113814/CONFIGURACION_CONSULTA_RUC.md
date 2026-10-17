# Consulta RUC interna — proveedores y clientes

## Qué cambió

El trabajador escribe el RUC y pulsa **Consultar RUC**. Recibe razón social, dirección y estado/condición disponibles. Revisa los datos y guarda el proveedor o cliente. Consultar no registra automáticamente en Firestore ni importa una lista de proveedores. Contacto y teléfono siguen siendo datos que registra el trabajador.

No se pide token en ninguna de las pantallas de proveedores o clientes. Tampoco se incorpora el token de Decolecta a BuildConfig, recursos, Firestore legible, Remote Config ni APK. Android utiliza el ID token de su propia sesión Firebase automáticamente; esa credencial identifica al usuario y no es la credencial de Decolecta.

Flujo: Compose → ViewModel → RucLookup → Retrofit + corutinas → servicio Kotlin Cloud Run → validación Firebase Authentication y rol → Decolecta → datos a revisar → guardado del proveedor/cliente con la cola habitual SQLite/WorkManager.

**Estado de la entrega:** fuentes preparadas y probadas localmente. Servicio no desplegado, credencial no configurada y consulta real no ejecutada. Mientras falte configurar, la app muestra que la consulta aún no está habilitada y conserva el registro manual. No se modificó el proyecto Firebase remoto, sus reglas ni sus datos.

## Lo que tú necesitas hacer

1. Regístrate en [Decolecta](https://decolecta.com/profile) y obtén una credencial con acceso a consulta RUC. Confirma en tu panel la cuota y condiciones de tu cuenta. No pegues la credencial en el chat ni en el código.
2. En Google Cloud Console selecciona **el mismo proyecto de Firebase**. Habilita Cloud Run, Cloud Build, Artifact Registry y Secret Manager cuando corresponda. No se necesita Render ni Spring Boot.
3. En Secret Manager crea el secreto `decolecta-token`, con la credencial como valor. Se configura una vez por proyecto; no por trabajador. El código permite el valor sin el prefijo Bearer o con él.
4. Crea una cuenta de servicio dedicada, por ejemplo `siderurgica-ruc-run`. Necesita `roles/datastore.user` para perfiles, caché y contadores; `roles/firebaseauth.viewer` para verificar usuarios/revocación; y `roles/secretmanager.secretAccessor` **sobre ese secreto**. La cuenta usa identidad administrada: no descargues ni agregues un JSON de clave al proyecto.
5. Compila y despliega el pequeño servicio `cloud-ruc`. Abajo están los comandos de referencia. No afecta el servicio privado `cloud-notifier` de notificaciones.
6. Copia la URL HTTPS que devuelve Cloud Run. En `local.properties` de Android agrega **solo la URL pública**, por ejemplo:

```properties
RUC_PROXY_BASE_URL=https://URL-REAL-DEL-SERVICIO.run.app
```

No copies esa URL de ejemplo literalmente. No agregues `/ruc` a la base. La URL no es secreta; el acceso lo controla Firebase. No pongas aquí `DECOLECTA_TOKEN`.

7. Sincroniza Gradle, recompila e instala la app. Ya no habrá que ingresar credenciales de Decolecta desde el teléfono.

## Despliegue de referencia con Google Cloud CLI

Ejecuta desde la raíz de este proyecto, tras iniciar sesión en Google Cloud CLI con tu propia cuenta. Sustituye `TU_PROJECT_ID`, `TU_CUENTA_SERVICIO` y región si corresponde. No se ejecutaron estos comandos durante la entrega.

```powershell
$rucProject = 'TU_PROJECT_ID'
$rucRegion = 'us-central1'
$rucRuntime = "siderurgica-ruc-run@$rucProject.iam.gserviceaccount.com"
$rucImage = "$rucRegion-docker.pkg.dev/$rucProject/siderurgica/ruc:1"
$env:JAVA_HOME = 'C:/Users/Usuario/.jdks/jbr-21.0.11'
.\gradlew.bat -p cloud-ruc test installDist

# Crear el repositorio una sola vez si no existe.
gcloud artifacts repositories create siderurgica --repository-format=docker --location=$rucRegion --project=$rucProject
gcloud builds submit cloud-ruc --tag=$rucImage --project=$rucProject
gcloud run deploy siderurgica-ruc --image=$rucImage --project=$rucProject --region=$rucRegion --service-account=$rucRuntime --allow-unauthenticated --min-instances=0 --max-instances=1 --concurrency=8 --timeout=45 --set-env-vars="GOOGLE_CLOUD_PROJECT=$rucProject,RUC_DAILY_LIMIT=20" --set-secrets="DECOLECTA_TOKEN=decolecta-token:latest"
```

La cuenta que construye/despliega también necesita permisos de Cloud Build, Artifact Registry, Cloud Run y uso de la cuenta de servicio según las políticas del proyecto. Los permisos de despliegue son distintos a los del servicio en ejecución.

**Importante sobre `--allow-unauthenticated`:** permite que Android llegue al endpoint HTTP sin una credencial IAM de Google Cloud. No permite consultar Decolecta anónimamente: el código exige un ID token válido de **tu Firebase**, email verificado, perfil activo y rol ADMIN, ALMACEN o VENTAS antes de consultar. No elimines esa validación. No reutilices esta opción para el servicio privado de notificaciones.

## Límites y configuración interna

- La credencial se inyecta al servicio como `DECOLECTA_TOKEN` desde Secret Manager. Nunca se devuelve al cliente ni se imprimen errores crudos de Decolecta.
- Por defecto permite **20 intentos externos al día por proyecto** (día UTC), configurables con `RUC_DAILY_LIMIT`. Se cuentan intentos reservados, incluidos fallos, para mantener el límite conservador.
- Limita a 5 consultas por minuto y usuario con contadores compartidos en Firestore. Las instancias no llevan contadores independientes.
- Reutiliza datos durante 24 horas; consultar repetidamente un mismo RUC reciente no consume una llamada externa nueva. Una consulta simultánea del mismo RUC podría reservar más de una llamada; siempre respeta el límite global.
- `_ruc_cache` y `_ruc_limits` son colecciones internas. Las reglas actuales las bloquean para los clientes; Admin SDK las accede con su cuenta de servicio. No abras estas colecciones al público. Contadores diarios y caché no tienen limpieza automática en esta entrega.
- Estos límites controlan llamadas de este componente; no son un límite de facturación de Google Cloud ni incluyen otros usos de tu credencial Decolecta.
- La consulta requiere internet. El guardado posterior del proveedor/cliente conserva sus borradores y sincronización existentes. Consultar no programa tareas offline ni guarda credenciales en SQLite.

## Cómo probar después de activar

1. Inicia sesión como ADMIN o ALMACEN con correo verificado. Abre Proveedores → Registrar proveedor.
2. Escribe un RUC válido y pulsa Consultar razón social por RUC → Consultar RUC. Verifica razón social/dirección y luego guarda. Nunca se solicita una credencial.
3. Prueba también Registrar cliente desde cotizaciones. ADMIN y VENTAS pueden consultar y guardar clientes; ALMACEN no obtiene permisos nuevos de ventas por esta consulta.
4. Consulta un RUC con dígito incorrecto: debe rechazarse sin llamar al servicio.
5. Sin conexión, completa manualmente los datos y guarda borrador; una consulta por API no puede hacerse sin red.
6. Si alcanzas límite, aparece un mensaje comprensible y puedes registrar manualmente. Ante una sesión vencida se indica volver a ingresar, sin pedir tokens.
7. Hasta configurar la URL aparece “La consulta RUC aún no está habilitada”. Esto es configuración pendiente, no una respuesta real de la API.

## Archivos del recorrido

- `ui/screens/suppliers/SupplierScreen.kt` y `SupplierViewModel.kt`: interfaz y consulta de proveedores sin campo de token.
- `ui/screens/business/CommercialScreen.kt`, `BusinessScreen.kt`, `BusinessViewModel.kt`: consulta equivalente para clientes.
- `data/remote/RucApi.kt`: contrato Retrofit hacia `/ruc`, corutinas y validación del resultado.
- `di/AppModule.kt`: URL de configuración y obtención automática de sesión Firebase.
- `app/build.gradle.kts`: lee solamente `RUC_PROXY_BASE_URL`, no la credencial externa.
- `cloud-ruc/src/main/kotlin/RucServer.kt`: validación Firebase, perfiles, caché y límites.
- `cloud-ruc/src/main/kotlin/RucService.kt`: permisos, validación RUC y llamada al proveedor externo.

## Referencias oficiales

- [Autenticación de Decolecta](https://decolecta.gitbook.io/docs).
- [Consulta SUNAT RUC de Decolecta](https://decolecta.gitbook.io/docs/servicios/integrations).
- [Verificación de ID tokens Firebase](https://firebase.google.com/docs/auth/admin/verify-id-tokens).
- [Secret Manager con Cloud Run](https://docs.cloud.google.com/run/docs/configuring/services/secrets).
