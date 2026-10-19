# Guía de exposición completa: HU08 e intervención documental de HU09

Fuentes revisadas el **18/10/2026** en el proyecto del Escritorio. Esta guía describe la versión actual; no el backend Spring Boot anterior ni el servicio Cloud Run opcional. No se incluyen credenciales.

## Cómo estudiar esta guía

Léela en orden y reproduce las acciones de la demo. Cada sección parte de una acción del trabajador, muestra la llamada que se ejecuta y salta al componente invocado. Las narraciones entre comillas están en primera persona para poder leerlas durante la exposición. Los enlaces abren el archivo real en la línea verificada. Los fragmentos no son archivos independientes para compilar.

El alcance demostrado es: **proveedor registrado → orden creada → aprobación con presupuesto → recepción física → actualización de inventario → respaldo fotográfico vinculado**. El proveedor no opera en nuestra aplicación; la negociación y el envío del PDF son externos. Finalizar aquí significa compra recibida y stock actualizado, no proveedor pagado.

| Historia | Intervención en este recorrido |
|---|---|
| HU08 | Proveedores, precios acordados, orden, presupuesto, aprobación, recepción e inventario |
| HU09, integración parcial | Cámara/imagen, reconocimiento local del documento, sugerencia de número y respaldo JPEG en Storage |
| HU03, soporte reutilizado | Room/SQLite, cola, WorkManager y reintentos por usuario |
| HU07, resultado conectado | Consulta de existencias y movimientos generados por la compra |

No afirmar que HU09 entera queda demostrada: la compra no consume todas las extracciones OCR ni muestra los códigos QR/barra que el escáner también puede detectar. No reconoce tablas de cotizaciones ni importa PDFs del proveedor.

## Preparación de la demo

Publicar los `firestore.rules` y `storage.rules` vigentes; cuenta verificada/activa ADMIN, otra ALMACEN si se quiere separar tareas; proveedor por registrar; producto con stock conocido (ejemplo 10); presupuesto inicial S/1,000 sin compromisos previos. Si se usa consulta RUC, configurar DECOLECTA_TOKEN localmente y recompilar. No mostrar local.properties ni BuildConfig generado en pantalla.

La orden de ejemplo compra **5 unidades a S/20 sin IGV**: subtotal S/100, IGV S/18, total S/118. Al aprobar, disponible S/882 y reservado S/118; al recibir, stock 15 y reserva cero. Los importes internos son céntimos enteros.

## 1. Entramos a proveedores y consultamos RUC — HU08

“Comenzamos creando al proveedor que abastecerá nuestra compra. El formulario permite ingreso manual o una consulta RUC. Esta consulta solo ayuda a completar razón social y dirección; no registra al proveedor ni devuelve su catálogo o precios.”

**Abrimos [SupplierScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierScreen.kt:33>)**, línea 33. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            OutlinedTextField(ruc, { ruc = it.filter(Char::isDigit).take(11) }, Modifier.fillMaxWidth(), label = { Text("RUC") })
            TextButton(onClick = { apiOpen = !apiOpen }) { Text("Consultar razón social por RUC") }
            if (apiOpen) {
                Notice("Consulta los datos de la empresa por RUC y revísalos antes de guardar. Requiere conexión.")
                OutlinedButton(onClick = { vm.lookup(ruc) }, enabled = !state.busy) { Text("Consultar RUC") }
                state.lookup?.let { Text("Estado ${it.status} · Condición ${it.condition}") }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Razón social") })
```

“El botón delega al ViewModel. No realizamos la llamada HTTP dentro del botón: la acción del ViewModel ejecuta una corutina, controla la espera y publica el resultado en StateFlow.”

**Abrimos [SupplierViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierViewModel.kt:23>)**, línea 23. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun lookup(ruc: String) = action {
        val result = api.lookup(ruc.trim())
        mutable.value = mutable.value.copy(lookup = result, message = "Datos consultados. Revisa y confirma antes de guardar.")
    }
```

**Abrimos [SupplierViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierViewModel.kt:34>)**, línea 34. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "")
        viewModelScope.launch {
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(message = userMessage(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
```

“La siguiente llamada es RucLookup.lookup. Antes de consultar validamos el RUC. Esa validación incluye los 11 dígitos, el tipo y el dígito verificador.”

**Abrimos [InputRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/InputRules.kt:18>)**, línea 18. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun validRuc(value: String): Boolean {
        if (!value.matches(Regex("\\d{11}")) || value.take(2) !in setOf("10", "15", "16", "17", "20")) return false
        val weights = listOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
        return (11 - weights.indices.sumOf { (value[it] - '0') * weights[it] } % 11) % 10 == value.last() - '0'
    }
```

**Abrimos [RucApi.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/remote/RucApi.kt:19>)**, línea 19. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun lookup(ruc: String): RucResult {
        require(InputRules.validRuc(ruc)) { "Revisa el RUC antes de consultar." }
        val response = try {
            val token=credential().trim().removePrefix("Bearer ").trim()
            check(token.isNotBlank()) { "La consulta RUC aún no está configurada. Puedes completar los datos manualmente." }
            require(token.length in 8..2000 && token.matches(Regex("[A-Za-z0-9._~+/=-]+"))) { "La consulta RUC requiere revisar su configuración. Contacta al administrador." }
            api().lookup(ruc,"Bearer $token")
        } catch(e:retrofit2.HttpException) {
            // Never display upstream bodies, authorization headers or internal configuration.
```

“credential() y api() son dependencias que Koin configuró. La URL es Decolecta y la credencial se obtiene de configuración local de compilación. No se le pide al trabajador ni se envía la sesión Firebase a Decolecta.”

**Abrimos [AppModule.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/di/AppModule.kt:35>)**, línea 35. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    single<RucApi> { Retrofit.Builder().baseUrl("https://api.decolecta.com/")
        .client(okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,java.util.concurrent.TimeUnit.SECONDS).callTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(RucApi::class.java) }
    single { RucLookup(api={get<RucApi>()},credential={pe.edu.unmsm.siderurgica.BuildConfig.DECOLECTA_TOKEN}) }
```

**Abrimos [RucApi.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/remote/RucApi.kt:14>)**, línea 14. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
interface RucApi {
    @GET("v1/sunat/ruc") suspend fun lookup(@Query("numero") ruc: String, @Header("Authorization") bearer: String): RucResponse
}
```

“Retrofit construye la petición GET con numero en la consulta y Authorization en la cabecera. El método suspend permite esperar sin bloquear la interfaz. Cuando vuelve la respuesta, comprobamos que corresponda al RUC solicitado antes de publicarla.”

**Abrimos [RucApi.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/remote/RucApi.kt:36>)**, línea 36. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        require(response.number == ruc && !response.name.isNullOrBlank()) { "La consulta no devolvió datos válidos para ese RUC." }
        return RucResult(ruc,response.name,response.address.orEmpty(),response.status.orEmpty(),response.condition.orEmpty())
```

“Volvemos al StateFlow del ViewModel. Compose observa el estado y aplica esos datos al formulario. Todavía puedo revisarlos y corregirlos.”

**Abrimos [SupplierScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierScreen.kt:15>)**, línea 15. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    val state by vm.state.collectAsStateWithLifecycle()
    var form by rememberSaveable { mutableStateOf(false) }
```

**Abrimos [SupplierScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierScreen.kt:24>)**, línea 24. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    LaunchedEffect(state.lookup) { state.lookup?.let { ruc = it.ruc; name = it.name; address = it.address } }
```

**Cierre de esta consulta:** campos sugeridos y revisables. La API exige internet; no pasa por WorkManager. El guardado posterior sí puede quedar pendiente. En este modo académico la credencial va en el APK; local.properties evita subirla a Git, no protege el APK frente a extracción.

## 2. Guardamos al proveedor y evitamos duplicados — HU08

“Después de revisar razón social y dirección, agregamos contacto y teléfono. Ahora sí pulsamos Guardar y sincronizar. Construimos un SupplierDraft con los datos del formulario.”

**Abrimos [SupplierScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierScreen.kt:44>)**, línea 44. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            Button(onClick = { vm.save(SupplierDraft(ruc, name.trim(), address.trim(), contact.trim(), phone.trim()), true) }, enabled = !state.busy) { Text("Guardar y sincronizar") }
            OutlinedButton(onClick = { vm.save(SupplierDraft(ruc, name.trim(), address.trim(), contact.trim(), phone.trim()), false) }, enabled = !state.busy) { Text("Guardar solo borrador") }
```

**Abrimos [Supplier.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/model/Supplier.kt:3>)**, línea 3. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
data class SupplierDraft(val ruc: String, val name: String, val address: String, val contact: String, val phone: String)
```

**Abrimos [SupplierViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierViewModel.kt:27>)**, línea 27. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun save(draft: SupplierDraft, send: Boolean) = action {
        SupplierRules.validate(draft)
        require(mutable.value.suppliers.none { it.ruc == draft.ruc }) { "Ya existe un proveedor con ese RUC en los datos cargados." }
        queue.save("SUPPLIER", draft.name, draft, send)
        mutable.value = mutable.value.copy(saved = true, lookup = null, message = if (send) "Proveedor pendiente de sincronización." else "Borrador de proveedor guardado localmente.")
    }
```

“La primera llamada es SupplierRules.validate. Comprobamos RUC, razón social y longitudes. Después revisamos los proveedores cargados para advertir un duplicado; la protección definitiva ocurrirá en Firestore.”

**Abrimos [SupplierRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/SupplierRules.kt:5>)**, línea 5. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun validate(d: SupplierDraft) {
        require(InputRules.validRuc(d.ruc)) { "Ingresa un RUC válido de 11 dígitos." }
        require(d.name.trim().length in 2..160) { "Completa la razón social (2 a 160 caracteres)." }
        require(d.address.length <= 240 && d.contact.length <= 80 && d.phone.length <= 30) { "Revisa la longitud de los datos del proveedor." }
    }
```

“Seguimos inmediatamente queue.save. Guardamos un PendingOperation de tipo SUPPLIER en Room con el usuario, un UUID estable y el contenido serializado. El usuario puede escoger borrador, que no se envía, o pendiente, que programa sincronización.”

**Abrimos [QueueRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/QueueRepository.kt:15>)**, línea 15. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun save(kind: String, title: String, value: Any, send: Boolean, id: String = UUID.randomUUID().toString()): String {
        val uid = owner()
        dao.insert(PendingOperation(id, uid, kind, title, Gson().toJson(value), if (send) "PENDIENTE" else "BORRADOR", System.currentTimeMillis()))
        if (send) schedule(uid)
        return id
    }
```

**Abrimos [LocalDatabase.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/LocalDatabase.kt:6>)**, línea 6. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
@Entity(tableName = "pending", indices = [Index(value = ["owner", "status"])])
data class PendingOperation(@PrimaryKey val id: String, val owner: String, val kind: String, val title: String,
    val payload: String, val status: String, val createdAt: Long, val attempts: Int = 0, val message: String = "")
```

**Abrimos [LocalDatabase.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/LocalDatabase.kt:16>)**, línea 16. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(operation: PendingOperation)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOperations(operations: List<PendingOperation>)
```

“Este DAO pertenece a LocalDatabase, que está anotada como base Room. Los registros están en SQLite; no es lo mismo que dejar una variable en memoria.”

**Abrimos [LocalDatabase.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/LocalDatabase.kt:32>)**, línea 32. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
@Database(entities = [PendingOperation::class, CacheEntry::class, LocalDocument::class], version = 1, exportSchema = false)
abstract class LocalDatabase : RoomDatabase() { abstract fun dao(): LocalDao }
```

“Como pedimos enviar, llegamos a schedule. WorkManager necesita conexión, usa reintentos con espera creciente y recibe el dueño de la operación. Que haya red no significa que la ejecución sea instantánea: Android programa el trabajo.”

**Abrimos [QueueRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/QueueRepository.kt:36>)**, línea 36. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun schedule(uid: String = owner()) {
        val work = OneTimeWorkRequestBuilder<PendingSyncWorker>()
            .setInputData(workDataOf("owner" to uid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("firebase-sync-$uid", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }
```

“Cuando se ejecuta el worker, recuperamos operaciones pendientes de esa cuenta. La rama SUPPLIER llama al repositorio que escribe en Firestore.”

**Abrimos [PendingSyncWorker.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:20>)**, línea 20. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        val owner = inputData.getString("owner") ?: return Result.failure()
        if (services.auth.currentUser?.uid != owner) return Result.success()
        val dao = koin.get<LocalDao>()
        var retry = false
        for (operation in dao.ready(owner)) {
            if (services.auth.currentUser?.uid != owner) return Result.success()
            try {
```

**Abrimos [LocalDatabase.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/LocalDatabase.kt:19>)**, línea 19. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    @Query("SELECT * FROM pending WHERE owner=:owner AND status='PENDIENTE' ORDER BY createdAt LIMIT 100") suspend fun ready(owner: String): List<PendingOperation>
```

**Abrimos [PendingSyncWorker.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:32>)**, línea 32. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
                    "SUPPLIER" -> koin.get<FirestoreSupplierRepository>().create(operation.id, Gson().fromJson(operation.payload, SupplierDraft::class.java), owner)
```

**Abrimos [FirestoreSupplierRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreSupplierRepository.kt:25>)**, línea 25. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun create(id: String, draft: SupplierDraft, owner: String) {
        SupplierRules.validate(draft)
        require(services.auth.currentUser?.uid == owner) { "La operación pertenece a otra cuenta." }
        val ref = services.database.collection("proveedores").document(draft.ruc)
        services.database.runTransaction { tx ->
            val existing = tx.get(ref)
            if (existing.exists()) {
                require(existing.getString("operationId") == id && existing.getString("creatorUid") == owner) { "Ya existe un proveedor con ese RUC. Revisa el listado." }
            } else tx.set(ref, mapOf("ruc" to draft.ruc, "nombre" to draft.name.trim(), "direccion" to draft.address.trim(),
                "contacto" to draft.contact.trim(), "telefono" to draft.phone.trim(), "creatorUid" to owner,
                "operationId" to id, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }
}
```

“Usamos el RUC como ID del documento. Si existe con el mismo identificador de operación y creador, es el mismo reintento; no volvemos a crear. Si pertenece a otra creación, rechazamos el duplicado. La transacción guarda también operador y fecha del servidor.”

**Abrimos [firestore.rules](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/firestore.rules:145>)**, línea 145. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```text
    match /proveedores/{ruc} {
      allow read: if roleIn(['ADMIN', 'ALMACEN']);
      allow create: if roleIn(['ADMIN', 'ALMACEN'])
        && request.resource.data.keys().hasAll(['ruc', 'nombre', 'direccion', 'contacto', 'telefono', 'creatorUid', 'operationId', 'createdAt'])
        && request.resource.data.keys().hasOnly(['ruc', 'nombre', 'direccion', 'contacto', 'telefono', 'creatorUid', 'operationId', 'createdAt'])
        && ruc.matches('^(10|15|16|17|20)[0-9]{9}$') && request.resource.data.ruc == ruc
        && request.resource.data.nombre is string && request.resource.data.nombre.size() >= 2 && request.resource.data.nombre.size() <= 160
        && request.resource.data.direccion is string && request.resource.data.direccion.size() <= 240
```

“Las reglas vuelven a exigir ADMIN o ALMACEN y validan la escritura. Tras la confirmación remota, el worker cambia la fila local a ENVIADO. No marcamos éxito antes de recibir esa confirmación.”

**Abrimos [PendingSyncWorker.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:37>)**, línea 37. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
                dao.status(operation.id, owner, "ENVIADO", "Confirmado por Firebase.", operation.attempts + 1)
                if (services.auth.currentUser?.uid == owner) pe.edu.unmsm.siderurgica.notifications.AppNotifications.show(applicationContext, operation.id, "Sincronización completada", "La operación fue confirmada. Consulta Borradores.")
```

“Actualizamos el listado: FirestoreSupplierRepository.list consulta proveedores y conserva una caché por cuenta. Esa caché permite volver a mostrar datos ya consultados sin internet; no convierte la consulta RUC externa en una consulta offline.”

**Abrimos [FirestoreSupplierRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreSupplierRepository.kt:13>)**, línea 13. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun list(): List<Supplier> {
        val owner = services.auth.currentUser?.uid ?: error("Inicia sesión.")
        if (!cache.online) return cache.read<List<Supplier>>(owner, "suppliers", object : TypeToken<List<Supplier>>() {}.type) ?: emptyList()
        return try {
            services.database.collection("proveedores").get(Source.SERVER).await().documents.map { d ->
                Supplier(d.id, d.getString("nombre").orEmpty(), d.getString("direccion").orEmpty(), d.getString("contacto").orEmpty(), d.getString("telefono").orEmpty())
            }.sortedBy { it.name }.also { cache.save(owner, "suppliers", it) }
        } catch (e: Exception) {
            if (e is CancellationException || !networkFailure(e)) throw e
            cache.read<List<Supplier>>(owner, "suppliers", object : TypeToken<List<Supplier>>() {}.type) ?: throw e
```

## 3. Abrimos compras y cargamos sus datos — HU08

“Con el proveedor sincronizado abrimos Órdenes de compra. La pantalla solicita al ViewModel los proveedores, productos, órdenes, rol y presupuesto. Seleccionar un producto en la lista no consulta una API nueva por cada tecla: filtramos los datos ya cargados.”

**Abrimos [PurchaseScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:22>)**, línea 22. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    LaunchedEffect(Unit) { vm.refresh("purchases") }
```

**Abrimos [BusinessViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessViewModel.kt:25>)**, línea 25. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun refresh(kind:String)=action {
        val role=repo.currentRole()
        val budget=if(kind=="purchases") repo.purchaseBudget(mutable.value.period) else mutable.value.budget
        val products=commerce.products()
        val clients=if(kind in listOf("quotes","orders","sales")) repo.clients() else emptyList()
        val providers=if(kind=="purchases") suppliers.list() else emptyList()
        val records=if(kind=="movements") emptyList() else repo.records(kind)
        val movements=if(kind=="movements") repo.movements() else emptyList()
        mutable.value=mutable.value.copy(products=products,clients=clients,suppliers=providers,records=records,movements=movements,role=role,budget=budget)
    }
```

“repo es el contrato BusinessRepository; Koin lo resuelve a FirestoreBusinessRepository. FirebaseServices proporciona las instancias de Authentication y Firestore que se utilizan en las operaciones.”

**Abrimos [AppModule.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/di/AppModule.kt:26>)**, línea 26. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    single<pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository> { FirestoreBusinessRepository(get(), get()) }
```

**Abrimos [FirebaseServices.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirebaseServices.kt:14>)**, línea 14. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    val database: FirebaseFirestore by lazy {
        check(configured) { "Firebase no está configurado." }
        FirebaseFirestore.getInstance()
    }
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:26>)**, línea 26. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    override suspend fun records(kind: String): List<BusinessRecord> = cached("v2-$kind") {
        db.collection(collection(kind)).get(Source.SERVER).await().documents.map { d ->
            gson.fromJson(gson.toJson(d.data.orEmpty().filterKeys { it !in listOf("createdAt", "updatedAt", "decisionAt", "receivedAt") }), BusinessRecord::class.java)
                .copy(id=d.id, createdAt=d.getTimestamp("createdAt")?.toDate()?.time ?: 0, decisionAt=d.getTimestamp("decisionAt")?.toDate()?.time ?: 0, receivedAt=d.getTimestamp("receivedAt")?.toDate()?.time ?: 0)
        }.sortedByDescending { it.createdAt }
    }
```

“Aquí purchases corresponde a compras_v2. Convertimos las fechas de servidor al formato que utiliza nuestro modelo y devolvemos la lista al StateFlow. La etiqueta que ve el usuario es diferente al estado técnico almacenado.”

**Abrimos [PurchaseScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:100>)**, línea 100. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
internal fun purchaseStatus(s:String)=when(s){"PENDIENTE"->"Por aprobar";"APROBADA"->"Por recibir";"RECIBIDA"->"Recibida";"RECHAZADA"->"Rechazada";"CANCELADA"->"Cancelada";else->s}
```

## 4. Preparamos el presupuesto — HU08, acción de ADMIN

“Si el período no tiene presupuesto, Administración define su límite. Esto es control de compras, no saldo bancario. El botón crea BUDGET_SET y reutiliza el mismo camino local: ViewModel, cola, WorkManager y repositorio.”

**Abrimos [PurchaseScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:110>)**, línea 110. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    Button(onClick={val n=runCatching{java.math.BigDecimal(amount.replace(',','.')).movePointRight(2).longValueExact()}.getOrDefault(-1);vm.submit(BusinessCommand("BUDGET_SET",target=s.period,budgetCents=n))},enabled=!s.busy&&period==s.period){Text("Guardar presupuesto y sincronizar")}
```

“Antes de entrar al caso concreto, abrimos la puerta común execute. Cada comando comercial tiene un registro de auditoría en operaciones_v2. Si vuelve el mismo comando confirmado, verificamos identidad y contenido y no ejecutamos otra vez sus efectos.”

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:49>)**, línea 49. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    override suspend fun execute(id: String, owner: String, command: BusinessCommand) {
        require(owner() == owner) { "La operación pertenece a otra cuenta." }
        val audit = db.collection("operaciones_v2").document(id)
        db.runTransaction { tx ->
            val done = tx.get(audit)
            val fingerprint = gson.toJson(command)
            if (done.exists()) {
                require(done.getString("owner") == owner && gson.fromJson(done.getString("payload"), BusinessCommand::class.java) == command) { "Identificador de operación reutilizado." }
            } else {
                when(command.action) {
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:66>)**, línea 66. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
                    "QUOTE_CREATE", "ORDER_CREATE", "PURCHASE_CREATE" -> create(tx,id,owner,command)
                    "QUOTE_STATE", "ORDER_STATE" -> state(tx,id,owner,command)
                    "SALE_CREATE", "PURCHASE_RECEIVE" -> fulfill(tx,id,owner,command)
                    "BUDGET_SET" -> budget(tx,id,owner,command)
                    "PURCHASE_STATE" -> purchaseState(tx,id,owner,command)
                    "MOVEMENT" -> manual(tx,id,owner,command)
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:74>)**, línea 74. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
                tx.set(audit, mapOf("owner" to owner, "action" to command.action, "target" to command.target,
                    "payload" to fingerprint, "createdAt" to FieldValue.serverTimestamp()))
```

“En BUDGET_SET llegamos a budget. Leemos las reservas y compras recibidas existentes. Solo ADMIN puede configurar y no puede reducir el total por debajo de lo ya comprometido.”

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:155>)**, línea 155. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    private fun budget(tx:Transaction,id:String,owner:String,c:BusinessCommand) {
        PurchaseRules.period(c.target)
        val ref=db.collection("presupuestos_compras").document(c.target)
        val old=tx.get(ref)
        require(tx.get(db.collection("usuarios").document(owner)).getString("rol")=="ADMIN") { "Solo ADMIN configura el presupuesto." }
        val reserved=old.getLong("reservedCents")?:0L; val spent=old.getLong("spentCents")?:0L
        PurchaseRules.available(c.budgetCents,reserved,spent)
        tx.set(ref,mapOf("totalCents" to c.budgetCents,"reservedCents" to reserved,"spentCents" to spent,"lastOperation" to id,"lastOrder" to "","updatedAt" to FieldValue.serverTimestamp()))
    }
```

**Abrimos [PurchaseRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/PurchaseRules.kt:15>)**, línea 15. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun available(total: Long, reserved: Long, spent: Long): Long {
        require(total in 0..400_000_000_000_000L && reserved >= 0 && spent >= 0 && reserved <= total && spent <= total-reserved) { "Presupuesto insuficiente o inválido." }
        return total-reserved-spent
    }
```

**Resultado:** disponible = total − reservado − recibido. En la demo comenzamos con S/1,000 disponibles y sin compras previas. No existe trámite de préstamo ni pago en esta función.

## 5. Registramos la compra según la cotización recibida — HU08

“La cotización vino del proveedor por un canal externo. Aquí la registramos manualmente: seleccionamos proveedor y productos, indicamos cantidades y el precio de compra sin IGV. La referencia identifica el acuerdo; no importa automáticamente un archivo ni extrae sus tablas.”

**Abrimos [BusinessScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessScreen.kt:77>)**, línea 77. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        SearchableSelector("Proveedor",supplier,s.suppliers.map{SelectorOption(it.ruc,it.name,"RUC ${it.ruc}",it.address)},
            onSelect={supplier=it},enabled=!s.busy,emptyText="Registra y sincroniza un proveedor desde Más → Proveedores y actualiza la consulta.")
```

**Abrimos [BusinessScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessScreen.kt:96>)**, línea 96. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    SearchableSelector("Producto",selected,s.products.map{SelectorOption(it.id,it.name,if(purchase) "${it.category} · stock ${it.stock}" else "${it.category} · venta ${money(it.priceCents)} · stock ${it.stock}")},
        onSelect={id->selected=id;price=if(purchase) "" else java.math.BigDecimal.valueOf(s.products.first{it.id==id}.priceCents,2).toPlainString()},
        enabled=!s.busy,emptyText="No hay productos cargados. Actualiza el catálogo o registra productos desde Stock.")
    OutlinedTextField(quantity,{quantity=it},Modifier.fillMaxWidth(),label={Text("Cantidad entera")})
    if(purchase)OutlinedTextField(price,{price=it},Modifier.fillMaxWidth(),label={Text("Precio de compra unitario SIN IGV S/")})
```

**Abrimos [BusinessScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessScreen.kt:110>)**, línea 110. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            val next=BusinessLineDraft(p.id,quantity.toLongOrNull()?:0,if(purchase)cents(price)else p.priceCents,if(purchase)0 else BusinessRules.automaticDiscount(quantity.toLongOrNull()?:0))
            BusinessRules.lines(lines+next,s.products,purchase);lines.add(next)
        }.exceptionOrNull()?.message.orEmpty()
```

“El selector devuelve un ID. Para compras dejamos el precio vacío al cambiar producto: no reutilizamos el precio de venta. Al agregar validamos cada línea antes de incorporarla al detalle. Seguimos esa llamada a BusinessRules.lines con purchase=true.”

**Abrimos [BusinessRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/BusinessRules.kt:25>)**, línea 25. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun lines(drafts: List<BusinessLineDraft>, products: List<Product>, purchase: Boolean = false): List<BusinessLine> {
        require(drafts.size in 1..MAX_LINES) { "Agrega de uno a tres productos distintos." }
        require(drafts.map { it.productId }.distinct().size == drafts.size) { "No repitas productos; modifica la cantidad." }
        return drafts.map { d ->
            val p = products.firstOrNull { it.id == d.productId } ?: error("Producto no encontrado.")
            require(d.quantity in 1..1_000_000 && d.priceCents in 1..100_000_000 && d.discountPercent in 0..30) { "Revisa cantidades, precios y descuento (0–30 %)." }
            require(purchase || d.priceCents == p.priceCents) { "El precio cambió. Revisa el borrador y prepara una propuesta nueva." }
            require(purchase || d.discountPercent == automaticDiscount(d.quantity)) { "El descuento debe calcularse automáticamente según la cantidad. Prepara una propuesta nueva." }
            require(!purchase || d.discountPercent == 0L) { "Las compras utilizan el precio pactado, sin descuento adicional." }
            val gross = Math.multiplyExact(d.quantity, d.priceCents)
            val net = BigDecimal.valueOf(gross).multiply(BigDecimal.valueOf(100-d.discountPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact()
            BusinessLine(p.id, p.name, d.quantity, d.priceCents, d.discountPercent, gross, net)
        }
    }
```

“Comprobamos producto existente, cantidades, precio y ausencia de repetidos. Una compra acepta el precio pactado y no aplica el descuento automático de ventas. El límite actual es tres líneas; es una restricción del avance, no una regla del negocio.”

**Abrimos [BusinessRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/BusinessRules.kt:40>)**, línea 40. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun tax(subtotal: Long) = SalesRules.taxCents(subtotal)
    fun total(lines: List<BusinessLine>): Triple<Long, Long, Long> {
        val subtotal = lines.fold(0L) { sum, l -> Math.addExact(sum, l.netCents) }
        val tax = tax(subtotal)
        return Triple(subtotal, tax, Math.addExact(subtotal, tax))
    }
```

**Abrimos [SalesRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/SalesRules.kt:4>)**, línea 4. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun taxCents(netSubtotal: Long): Long {
        require(netSubtotal in 1..100_000_000_000_000) { "Importe fuera de rango." }
        return Math.addExact(Math.multiplyExact(netSubtotal, 18), 50) / 100
    }
```

“Aunque el helper se llama SalesRules, aquí solo reutilizamos su cálculo del IGV. Trabajamos con céntimos enteros y redondeo: 5 × 2,000 = 10,000 céntimos de subtotal; IGV 1,800; total 11,800.”

**Abrimos [BusinessScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessScreen.kt:124>)**, línea 124. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun command():BusinessCommand{val c=s.clients.firstOrNull{it.id==clientId};return BusinessCommand(if(purchase)"PURCHASE_CREATE" else if(kind=="orders")"ORDER_CREATE" else "QUOTE_CREATE",client=c?.name.orEmpty(),ruc=c?.ruc.orEmpty(),clientId=clientId,lines=lines.toList(),supplierRuc=supplier,budgetPeriod=if(purchase)s.period else "",deliveryDate=if(purchase)delivery else "",supplierQuote=if(purchase)supplierQuote.trim() else "")}
    Button(onClick={vm.submit(command())},enabled=!s.busy&&lines.isNotEmpty()){Text(if(purchase)"Solicitar aprobación" else "Guardar y sincronizar")}
    OutlinedButton(onClick={vm.submit(command(),false)},enabled=!s.busy&&lines.isNotEmpty()){Text("Guardar solo borrador")}
```

**Abrimos [BusinessViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessViewModel.kt:48>)**, línea 48. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            "PURCHASE_CREATE" -> {
                require(c.supplierRuc.isNotBlank()) { "Selecciona un proveedor." }
                BusinessRules.lines(c.lines,mutable.value.products,true)
                PurchaseRules.metadata(c.budgetPeriod,c.deliveryDate,c.supplierQuote)
            }
```

**Abrimos [PurchaseRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/PurchaseRules.kt:5>)**, línea 5. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun metadata(period: String, delivery: String, quote: String) {
        period(period)
        require(runCatching { java.time.LocalDate.parse(delivery) }.isSuccess) { "Indica entrega estimada AAAA-MM-DD válida." }
        require(quote.trim().length in 3..120) { "Indica referencia de cotización o acuerdo (3–120 caracteres)." }
    }
```

“Al solicitar aprobación guardamos BUSINESS en la misma cola ya explicada. WorkManager entra a execute y deriva PURCHASE_CREATE a create. Ahora comprobamos en servidor que proveedor y productos sigan existiendo.”

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:80>)**, línea 80. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    private fun create(tx: Transaction,id: String,owner: String,c: BusinessCommand) {
        val purchase = c.action == "PURCHASE_CREATE"
        val order = c.action == "ORDER_CREATE"
        val kind = if(purchase) "compras_v2" else if(order) "pedidos_v2" else "cotizaciones_v2"
        val targetId = if(order && c.reference.isNotBlank()) c.reference else id
        val ref = db.collection(kind).document(targetId)
        require(!tx.get(ref).exists()) { "El documento ya fue creado." }
        val quote = if(order && c.reference.isNotBlank()) tx.get(db.collection("cotizaciones_v2").document(c.reference)) else null
        val client = if(purchase) null else tx.get(db.collection("clientes_v2").document(c.clientId))
        val supplier = if(purchase) tx.get(db.collection("proveedores").document(c.supplierRuc)) else null
        if(purchase) require(supplier?.exists() == true) { "Selecciona un proveedor registrado y sincronizado." }
        else {
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:96>)**, línea 96. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        val snapshots = c.lines.map { tx.get(db.collection("productos").document(it.productId)) }
        require(snapshots.all { it.exists() }) { "Producto no encontrado." }
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:105>)**, línea 105. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        val amounts = BusinessRules.total(lines)
        if(purchase) PurchaseRules.metadata(c.budgetPeriod,c.deliveryDate,c.supplierQuote)
        val data = mapOf("owner" to owner,"client" to (if(purchase) supplier!!.getString("nombre").orEmpty() else c.client.trim()),
            "ruc" to (if(purchase) c.supplierRuc else c.ruc),"clientId" to c.clientId,
            "lines" to lines.map(::lineMap),"status" to "PENDIENTE", "subtotalCents" to amounts.first,"taxCents" to amounts.second,
            "totalCents" to amounts.third,"reference" to c.reference,"supplierRuc" to c.supplierRuc,"budgetCents" to (if(purchase) 0L else c.budgetCents),
            "lastOperation" to id,"createdAt" to FieldValue.serverTimestamp())
        tx.set(ref,if(purchase) data + mapOf("budgetPeriod" to c.budgetPeriod,"deliveryDate" to c.deliveryDate,"supplierQuote" to c.supplierQuote.trim()) else data)
        if(quote != null) tx.update(quote.reference,mapOf("status" to "CONVERTIDA","lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()))
```

**Resultado:** compras_v2 contiene la orden PENDIENTE, visible como Por aprobar. No reservamos presupuesto ni aumentamos stock. BusinessRecord usa client/ruc como campos genéricos; en una compra contienen nombre/RUC del proveedor, no un cliente de ventas. Las ramas de cotización/ventas que aparecen en este método compartido no son cotizaciones del proveedor.

## 6. Administración aprueba y reserva capital — HU08

“Abrimos el detalle y Administración deja el motivo. Aprobar compra crea PURCHASE_STATE con APROBADA. Reutilizamos validación, cola y auditoría; ahora execute invoca purchaseState.”

**Abrimos [PurchaseScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:112>)**, línea 112. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
@Composable private fun PurchaseDecisionForm(r:BusinessRecord,s:BusinessState,vm:BusinessViewModel) {
    var reason by rememberSaveable(r.id){mutableStateOf("")}
    OutlinedTextField(reason,{reason=it},Modifier.fillMaxWidth(),label={Text("Motivo de la decisión")})
    if(r.status=="PENDIENTE" && r.budgetPeriod.isNotBlank()) {
        ConfirmButton("Aprobar compra","Se reservará ${money(r.totalCents)} del presupuesto. La mercadería se registrará cuando llegue.",!s.busy){vm.submit(BusinessCommand("PURCHASE_STATE",r.id,state="APROBADA",reason=reason))}
        ConfirmButton("Rechazar orden","No modifica inventario ni reserva presupuesto.",!s.busy){vm.submit(BusinessCommand("PURCHASE_STATE",r.id,state="RECHAZADA",reason=reason))}
    }
    ConfirmButton("Cancelar orden","Si estaba autorizada, libera su reserva una sola vez. No borra el historial.",!s.busy){vm.submit(BusinessCommand("PURCHASE_STATE",r.id,state="CANCELADA",reason=reason))}
}
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:164>)**, línea 164. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    private fun purchaseState(tx:Transaction,id:String,owner:String,c:BusinessCommand) {
        PurchaseRules.reason(c.reason)
        val ref=db.collection("compras_v2").document(c.target); val d=tx.get(ref)
        val status=d.getString("status")
        require(d.exists() && ((status=="PENDIENTE" && c.state in listOf("APROBADA","RECHAZADA","CANCELADA")) || (status=="APROBADA" && c.state=="CANCELADA"))) { "Transición de compra no permitida. Actualiza." }
        require(tx.get(db.collection("usuarios").document(owner)).getString("rol")=="ADMIN") { "Solo ADMIN autoriza o cancela compras." }
        if(c.state=="APROBADA" || status=="APROBADA") {
            val period=d.getString("budgetPeriod")?:error("Orden histórica: crea una nueva orden con período.")
            val bRef=db.collection("presupuestos_compras").document(period);val b=tx.get(bRef)
            val total=b.getLong("totalCents")?:0L;val reserved=b.getLong("reservedCents")?:0L;val spent=b.getLong("spentCents")?:0L
            val amount=d.getLong("totalCents")!!
            if(c.state=="APROBADA") require(PurchaseRules.available(total,reserved,spent)>=amount) { "Presupuesto insuficiente. Solicita disponibilidad a administración." }
            val next=reserved+if(c.state=="APROBADA") amount else -amount
            PurchaseRules.available(total,next,spent)
            tx.update(bRef,mapOf("reservedCents" to next,"lastOperation" to id,"lastOrder" to c.target,"updatedAt" to FieldValue.serverTimestamp()))
        }
        tx.update(ref,mapOf("status" to c.state,"decisionBy" to owner,"decisionAt" to FieldValue.serverTimestamp(),"decisionReason" to c.reason.trim(),"responseChannel" to "INTERNO","lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()))
    }
```

“Primero controlamos la transición y el rol. Si aprobamos, la función available que acabamos de mostrar comprueba el disponible vigente; sumamos la reserva en la misma transacción que cambia el estado. Una aprobación concurrente obliga a reevaluar los documentos. Cancelar una aprobada resta esa reserva; una recibida ya no admite esta cancelación.”

**Resultado de la demo:** APROBADA / Por recibir; reservado S/118, disponible S/882, stock todavía 10. Si no alcanza presupuesto no se aplica ninguno de esos cambios. Rechazar una pendiente no modifica stock ni reserva.

## 7. Generamos el PDF de la orden aprobada — HU08

“Podemos preparar el documento para enviarlo al proveedor. Es nuestra orden de compra, no la factura del proveedor. Desde la acción de interfaz llamamos a generate.”

**Abrimos [PurchasePdfActions.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePdfActions.kt:41>)**, línea 41. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    Button(onClick={perform{file=repository.generate(purchase);message="PDF preparado con los importes guardados. Puedes revisarlo y compartirlo."}},enabled=!busy){Text("Generar PDF de orden de compra")}
```

**Abrimos [PurchasePdfRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/PurchasePdfRepository.kt:38>)**, línea 38. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun generate(purchase:BusinessRecord):File=withContext(Dispatchers.IO){
        val owner=services.auth.currentUser?.uid?:error("Inicia sesión.")
        require(purchase.id.matches(Regex("[a-f0-9-]{36}"))) { "Referencia de orden inválida." }
        val rows=PurchasePdfContent.rows(purchase)
        val directory=File(context.filesDir,"ordenes-compra/$owner").apply{mkdirs()}
        val target=File(directory,"OC-${purchase.id}.pdf")
        val temporary=File(directory,"OC-${purchase.id}.tmp")
        val pdf=PdfDocument()
        try {
```

“Antes de dibujar el PDF, generate llama a PurchasePdfContent.rows. Esa función exige APROBADA y utiliza los precios ya guardados, sin consultar ni cambiar el precio de venta.”

**Abrimos [PurchasePdfRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/PurchasePdfRepository.kt:14>)**, línea 14. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun rows(purchase:BusinessRecord):List<String> {
        require(purchase.lines.size in 1..3 && purchase.client.isNotBlank()) { "La orden no contiene un detalle válido." }
        fun amount(cents:Long)="S/ ${BigDecimal.valueOf(cents,2).toPlainString()}"
        require(purchase.status=="APROBADA") { "Solo se emiten órdenes autorizadas." }
        return buildList {
            add("SIDERÚRGICA PERÚ")
            add("ORDEN DE COMPRA")
```

**Abrimos [PurchasePdfRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/PurchasePdfRepository.kt:29>)**, línea 29. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            add("Subtotal: ${amount(purchase.subtotalCents)}")
            add("IGV 18 %: ${amount(purchase.taxCents)}")
            add("TOTAL: ${amount(purchase.totalCents)}")
            add("Coordinar entrega con la empresa. No acredita pago ni recepción de mercadería.")
```

“PdfDocument dibuja esas filas y las escribe en el almacenamiento interno de esta cuenta. Si necesitamos revisar la escritura exacta, abrimos la parte final del método.”

**Abrimos [PurchasePdfRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/PurchasePdfRepository.kt:64>)**, línea 64. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            pdf.finishPage(page)
            temporary.outputStream().use{pdf.writeTo(it)}
            require(services.auth.currentUser?.uid==owner){"La sesión cambió durante la generación."}
            temporary.copyTo(target,overwrite=true)
        } finally {pdf.close();temporary.delete()}
```

“Compartimos el archivo por FileProvider, con permiso temporal de lectura y el selector de aplicaciones de Android. No hay un envío automático al sistema del proveedor.”

**Abrimos [PurchasePdfActions.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePdfActions.kt:50>)**, línea 50. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            val intent=Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri)
                .apply { clipData=ClipData.newRawUri("Orden de compra",uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent,"Compartir orden de compra con el proveedor"))
        }},enabled=!busy){Text("Compartir orden de compra")}
```

**Cierre:** PDF local para revisar, guardar o compartir. No se sube a Storage en este recorrido, no acredita pago y no interviene en el OCR de recepción.

## 8. Llega la mercadería y abrimos la recepción — HU08

“Almacén recibe físicamente los productos. Desde una orden aprobada abre Registrar entrega. Revisa todas las cantidades, registra número de guía o factura y observaciones. No marca llegada de productos que aún faltan.”

**Abrimos [PurchaseScreen.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:121>)**, línea 121. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
@Composable private fun PurchaseReceptionForm(r:BusinessRecord,s:BusinessState,vm:BusinessViewModel) {
    var document by rememberSaveable(r.id){mutableStateOf("")}
    var notes by rememberSaveable(r.id){mutableStateOf("")}
    var checked by rememberSaveable(r.id){mutableStateOf(arrayListOf<String>())}
    var photo by rememberSaveable(r.id){mutableStateOf("")}
    var scanning by remember{mutableStateOf(false)}
    Text("Verifica físicamente cada producto y su cantidad completa. No confirmes entregas parciales o productos con observaciones pendientes.")
    r.lines.forEach { l-> Row { Checkbox(l.productId in checked,{yes->checked=ArrayList(if(yes)(checked+l.productId).distinct() else checked-l.productId)});Text("Recibí conforme ${l.quantity} de ${l.name}",Modifier.padding(top=12.dp)) } }
    OutlinedTextField(document,{document=it},Modifier.fillMaxWidth(),label={Text("Número de factura o guía del proveedor")})
    OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("Observaciones / referencia documental (opcional)")})
    PurchasePhotoCapture(photo,onPhoto={path,number->photo=path;if(document.isBlank()&&number.isNotBlank())document=number},onBusy={scanning=it})
    ConfirmButton("Confirmar recepción completa","Se sumarán las cantidades verificadas al inventario cuando se confirme el envío. La fotografía, si la incluyes, se enviará por separado.",!s.busy&&!scanning&&checked.size==r.lines.size){vm.submit(BusinessCommand("PURCHASE_RECEIVE",r.id,receiptDocument=document.trim(),receiptNotes=notes.trim()),receiptPhoto=photo)}
}

```

“Aquí se ve exactamente la frontera con HU09: PurchasePhotoCapture es una ayuda opcional dentro de esta pantalla. La llamada onPhoto recibe ruta y número reconocido; solo aplica el número si el campo está vacío. No sobrescribe lo que el trabajador ya escribió. Las casillas son confirmación humana, no resultados del OCR.”

## 9. Tomamos la fotografía o elegimos una imagen — intervención HU09

“Cuando el trabajador desea respaldo usa cámara o galería. La foto inicial utiliza una URI de FileProvider, que permite a la cámara escribir en el destino temporal sin exponer una ruta privada.”

**Abrimos [PurchasePhotoCapture.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePhotoCapture.kt:32>)**, línea 32. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok&&capture.isNotBlank())scan(android.net.Uri.parse(capture))}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let(::scan)}
```

**Abrimos [PurchasePhotoCapture.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePhotoCapture.kt:42>)**, línea 42. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            val file=File(context.cacheDir,"capturas/${java.util.UUID.randomUUID()}.jpg").apply{parentFile?.mkdirs()}
            capture=FileProvider.getUriForFile(context,"${context.packageName}.files",file).toString()
            runCatching{camera.launch(android.net.Uri.parse(capture))}.onFailure{message="No se pudo abrir la cámara. Puedes elegir una imagen."}
```

“TakePicture entrega un booleano; si se obtuvo imagen usamos la URI preparada. GetContent devuelve directamente una URI. Ambos caminos llaman a scan, que ejecuta el análisis en una corutina y muestra el progreso.”

**Abrimos [PurchasePhotoCapture.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePhotoCapture.kt:27>)**, línea 27. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun scan(uri:android.net.Uri){busy=true;onBusy(true);scope.launch {
        try {val result=DocumentScanner.scan(context,uri);onPhoto(result.imagePath,result.invoice.number);message=if(result.invoice.number.isBlank())"Foto preparada. Escribe el número del documento." else "Número sugerido por lectura de imagen. Revísalo antes de confirmar."}
        catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}
        finally{busy=false;onBusy(false)}
    }}
```

## 10. Procesamos la imagen y sugerimos el número — intervención HU09

“La llamada entra a DocumentScanner.scan. El trabajo pesado se mueve a Dispatchers.IO. Primero leemos dimensiones y reducimos la imagen para limitar memoria; aplicamos su orientación EXIF antes del reconocimiento.”

**Abrimos [DocumentScanner.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/DocumentScanner.kt:20>)**, línea 20. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun scan(context: Context, uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "No se pudo leer la imagen. Prueba otra fotografía." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("No se pudo abrir la imagen.")
        val exif = context.contentResolver.openInputStream(uri).use { stream -> stream?.let { androidx.exifinterface.media.ExifInterface(it) } }
        val transform = android.graphics.Matrix().apply {
```

**Abrimos [DocumentScanner.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/DocumentScanner.kt:28>)**, línea 28. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        val exif = context.contentResolver.openInputStream(uri).use { stream -> stream?.let { androidx.exifinterface.media.ExifInterface(it) } }
        val transform = android.graphics.Matrix().apply {
            if(exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        if(bitmap !== decoded) decoded.recycle()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
```

“Creamos los lectores de texto y código de ML Kit y les entregamos la misma imagen orientada. ML Kit devuelve texto; InvoiceParser convierte partes de ese texto en sugerencias estructuradas.”

**Abrimos [DocumentScanner.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/DocumentScanner.kt:35>)**, línea 35. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val barcode = BarcodeScanning.getClient()
        try {
            // OCR and saved JPEG share the same orientation and bounded memory usage.
            val input = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(input).await().text
            val codes = barcode.process(input).await().mapNotNull { it.rawValue }.joinToString("\n")
            val file = File(context.filesDir, "documentos/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            ScanResult(file.absolutePath, InvoiceParser.parse(text), codes, text)
        } finally { bitmap.recycle(); recognizer.close(); barcode.close() }
    }
}
```

**Abrimos [InvoiceParser.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/InvoiceParser.kt:5>)**, línea 5. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun parse(text: String): ExtractedInvoice {
        val ruc = Regex("\\b\\d{11}\\b").findAll(text).map { it.value }.firstOrNull(InputRules::validRuc).orEmpty()
        val number = Regex("\\b[A-Z]\\d{3}\\s*[-–]\\s*\\d{1,12}\\b", RegexOption.IGNORE_CASE).find(text)?.value.orEmpty()
        val date = Regex("\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}\\b").find(text)?.value.orEmpty()
        val rawAmount = Regex("\\b(?:TOTAL(?: A PAGAR)?|IMPORTE TOTAL)\\s*[:]?\\s*(?:S/\\.?|PEN)?\\s*([0-9]{1,3}(?:[, .][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2})(?=\\s|$)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1).orEmpty()
        val amount = if(rawAmount.length >= 4) rawAmount.dropLast(3).filter(Char::isDigit) + "." + rawAmount.takeLast(2) else ""
        return ExtractedInvoice(ruc, number, date, amount)
    }
}
```

“El número se busca con un patrón de serie y correlativo. Es una heurística sobre OCR, no comprensión completa de una guía. Algunas series o documentos no coincidirán y se completarán manualmente. Guardamos un JPEG comprimido al 80 % en filesDir/documentos; esa ruta duradera es diferente al archivo temporal de cámara.”

“Volvemos a PurchasePhotoCapture: de ScanResult solo pasamos imagePath e invoice.number al formulario de recepción. Aunque el escáner también produce RUC, fecha, monto, texto y códigos, esta pantalla no los aplica a la compra. No interpreta líneas de productos ni modifica cantidades.”

**Cierre HU09 en esta fase:** imagen local, vista previa y posible número sugerido. Todavía no subimos el archivo ni actualizamos stock. El trabajador puede revisar, corregir, quitar/reemplazar foto o confirmar sin imagen.

## 11. Confirmamos y persistimos las dos tareas — HU08 + soporte HU09

“Al confirmar, PurchaseRules.receipt valida documento y observaciones. BusinessViewModel.submit distingue recepción de una operación comercial normal: delega a queue.purchaseReceipt y entrega aparte la ruta de la imagen.”

**Abrimos [PurchaseRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/PurchaseRules.kt:11>)**, línea 11. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun receipt(document: String, notes: String) {
        require(document.trim().length in 3..120) { "Indica factura o guía del proveedor (3–120 caracteres)." }
        require(notes.trim().length <= 240) { "Las observaciones admiten hasta 240 caracteres." }
    }
```

**Abrimos [BusinessViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessViewModel.kt:66>)**, línea 66. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        if(c.action=="PURCHASE_RECEIVE" && send) queue.purchaseReceipt(c,receiptPhoto)
        else queue.save("BUSINESS",label+" · "+c.client.ifBlank { c.target.take(12) },c,send)
        mutable.value=mutable.value.copy(saved=mutable.value.saved+1,message=if(c.action.startsWith("PURCHASE") || c.action=="BUDGET_SET") { if(send) "Cambios guardados para enviar. Actualiza en unos momentos para consultar el resultado." else "Borrador guardado. Puedes enviarlo desde Pendientes." } else if(send) "Operación en cola. Su estado definitivo se confirma en Borradores y sincronización; actualiza después el listado." else "Borrador guardado en SQLite, sin enviar.")
```

**Abrimos [QueueRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/QueueRepository.kt:21>)**, línea 21. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun purchaseReceipt(command:pe.edu.unmsm.siderurgica.domain.model.BusinessCommand,imagePath:String) {
        require(command.action=="PURCHASE_RECEIVE")
        val uid=owner();val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
        val operations=mutableListOf(PendingOperation(id,uid,"BUSINESS","Recepción de compra",Gson().toJson(command),"PENDIENTE",now))
        if(imagePath.isNotBlank()) operations.add(PendingOperation(UUID.randomUUID().toString(),uid,"PURCHASE_PHOTO","Foto de la entrega",Gson().toJson(pe.edu.unmsm.siderurgica.domain.model.PurchasePhoto(command.target,imagePath,id)),"PENDIENTE",now+1))
        // A Room @Insert of a list is transactional: receipt and optional photo survive together.
        dao.insertOperations(operations)
        schedule(uid)
    }
```

**Abrimos [PurchasePhoto.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/model/PurchasePhoto.kt:2>)**, línea 2. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
data class PurchasePhoto(val orderId:String="",val imagePath:String="",val receiptOperation:String="")
```

**Abrimos [LocalDatabase.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/LocalDatabase.kt:17>)**, línea 17. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOperations(operations: List<PendingOperation>)
```

“Room inserta la lista transaccionalmente: no guardamos solo media pareja. La recepción lleva un UUID y la foto otro, con receiptOperation apuntando a la primera tarea. Primero se ordena recepción; luego foto. Si no adjuntamos imagen, solo creamos la tarea de recepción. La imagen permanece como archivo interno; SQLite conserva su ruta y vínculo, no sus bytes.”

## 12. Confirmamos recepción e inventario en Firestore — HU08

“WorkManager toma la tarea BUSINESS. execute ya conoce el UUID y deriva PURCHASE_RECEIVE a fulfill. La auditoría que mostramos impide aplicar otra vez un comando confirmado.”

**Abrimos [PendingSyncWorker.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:29>)**, línea 29. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
                    "BUSINESS" -> koin.get<pe.edu.unmsm.siderurgica.domain.repository.BusinessRepository>().execute(operation.id, owner, Gson().fromJson(operation.payload, BusinessCommand::class.java))
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:129>)**, línea 129. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    private fun fulfill(tx: Transaction,id: String,owner: String,c: BusinessCommand) {
        val purchase = c.action == "PURCHASE_RECEIVE"
        val ref = db.collection(if(purchase) "compras_v2" else "pedidos_v2").document(c.target)
        val d=tx.get(ref)
        require(d.exists()) { "Documento no encontrado." }
        require(d.getString("status") == (if(purchase) "APROBADA" else "APROBADO")) { "La operación ya fue completada o cambió de estado." }
        val budgetRef=if(purchase) db.collection("presupuestos_compras").document(d.getString("budgetPeriod")?:error("Orden histórica: crea una orden nueva con período.")) else null
        val budget=budgetRef?.let { tx.get(it) }
        if(purchase) PurchaseRules.receipt(c.receiptDocument,c.receiptNotes)
        val saleRef = db.collection("ventas_v2").document(c.target)
        if(!purchase) require(!tx.get(saleRef).exists()) { "La venta ya existe." }
        val lines=decodeLines(d)
        val products=lines.map { tx.get(db.collection("productos").document(it.productId)) }
        val movements=lines.mapIndexed { i,l -> movement(id,l.productId,l.name,if(purchase) "COMPRA" else "VENTA",products[i].getLong("stock") ?: error("Stock inexistente."),l.quantity,-1,if(purchase) "Recepción de compra" else "Confirmación de venta",owner,c.target) }
        if(purchase) {
            val reserved=budget!!.getLong("reservedCents")?:0
            val spent=budget.getLong("spentCents")?:0
```

“Necesitamos una orden APROBADA. Leemos su presupuesto y todos los productos antes de escribir. Cada movimiento llama a BusinessRules.stock para calcular un nuevo stock válido.”

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:188>)**, línea 188. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    private fun movement(id:String,p:String,name:String,type:String,before:Long,quantity:Long,expected:Long,reason:String,owner:String,source:String): Map<String,Any> = mapOf(
        "productId" to p,"productName" to name,"type" to type,"quantity" to quantity,"beforeStock" to before,
        "afterStock" to BusinessRules.stock(type,before,quantity,expected),"reason" to reason,"actor" to owner,"source" to source,
        "operationId" to id,"createdAt" to FieldValue.serverTimestamp())
```

**Abrimos [BusinessRules.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/BusinessRules.kt:46>)**, línea 46. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun stock(type: String, current: Long, quantity: Long, expected: Long = -1): Long {
        require(current in 0..1_000_000) { "Stock actual inválido." }
        require(quantity in (if (type == "AJUSTE") 0L else 1L)..1_000_000L) { "Cantidad inválida." }
        val next = when(type) {
            "ENTRADA", "COMPRA" -> Math.addExact(current, quantity)
            "SALIDA", "VENTA" -> Math.subtractExact(current, quantity)
            "AJUSTE" -> { require(expected == current) { "El stock cambió. Actualiza antes de ajustar." }; quantity }
            else -> error("Movimiento inválido.")
        }
        require(next in 0..1_000_000) { "Stock insuficiente o fuera del límite permitido." }
        return next
    }
}
```

“Con los resultados válidos, trasladamos la reserva al importe recibido y registramos quién recibió, cuándo, las líneas y el documento. No estamos registrando un pago.”

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:144>)**, línea 144. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            val reserved=budget!!.getLong("reservedCents")?:0
            val spent=budget.getLong("spentCents")?:0
            val total=d.getLong("totalCents")!!
            require(reserved>=total) { "La reserva presupuestal no está disponible." }
            tx.update(budgetRef!!,mapOf("reservedCents" to reserved-total,"spentCents" to spent+total,"lastOperation" to id,"lastOrder" to c.target,"updatedAt" to FieldValue.serverTimestamp()))
            tx.set(db.collection("recepciones_v2").document(c.target),mapOf("orderId" to c.target,"operationId" to id,"receivedBy" to owner,"createdAt" to FieldValue.serverTimestamp(),"document" to c.receiptDocument.trim(),"notes" to c.receiptNotes.trim(),"lines" to lines.map(::lineMap)))
        }
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:152>)**, línea 152. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        tx.update(ref,mapOf("status" to (if(purchase) "RECIBIDA" else "FACTURADO"),"lastOperation" to id,"updatedAt" to FieldValue.serverTimestamp()) + if(purchase) mapOf("receivedBy" to owner,"receivedAt" to FieldValue.serverTimestamp(),"receiptDocument" to c.receiptDocument.trim(),"receiptNotes" to c.receiptNotes.trim()) else emptyMap())
        movements.forEach { m -> writeMovement(tx,id,m) }
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:192>)**, línea 192. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    private fun writeMovement(tx:Transaction,id:String,m:Map<String,Any>) {
        val key=id+"_"+m["productId"]
        tx.set(db.collection("movimientos_v2").document(key),m)
        tx.update(db.collection("productos").document(m["productId"] as String),mapOf("stock" to m["afterStock"],"lastMovement" to key,"updatedAt" to FieldValue.serverTimestamp()))
    }
```

“writeMovement guarda la entrada y actualiza stock con el mismo identificador de operación. Todo esto está dentro de la transacción de execute, junto con su auditoría. Las reglas comprueban los vínculos; si alguna escritura no es válida, no se confirma parcialmente el inventario.”

**Resultado:** orden RECIBIDA, recepción en recepciones_v2, stock 15, movimiento COMPRA por 5, reserva cero y recibido S/118. El worker marca ENVIADO la tarea comercial. La compra ya está recibida aunque la foto siga esperando.

## 13. Subimos el respaldo y lo vinculamos — intervención HU09

“Seguimos con la segunda tarea de la cola, PURCHASE_PHOTO. Esta rama no llama a fulfill; por tanto no vuelve a generar un movimiento de stock.”

**Abrimos [PendingSyncWorker.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:33>)**, línea 33. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
                    "PURCHASE_PHOTO" -> koin.get<PurchaseEvidenceRepository>().upload(operation.id,owner,Gson().fromJson(operation.payload,PurchasePhoto::class.java))
```

**Abrimos [PurchaseEvidenceRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/PurchaseEvidenceRepository.kt:14>)**, línea 14. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun upload(id:String,owner:String,c:PurchasePhoto) {
        require(services.auth.currentUser?.uid==owner){"Inicia sesión con la cuenta que registró la recepción."}
        val dependency=dao.operation(c.receiptOperation,owner)
        check(dependency==null || dependency.status=="ENVIADO"){"Primero confirma la recepción pendiente. Luego reintenta la foto; no registres otra entrada de stock."}
        val order=services.database.collection("recepciones_v2").document(c.orderId)
        val receipt=order.get(Source.SERVER).await()
        require(receipt.getString("receivedBy")==owner && receipt.getString("operationId")==c.receiptOperation){"La foto no corresponde a esta recepción."}
        val meta=order.collection("adjuntos").document(id)
        if(meta.get(Source.SERVER).await().exists())return
        val file=File(c.imagePath).canonicalFile
        val directory=File(context.filesDir,"documentos").canonicalFile
        require(file.toPath().startsWith(directory.toPath()) && file.isFile && file.length() in 1 until 10*1024*1024){"La fotografía no está disponible o supera 10 MB. La recepción conserva su registro."}
```

“Verificamos la cuenta, el estado de su recepción local si aún existe y, de manera definitiva, la recepción remota con el mismo usuario y operationId. Si ya guardamos metadatos de esta foto retornamos sin repetirlos. Comprobamos que el archivo pertenece al directorio interno permitido y no supera 10 MB.”

**Abrimos [PurchaseEvidenceRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/PurchaseEvidenceRepository.kt:26>)**, línea 26. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
        val path="compras/${c.orderId}/$id.jpg"
        val storage=FirebaseStorage.getInstance().apply{maxUploadRetryTimeMillis=60_000;maxOperationRetryTimeMillis=30_000}
        storage.reference.child(path).putFile(Uri.fromFile(file),StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        check(services.auth.currentUser?.uid==owner){"La sesión cambió. Reintenta la foto con la cuenta original."}
        services.database.runTransaction { tx->
            if(!tx.get(meta).exists()) tx.set(meta,mapOf("owner" to owner,"storagePath" to path,"receiptOperation" to c.receiptOperation,"createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }
```

“putFile sube el JPEG a Storage. Después guardamos su ruta en la subcolección adjuntos de esa recepción. Storage contiene el archivo; Firestore contiene el vínculo y datos de auditoría. Son dos servicios y esta subida no forma parte de la transacción de inventario.”

**Resultado:** la segunda tarea termina ENVIADO si archivo y metadatos se confirmaron. Si falla, reintentamos la foto conservando su UUID. No reenviamos la compra. Si la recepción falló primero, hay que corregir y reintentar esa recepción antes de volver a intentar su foto.

## 14. Consultamos el respaldo desde la compra recibida — HU08 con HU09

“Volvemos al detalle de la recibida y pulsamos Consultar foto de la entrega. La acción pide el archivo al repositorio. Si hay copia en caché de esta cuenta, la muestra; si no, consulta el vínculo en Firestore y descarga la imagen desde Storage.”

**Abrimos [PurchasePhotoCapture.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePhotoCapture.kt:55>)**, línea 55. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
@Composable internal fun PurchaseEvidenceButton(orderId:String){
    val repository:PurchaseEvidenceRepository=koinInject();val scope=rememberCoroutineScope()
    var path by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    OutlinedButton(onClick={busy=true;message="";scope.launch{try{path=repository.download(orderId).absolutePath}catch(e:Exception){if(e is CancellationException)throw e;message=userMessage(e)}finally{busy=false}}},enabled=!busy){Text(if(busy)"Buscando fotografía…" else "Consultar foto de la entrega")}
    Notice(message)
    if(path.isNotBlank())AlertDialog(onDismissRequest={path=""},title={Text("Documento de entrega")},text={PurchaseImage(path)},confirmButton={TextButton(onClick={path=""}){Text("Cerrar")}})
}
```

**Abrimos [PurchaseEvidenceRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/PurchaseEvidenceRepository.kt:34>)**, línea 34. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    suspend fun download(orderId:String):File {
        val uid=services.auth.currentUser?.uid?:error("Inicia sesión.")
        require(orderId.matches(Regex("[a-f0-9-]{36}")))
        val dir=File(context.cacheDir,"recepciones/$uid").apply{mkdirs()}
        val file=File(dir,"$orderId.jpg")
        if(file.isFile)return file
        val records=services.database.collection("recepciones_v2").document(orderId).collection("adjuntos").limit(1).get(Source.SERVER).await()
        val path=records.documents.firstOrNull()?.getString("storagePath")?:error("No hay foto confirmada. Si la adjuntaste, revisa su envío en Pendientes.")
        require(path.matches(Regex("compras/$orderId/[a-f0-9-]{36}[.]jpg")))
        val temp=File(dir,"$orderId.tmp")
        try {
            FirebaseStorage.getInstance().reference.child(path).getFile(temp).await()
            require(temp.length() in 1 until 10*1024*1024)
            check(services.auth.currentUser?.uid==uid){"La sesión cambió."}
            temp.copyTo(file,overwrite=true)
        }finally{temp.delete()}
        return file
    }
```

**Abrimos [PurchasePhotoCapture.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePhotoCapture.kt:51>)**, línea 51. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
@Composable private fun PurchaseImage(path:String){
    val bitmap=remember(path){android.graphics.BitmapFactory.decodeFile(path,android.graphics.BitmapFactory.Options().apply{inSampleSize=2})}
    if(bitmap!=null)Image(bitmap.asImageBitmap(),"Documento de entrega",Modifier.fillMaxWidth().height(200.dp),contentScale=ContentScale.Fit)
}
```

“El resultado vuelve a Compose como una imagen dentro del diálogo. No usamos un enlace público permanente. Si la foto aún no terminó de enviarse, el mensaje explica que consultemos sus pendientes.”

## 15. Comprobamos el cierre en inventario — conexión HU07

“Cerramos el recorrido consultando existencias y movimientos. El inventario no depende de que el trabajador agregue otra entrada: la recepción ya la generó. Al actualizar productos, el ViewModel consulta el repositorio y publica el stock confirmado.”

**Abrimos [ProductViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/products/ProductViewModel.kt:19>)**, línea 19. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    fun refresh() = action { mutable.value = mutable.value.copy(products = repo.products()) }
```

**Abrimos [FirestoreCommerceRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreCommerceRepository.kt:14>)**, línea 14. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    override suspend fun products(): List<Product> {
        val owner = services.auth.currentUser?.uid ?: error("Inicia sesión.")
        if (!cache.online) return cache.read<List<Product>>(owner, "products", object : TypeToken<List<Product>>() {}.type)
            ?: error("No hay productos guardados. Consulta el catálogo una vez con internet.")
        return try {
            services.database.collection("productos").get(Source.SERVER).await().documents.map { d ->
                Product(d.id, d.getString("nombre").orEmpty(), d.getString("categoria").orEmpty(), d.getLong("precioCentimos") ?: 0,
                    d.getLong("stock") ?: 0, d.getLong("stockMinimo") ?: 0)
            }.sortedBy { it.name }.also { cache.save(owner, "products", it) }
        } catch (e: Exception) {
            if (e is CancellationException || !networkFailure(e)) throw e
```

**Abrimos [FirestoreBusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:43>)**, línea 43. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    override suspend fun movements(): List<StockMovement> = cached("v2-movements") {
        db.collection("movimientos_v2").get(Source.SERVER).await().documents.map { d ->
            gson.fromJson(gson.toJson(d.data.orEmpty().filterKeys { it != "createdAt" }), StockMovement::class.java)
                .copy(id=d.id, createdAt=d.getTimestamp("createdAt")?.toDate()?.time ?: 0)
        }.sortedByDescending { it.createdAt }
    }
```

“En movimientos vemos antes 10, entrada 5, después 15 y la orden de origen. La foto es respaldo adicional; su envío pendiente no resta unidades ni exige volver a registrar la compra.”

## 16. Explicamos desconexión y errores sin confundir estados

“Ahora distinguimos el estado comercial del estado de envío. Por aprobar/Por recibir/Recibida describe la orden. BORRADOR/PENDIENTE/ENVIADO/ERROR describe una tarea local. Una orden recibida puede tener foto con ERROR sin que la recepción haya fallado.”

**Abrimos [PendingSyncWorker.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:39>)**, línea 39. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val transient = networkFailure(e) && operation.attempts < 7
                dao.status(operation.id, owner, if (transient) "PENDIENTE" else "ERROR", userMessage(e), operation.attempts + 1)
                if (!transient && services.auth.currentUser?.uid == owner) pe.edu.unmsm.siderurgica.notifications.AppNotifications.show(applicationContext, operation.id, "No se pudo sincronizar", "Revisa la operación y su motivo en Borradores.")
                retry = retry || transient
            }
        }
        return if (retry || dao.ready(owner).isNotEmpty()) Result.retry() else Result.success()
    }
```

**Abrimos [DraftViewModel.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/drafts/DraftViewModel.kt:16>)**, línea 16. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```kotlin
    init { viewModelScope.launch { repo.observe().collect { mutable.value = mutable.value.copy(operations = it) } } }
    fun send(id: String) = action { repo.enqueue(id); mutable.value = mutable.value.copy(message = "Envío programado. Requiere conexión y una sesión de esta cuenta.") }
    fun delete(id: String) = action { repo.delete(id) }
    fun sync() { repo.schedule(); mutable.value = mutable.value.copy(message = "Sincronización programada; Android decidirá cuándo ejecutarla según la red.") }
```

“Las fallas temporales de red se reintentan con espera creciente y un máximo de ocho intentos por tarea según este contador; no prometemos reintentos infinitos. Un error definitivo requiere revisión. El borrador necesita enviarse explícitamente. Si borro datos o desinstalo, no debo esperar recuperar fotos locales aún no subidas.”

## 17. Mostramos dónde se comprueban los permisos

“Los botones reflejan el rol, pero la seguridad no termina en la interfaz. Las reglas de Firestore y Storage vuelven a validar cada operación. ADMIN configura/aprueba; ADMIN o ALMACEN reciben. El respaldo solo lo sube quien registró esa recepción, y ambas áreas autorizadas pueden consultarlo.”

**Abrimos [firestore.rules](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/firestore.rules:323>)**, línea 323. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```text
    match /compras_v2/{id} {
      allow read: if roleIn(['ADMIN','ALMACEN']);
      allow create: if roleIn(['ADMIN','ALMACEN']) && id==request.resource.data.lastOperation && v2Base(request.resource.data,true,false)
        && request.resource.data.clientId=='' && request.resource.data.reference==''
        && request.resource.data.ruc==request.resource.data.supplierRuc
        && request.resource.data.client==get(/databases/$(database)/documents/proveedores/$(request.resource.data.supplierRuc)).data.nombre
        && request.resource.data.budgetCents==0
        && request.resource.data.budgetPeriod.matches('^[0-9]{4}-(0[1-9]|1[0-2])$')
        && request.resource.data.deliveryDate.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
        && request.resource.data.supplierQuote is string && request.resource.data.supplierQuote.size()>=3 && request.resource.data.supplierQuote.size()<=120;
      allow update: if (roleIn(['ADMIN']) && v2Decision(request.resource.data,resource.data,false)
          && ((resource.data.status=='PENDIENTE' && request.resource.data.status in ['RECHAZADA','CANCELADA'])
            || (resource.data.status=='PENDIENTE' && request.resource.data.status=='APROBADA' && purchaseBudgetChange(request.resource.data,resource.data,id))
            || (resource.data.status=='APROBADA' && request.resource.data.status=='CANCELADA' && purchaseBudgetChange(request.resource.data,resource.data,id))))
        || (roleIn(['ADMIN','ALMACEN']) && resource.data.status=='APROBADA' && request.resource.data.status=='RECIBIDA' && purchaseReceipt(request.resource.data,resource.data,id));
      allow delete: if false;
    }
    function budgetLinked(d,b) {
      let old=get(/databases/$(database)/documents/compras_v2/$(d.lastOrder)).data;
      let order=getAfter(/databases/$(database)/documents/compras_v2/$(d.lastOrder)).data;
      return order.budgetPeriod==b && order.lastOperation==d.lastOperation && order.status!=old.status
        && purchaseBudgetChange(order,old,d.lastOrder)
```

**Abrimos [firestore.rules](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/firestore.rules:377>)**, línea 377. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```text
    match /recepciones_v2/{orderId}/adjuntos/{id} {
      allow read: if roleIn(['ADMIN','ALMACEN']);
      allow create: if roleIn(['ADMIN','ALMACEN']) && id.matches('^[a-f0-9-]{36}$')
        && request.resource.data.keys().hasOnly(['owner','storagePath','receiptOperation','createdAt'])
        && request.resource.data.owner==request.auth.uid && request.resource.data.createdAt==request.time
        && request.resource.data.storagePath=='compras/'+orderId+'/'+id+'.jpg'
        && get(/databases/$(database)/documents/recepciones_v2/$(orderId)).data.receivedBy==request.auth.uid
        && request.resource.data.receiptOperation==get(/databases/$(database)/documents/recepciones_v2/$(orderId)).data.operationId;
      allow update,delete: if false;
    }
```

**Abrimos [storage.rules](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/storage.rules:26>)**, línea 26. Tajada exacta del archivo; si está recortada, el método continúa en su fuente.

```text
    function purchaseAccess() {
      return request.auth!=null && request.auth.token.email_verified==true
        && firestore.get(/databases/(default)/documents/usuarios/$(request.auth.uid)).data.activo==true
        && firestore.get(/databases/(default)/documents/usuarios/$(request.auth.uid)).data.rol in ['ADMIN','ALMACEN'];
    }
    match /compras/{orderId}/{fileName} {
      allow read: if purchaseAccess();
      allow write: if purchaseAccess() && request.resource!=null
        && fileName.matches('^[a-f0-9-]{36}[.]jpg$')
        && firestore.get(/databases/(default)/documents/recepciones_v2/$(orderId)).data.receivedBy==request.auth.uid
        && request.resource.size<10*1024*1024 && request.resource.contentType=='image/jpeg';
    }
```

“Para profundizar en atomicidad abrimos también purchaseBudgetChange, purchaseReceipt, el bloque presupuestos_compras y v2Stock en firestore.rules. No basta enseñar un botón deshabilitado para demostrar estas restricciones.”

## 18. Cierre listo para leer

“Partimos de un proveedor, consultamos su RUC y revisamos sus datos antes de guardarlo. Registramos una orden con las condiciones acordadas externamente, que Administración aprueba contra un presupuesto compartido. Al recibir, almacén verifica cantidades y el documento; opcionalmente toma una fotografía aquí mismo y revisa el número sugerido. La recepción actualiza presupuesto, estado e inventario en una transacción. La foto tiene su propio envío y queda vinculada a la recepción, por lo que reintentar un respaldo no duplica stock. Terminamos con mercadería recibida, movimiento trazable y respaldo consultable cuando concluye su sincronización.”

## Paradas recomendadas en la demo

| Parada | Qué mostrar en app | Qué comprobar |
|---|---|---|
| 1 | Proveedor, consulta y guardado | Consulta API diferente de registro Firestore; proveedor único por RUC |
| 2 | Presupuesto S/1,000 y nueva compra S/118 | Por aprobar, sin variar stock ni reservar presupuesto |
| 3 | Aprobar compra | Por recibir, reservado S/118, disponible S/882, stock intacto |
| 4 | Generar/compartir PDF | Orden local; no factura del proveedor ni pago |
| 5 | Registrar entrega con foto | Cantidades humanas, número manual/sugerido revisable |
| 6 | Confirmación y pendientes | Una tarea comercial y otra documental; independientes |
| 7 | Detalle Recibida y fotografía | Documento y respaldo ligado a su recepción |
| 8 | Stock y movimientos | 10 + 5 = 15 una vez; orden de origen |

Si el proveedor ya existe, no hay que crearlo para cada compra. Si un producto existe con stock cero, se compra el mismo. Si es nuevo, un usuario autorizado lo registra con stock inicial cero y luego actualiza el catálogo antes de comprar; no se sustituye por uno similar ni se crea un duplicado solo por el nombre externo.

## Dónde encaja MVVM y qué herramienta cumple cada parte

| Capa/recurso | Uso real |
|---|---|
| View | SupplierScreen, PurchaseScreen, RecordForm y capturas Compose |
| ViewModel | SupplierViewModel y BusinessViewModel; StateFlow, validaciones y coordinación |
| Model/data | Comandos/modelos, reglas de dominio, contratos, repositorios, Room y Firebase |
| Retrofit + corutinas | Consulta RUC directa; no son el transporte de Firestore |
| Firebase Authentication | Sesión/UID de la operación y acceso por reglas con perfil activo/verificado |
| Firestore | Proveedor, orden, presupuesto, recepción, movimientos, auditoría y vínculos documentales |
| SQLite mediante Room | Pendientes, borradores y caché; rutas de las imágenes, no sus bytes |
| WorkManager | Espera red y procesa/reintenta tareas duraderas |
| ML Kit | OCR en dispositivo; parser local interpreta sugerencias |
| Storage | Fotografía JPEG de la entrega |
| PdfDocument / FileProvider | Generación local de PDF y compartición temporal |

Esta separación no implica que toda la lógica viva en un UseCase: parte de la coordinación y lógica transaccional está en los repositorios Firebase. No describir el proyecto como un servidor Spring dentro de Android.

## Límites que debes decir con precisión

- Hasta tres productos distintos por orden; no convertirlo en supuesta política de la empresa.
- Recepción completa: no hay entregas parciales ni devoluciones en este recorrido.
- No se implementan pagos, préstamos ni fabricación. Importe recibido en presupuesto no significa pagado.
- Cotización de proveedor ingresada manualmente por referencia; no se adjunta/importa su PDF ni se reconoce su tabla.
- La captura de recepción aplica solo el número reconocido si está vacío. No completa cantidades, precios, importes o verificaciones físicas.
- La integración demuestra una parte de HU09, no todos sus criterios automáticamente. El módulo documental separado tiene otros campos, pero no se consumen aquí.
- PDF de orden local; respaldo subido a Storage es JPEG, no PDF.
- La prueba de cámara/OCR en teléfono real y la consulta con credencial real corresponden a tu demo. Las validaciones automatizadas previas registran 34 tests Android y 36 de reglas; esta tarea solo documenta y verifica los fragmentos, no vuelve a ejecutar esas pruebas.

## Índice de archivos en orden de aparición

No se explican como archivos aislados: esta tabla sirve para tenerlos abiertos en el orden de la llamada. Un archivo repetido se visita varias veces con métodos distintos.

| Archivo | Primera tajada |
|---|---|

| SupplierScreen.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierScreen.kt:33>) |

| SupplierViewModel.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/suppliers/SupplierViewModel.kt:23>) |

| InputRules.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/InputRules.kt:18>) |

| RucApi.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/remote/RucApi.kt:19>) |

| AppModule.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/di/AppModule.kt:35>) |

| Supplier.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/model/Supplier.kt:3>) |

| SupplierRules.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/SupplierRules.kt:5>) |

| QueueRepository.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/QueueRepository.kt:15>) |

| LocalDatabase.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/LocalDatabase.kt:6>) |

| PendingSyncWorker.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/worker/PendingSyncWorker.kt:20>) |

| FirestoreSupplierRepository.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreSupplierRepository.kt:25>) |

| firestore.rules | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/firestore.rules:145>) |

| PurchaseScreen.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchaseScreen.kt:22>) |

| BusinessViewModel.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessViewModel.kt:25>) |

| FirebaseServices.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirebaseServices.kt:14>) |

| FirestoreBusinessRepository.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreBusinessRepository.kt:26>) |

| PurchaseRules.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/PurchaseRules.kt:15>) |

| BusinessScreen.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/BusinessScreen.kt:77>) |

| BusinessRules.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/BusinessRules.kt:25>) |

| SalesRules.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/SalesRules.kt:4>) |

| PurchasePdfActions.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePdfActions.kt:41>) |

| PurchasePdfRepository.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/PurchasePdfRepository.kt:38>) |

| PurchasePhotoCapture.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/business/PurchasePhotoCapture.kt:32>) |

| DocumentScanner.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/documents/DocumentScanner.kt:20>) |

| InvoiceParser.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/rules/InvoiceParser.kt:5>) |

| PurchasePhoto.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/model/PurchasePhoto.kt:2>) |

| PurchaseEvidenceRepository.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/PurchaseEvidenceRepository.kt:14>) |

| ProductViewModel.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/products/ProductViewModel.kt:19>) |

| FirestoreCommerceRepository.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/firebase/FirestoreCommerceRepository.kt:14>) |

| DraftViewModel.kt | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/screens/drafts/DraftViewModel.kt:16>) |

| storage.rules | [Abrir código](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/storage.rules:26>) |

## Archivos de apoyo para preguntas del equipo

Los modelos `Business.kt`, `Supplier.kt` y `PurchasePhoto.kt` definen los datos; `BusinessRepository.kt` es el contrato; `DeviceCache.kt` implementa caché por usuario; `SearchableSelector.kt` filtra las opciones cargadas; `AndroidManifest.xml` y `res/xml/file_paths.xml` configuran FileProvider; `ui/common/ErrorMessages.kt` traduce errores a mensajes; `notifications/AppNotifications.kt` avisa del resultado de sincronización. No es necesario recorrerlos completos si no intervienen en la explicación que estés mostrando.

- [Business.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/model/Business.kt:1>)

- [BusinessRepository.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/domain/repository/BusinessRepository.kt:1>)

- [DeviceCache.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/data/local/DeviceCache.kt:1>)

- [SearchableSelector.kt](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/java/pe/edu/unmsm/siderurgica/ui/common/SearchableSelector.kt:1>)

- [AndroidManifest.xml](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/AndroidManifest.xml:1>)

- [file_paths.xml](<C:/Users/Usuario/Desktop/Ejercicios Progra/Ejercicios Kotlin/SiderurgicaFirebase_Sprints_1_2_3/SiderurgicaFirebase/app/src/main/res/xml/file_paths.xml:1>)
