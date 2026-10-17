# Consulta RUC directa desde Android — avance académico

## Configura la credencial una sola vez

1. Abre este proyecto en Android Studio. En el panel cambia a vista **Project** si no ves los archivos raíz.
2. Abre `local.properties` en la raíz, al lado de `settings.gradle.kts`. No es `gradle.properties` ni está dentro de `app`.
3. Ya se dejó una entrada vacía. Completa **solo** esta línea, sin comillas y sin `Bearer`:

```properties
DECOLECTA_TOKEN=PEGA_AQUI_TU_TOKEN_REAL
```

El ejemplo no es una credencial válida. No borres `sdk.dir` ni otras líneas existentes. No pegues la credencial en el chat. No compartas el archivo `local.properties`.

4. Guarda el archivo, sincroniza Gradle (**Sync Project with Gradle Files**) y ejecuta nuevamente la app con **Run**. La credencial se lee al compilar: ponerla después de instalar no actualiza automáticamente el APK.
5. En Proveedores → Registrar proveedor ingresa un RUC válido y pulsa Consultar razón social por RUC → Consultar RUC. También funciona desde Registrar cliente. Revisa los datos devueltos antes de guardarlos.

## Flujo vigente

Compose → ViewModel → RucLookup → Retrofit con función suspend/corutinas → HTTPS Decolecta → datos a revisar → guardado habitual en Firebase.

La app llama a `https://api.decolecta.com/v1/sunat/ruc?numero=...`. Envía la credencial configurada en `Authorization: Bearer ...`; nunca la coloca en la URL. Los trabajadores no ingresan tokens y los mensajes no muestran credenciales ni cuerpos internos de error. La sesión de Firebase sigue siendo la autenticación del ERP, pero no se envía a Decolecta.

Consultar trae razón social, dirección y estado/condición disponibles para **un RUC**. No importa una lista de proveedores ni obtiene sus precios o productos. No guarda un proveedor automáticamente: debes revisarlo y pulsar Guardar. Contacto y teléfono se completan manualmente.

No se necesita Cloud Run, Secret Manager ni el ZIP del servicio para este modo. El código `cloud-ruc` permanece como alternativa opcional; Android ya no lo llama y `RUC_PROXY_BASE_URL` se ignora si quedó en local.properties. El secreto y la cuenta de servicio que creaste pueden conservarse; no se borraron recursos remotos y no se desplegó ningún servicio en esta tarea. La configuración guardada en Secret Manager no se copia automáticamente a Android.

## Límites de esta opción

`local.properties` está excluido de Git, pero la credencial **queda incorporada en el APK y puede extraerse**. Es el compromiso aceptado para esta demostración académica; no es una credencial protegida por estar en BuildConfig. Usa una credencial dedicada de prueba, respeta la cuota del proveedor y revócala o cámbiala si compartes el APK fuera del equipo. No se publicará este APK como solución con credencial compartida protegida.

La consulta requiere internet y consume las llamadas del plan Decolecta. Los límites y la caché del componente opcional Cloud Run **no se aplican** al modo directo; no se añadió una caché de RUC ni un límite local en este cambio. Consulta solo cuando sea necesario. Sin internet o sin credencial, puedes continuar el registro manual y sus borradores SQLite/WorkManager existentes.

Sin token: la app indica que la consulta aún no está configurada. Credencial rechazada: indica revisar configuración con el administrador; el trabajador no tiene que pegarla en pantalla. Límite del proveedor: permite continuar manualmente.

## Archivos

- `app/build.gradle.kts`: lee `DECOLECTA_TOKEN` de `local.properties` y genera configuración de compilación.
- `di/AppModule.kt`: conexión HTTPS directa y credencial interna.
- `data/remote/RucApi.kt`: contrato Retrofit, validación del RUC, llamada suspend y validación de la respuesta.
- `SupplierViewModel.kt` / `BusinessViewModel.kt`: lanzan consultas dentro de viewModelScope.
- Pantallas de proveedores/clientes: solo solicitan RUC; no cambió el flujo de guardado en Firestore.

No se realizó una consulta real porque no se proporcionó ni configuró una credencial. Las pruebas usan un servidor simulado.
