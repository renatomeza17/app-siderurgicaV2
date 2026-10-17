> DOCUMENTO HISTÓRICO. Para la entrega 0.6.0 usa GUIA_SPRINTS_1_2_3.md y README.md. Sus indicaciones y alcance reemplazan esta referencia anterior.

# Primer avance ampliado — Firebase 0.5.0

Esta entrega amplía el proyecto de pedidos/ventas con las funcionalidades solicitadas para el avance inicial. No pretende completar el proyecto final. Mantiene el mismo paquete Android, proyecto Firebase, cuentas y roles. No utiliza el ERP Render/Railway ni consulta Neon.

## Abrir el proyecto

1. Guarda el proyecto anterior como respaldo y extrae este ZIP en una carpeta nueva.
2. Abre la carpeta `SiderurgicaFirebase` que contiene `settings.gradle.kts` en Android Studio.
3. En Settings → Build, Execution, Deployment → Build Tools → Gradle selecciona un **JDK 21**. Si no lo tienes, usa Download JDK desde Android Studio. El archivo `gradle/gradle-daemon-jvm.properties` solicita 21. La herramienta de pruebas SQLite utilizada no es compatible con el Java 25 de la configuración anterior; por eso se ajustó este punto. El bytecode de la app sigue dirigido a Java 17 y minSdk 26.
4. Usa SDK 36 y sincroniza Gradle. Conservamos el `google-services.json` de tu proyecto actual, sin credenciales de administrador.
5. Revisa/publica el **nuevo firestore.rules**. No borres perfiles, productos, cotizaciones, pedidos ni ventas. Las reglas mantienen esos procesos y agregan proveedores y documentos.

## Borradores y sincronización

Room administra una base SQLite privada del dispositivo llamada `siderurgica-firebase-local.db`. Conserva operaciones, caché por cuenta y documentos capturados.

- **Guardar solo borrador:** estado BORRADOR; no se envía automáticamente.
- **Guardar y sincronizar:** estado PENDIENTE; WorkManager espera conexión para intentar el envío.
- **ENVIADO:** Firebase confirmó la operación y se guardó ese resultado localmente.
- **ERROR:** la operación necesita revisión. Resolver la causa y pulsar Enviar o reintentar conserva el mismo ID.

Desde Más → Borradores y sincronización puedes consultar datos resumidos, enviar borradores, reintentar errores y limpiar registros locales terminados. No se permite eliminar operaciones PENDIENTES desde esta pantalla. Borrar un registro ENVIADO no elimina el dato remoto.

El worker trabaja solo con la cuenta Firebase que corresponde a cada fila. Cerrar sesión cancela su trabajo; los registros permanecen privados por UID y se reprograman cuando vuelve a ingresar esa cuenta. No se mezclan operaciones de cuentas distintas. Los reintentos de red tienen backoff y, después de ocho intentos fallidos, requieren reintento manual. No se garantiza ejecución inmediata cuando vuelve internet: Android administra la programación.

La fila sigue PENDIENTE mientras se procesa: si el proceso termina, puede reintentarse. Cotizaciones utilizan el ID persistido de la fila; proveedores utilizan RUC único más operationId; imágenes utilizan una ruta por documento. Una respuesta perdida no crea otro identificador en el reintento. Las validaciones remotas y estados de la versión comercial anterior se conservan.

Los borradores de cotización guardan el precio esperado. Si cambia el precio remoto antes del envío, queda ERROR y debes consultar el producto y preparar una propuesta nueva; no se cambia el importe silenciosamente. Pedidos/aprobaciones/ventas siguen requiriendo confirmación online y no se encolan en esta etapa.

### Demo offline

1. Ingresa con internet, carga el perfil y consulta productos/cotizaciones. Eso crea la caché SQLite de esa cuenta.
2. Desactiva la red. Puedes preparar una cotización de un producto previamente cargado o un proveedor manual.
3. Guarda solo borrador o elige sincronizar y revisa su estado en Más.
4. Cierra/reabre la app. Si hay perfil previamente guardado, se muestra la última configuración y se informa que los permisos se comprobarán en línea. Una cuenta nueva sin perfil local necesita ingresar inicialmente con internet.
5. Recupera conexión, envía el borrador si corresponde y espera/solicita sincronización. Verifica ENVIADO y actualiza el listado remoto.

El inicio puede mostrar métricas de datos guardados, no ventas nuevas confirmadas. Los permisos definitivos los comprueban las reglas Firebase en cada envío. No se usa caché para ocultar errores de autorización.

## Proveedores y API externa

ADMIN/ALMACEN pueden registrar y consultar proveedores en `proveedores/{RUC}`. Se valida RUC en Kotlin y se protege su identidad/forma en reglas. No se permite crear dos documentos con el mismo RUC. Edición/eliminación de proveedores no está en esta etapa.

El registro manual funciona sin API. La consulta opcional por RUC utiliza **Decolecta con Retrofit y corrutinas** para proponer razón social y dirección. El usuario revisa los campos antes de guardar; la respuesta no se registra automáticamente.

1. Obtén una cuenta/token propio en el portal del proveedor, revisando sus términos y límites vigentes.
2. En Proveedores → Registrar proveedor → Consultar razón social por API, introduce RUC y token personal.
3. Consulta y revisa la respuesta. El token se conserva únicamente en memoria de esa pantalla; no está incluido en el APK, no se guarda en Room/Firestore y no se envía como parte del proveedor. No pegues tokens en chats ni archivos compartidos.

No se incluyó un token ni se consultó la API real con credenciales del equipo. Se probaron petición, encabezado y mapeo con MockWebServer. Para producción no distribuyas una credencial compartida de la organización en Android: esta entrada personal es una configuración de demostración; un secreto corporativo requeriría una solución confiable de servidor.

Endpoint documentado: `GET https://api.decolecta.com/v1/sunat/ruc?numero=...`, encabezado `Authorization: Bearer ...`. Es un proveedor externo que consulta datos de RUC; no es una integración fiscal directa ni emite comprobantes. La API previa de proveedores Render no se mantiene como dependencia. Una importación inicial desde Render sería un trabajo independiente.

Documentación oficial del proveedor: https://decolecta.gitbook.io/docs/servicios/integrations

## Cámara, OCR y códigos

ADMIN/ALMACEN tienen Más → Documentos y códigos. Cámara usa la aplicación de captura del dispositivo y FileProvider; no hace falta solicitar permiso de cámara para este contrato externo. Si no hay cámara, se puede elegir una imagen de galería.

La imagen se decodifica con muestreo, se corrige orientación EXIF, ML Kit extrae texto y códigos QR/barras y se guarda JPEG comprimido. El parser propone RUC, número, fecha y monto. La vista permite revisar la imagen, corregir campos y confirmar factura local. Una lectura de código puede guardarse sin fingir datos de factura.

Guardar conserva imagen y campos en el dispositivo. **Enviar imagen a Firebase** es una confirmación separada: añade la operación a WorkManager, sube el JPEG y registra metadatos remotos bajo `documentos/{uid}/items/{id}`. La ruta de Storage es `usuarios/{uid}/documentos/{id}.jpg`. Solo el propietario con rol autorizado puede acceder. No se generan enlaces públicos.

Los documentos de este avance se listan desde la base local del dispositivo; aunque la copia remota permanezca, no se ha implementado todavía su descarga/listado en un dispositivo nuevo. Desinstalar la app elimina los documentos solo locales. PDF, edición posterior y asociación automática a una compra quedan pendientes.

## Activar Storage (opcional)

La captura, OCR y guardado local funcionan sin Storage. Para subir imágenes:

1. En Firebase Console → Storage configura el bucket del proyecto. Firebase exige actualmente **plan Blaze** para Cloud Storage; revisa facturación y límites antes de activarlo. Codex no activó facturación ni creó recursos.
2. Descarga de nuevo el `google-services.json` si al crear el bucket cambió la configuración de Storage y reemplázalo en `app/`. Conserva el mismo paquete/proyecto.
3. Revisa/publica `storage.rules`, además del nuevo `firestore.rules`. Storage consulta el perfil Firestore; si la consola solicita habilitar los permisos entre servicios, revisa y acepta únicamente los correspondientes a tu proyecto.
4. No reemplaces reglas de otras aplicaciones del mismo proyecto sin integrarlas. Las reglas incluidas habilitan solo las imágenes documentales de esta etapa, no PDF de ventas.
5. Si una subida quedó ERROR antes de configurar, revisa su causa y utiliza Reintentar en Borradores.

Requisito oficial: https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024

## Huella/biometría

Configura una huella o biometría fuerte compatible en Ajustes del teléfono. En la app, Más → Seguridad del dispositivo → Activar biometría. Se solicita autenticación del sistema antes de guardar la preferencia para esa cuenta.

Al reabrir o volver desde segundo plano se bloquea la vista hasta confirmar biometría. Al volver de la cámara también se puede solicitar desbloqueo, conservando el resultado de captura detrás del bloqueo. Si el sensor no está disponible o prefieres contraseña, el botón de ingreso por correo cierra la sesión y desactiva esa preferencia; se debe autenticar de nuevo con Firebase.

Es protección local de una sesión existente, no registro de huellas en Firebase ni sustitución de correo/contraseña. WorkManager puede completar envíos previamente autorizados mientras la pantalla esté bloqueada. La preferencia y el sensor deben probarse en un dispositivo compatible; no se ejecutó una prueba física de huella aquí.

## Qué dejamos fuera del primer avance

Compras/presupuesto/recepción completos, movimientos manuales de stock, PDF de ventas, despacho/GPS, RRHH/voz, dashboard avanzado y las otras funcionalidades IA, más publicación Play Store. No se presentan como terminados. Este proyecto ya tiene cámara y biometría; el tercer recurso y las otras funcionalidades IA corresponden a las siguientes entregas.

Ver VALIDACION.md para las pruebas ejecutadas. El recorrido de clase debe presentar el avance real y distinguir lo configurado del código preparado.
