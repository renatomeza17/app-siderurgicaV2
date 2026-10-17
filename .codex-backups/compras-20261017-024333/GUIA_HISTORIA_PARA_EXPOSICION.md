# Historia principal recomendada: HU05 — Registrar pedidos y ventas

Esta historia es la mejor preparada para una exposición completa sin depender de un token de API externa. Su alcance es validar cliente/stock, crear y cambiar el estado del pedido, reintentar de forma segura y actualizar inventario al confirmar la venta. Está implementada en código y tiene pruebas de reglas/transacciones. Para declararla aceptada en tu entorno debes completar la demo con tu cuenta y teléfono; la compilación no sustituye esa aceptación.

Puedes comenzar con un pedido directo. La cotización es una entrada opcional de HU04; el PDF de venta es una extensión de HU06. No necesitas explicar tres historias como si fueran una sola.

## Recorrido del código en orden de ejecución

Los siguientes números de línea corresponden a esta carpeta actual. Sigue cada llamada al siguiente componente antes de volver al flujo:

1. **Pantalla y acceso:** `navigation/AppNavigation.kt:105` — La ruta comprueba canSell y abre BusinessScreen("orders"). El cliente comprador no utiliza esta app interna.

2. **Consulta y formulario:** `ui/screens/business/CommercialScreen.kt:27` — El listado y el formulario son vistas separadas. collectAsStateWithLifecycle recibe estado; refresh obtiene los datos de referencia.

3. **Carga de referencias:** `ui/screens/business/BusinessViewModel.kt:24` — El ViewModel pide catálogo a commerce.products(), clientes a repo.clients() y pedidos a repo.records("orders"). Salta a los repositorios para mostrar de dónde vienen.

4. **Productos y caché:** `firebase/FirestoreCommerceRepository.kt:14` — Consulta productos de Firestore con Source.SERVER; DeviceCache conserva la última copia por cuenta en Room.

5. **Clientes y pedidos:** `firebase/FirestoreBusinessRepository.kt:31` — clients consulta clientes_v2; records("orders") consulta pedidos_v2. cached sirve la copia local si no hay conexión.

6. **Formulario y selección:** `ui/screens/business/BusinessScreen.kt:89` — El vendedor elige IDs de cliente/productos, cantidades y ve descuentos automáticos, subtotal, IGV y total. command() prepara ORDER_CREATE.

7. **Selector reutilizable:** `ui/common/SearchableSelector.kt:19` — Un campo compacto abre búsqueda por nombre/código/RUC; LazyColumn acota la lista visible. La selección conserva el ID del registro.

8. **Reglas de negocio:** `domain/rules/BusinessRules.kt:25` — Valida productos distintos, cantidad, precio del catálogo y descuento por cantidad. total y tax trabajan con céntimos e IGV de 18 %.

9. **Confirmación de la pantalla:** `ui/screens/business/BusinessViewModel.kt:36` — Vuelve a validar el pedido y llama queue.save("BUSINESS",...). La pantalla todavía no afirma que existe un pedido remoto.

10. **Persistencia y programación:** `data/local/QueueRepository.kt:15` — Genera un UUID estable, guarda propietario y payload como PENDIENTE en Room y programa trabajo con NetworkType.CONNECTED.

11. **Base SQLite:** `data/local/LocalDatabase.kt:7` — La tabla pending conserva la operación ante un cierre; ready devuelve solo pendientes del propietario y status actualiza el resultado.

12. **Ejecución en segundo plano:** `worker/PendingSyncWorker.kt:16` — Comprueba UID, recupera el comando y delega BUSINESS a BusinessRepository.execute con el mismo ID. No saltes a CommerceRepository.createQuote: es el formato histórico.

13. **Crear pedido remoto:** `firebase/FirestoreBusinessRepository.kt:69` — execute comprueba primero operaciones_v2. create valida cliente/productos/precio/stock vigentes y crea pedidos_v2 PENDIENTE sin descontar inventario.

14. **Validar operativamente:** `firebase/FirestoreBusinessRepository.kt:104` — Desde el detalle se envía ORDER_STATE. state exige PENDIENTE, comprueba stock y guarda APROBADO con operador, fecha, canal INTERNO y sustento. No registra pagos ni reserva existencias.

15. **Confirmar venta:** `firebase/FirestoreBusinessRepository.kt:118` — SALE_CREATE exige pedido APROBADO y venta inexistente. Lee todos los productos antes de escribir; calcula movimientos VENTA y guarda venta, pedido FACTURADO, movimientos y stock en una transacción.

16. **Validar salida:** `domain/rules/BusinessRules.kt:46` — La rama VENTA resta cantidad y rechaza resultados negativos. Si un producto no alcanza, la transacción completa falla y no deja ventas ni descuentos parciales.

17. **Aplicar movimientos:** `firebase/FirestoreBusinessRepository.kt:143` — Registra antes/después, actor y referencia en movimientos_v2 y actualiza productos con lastMovement como vínculo de respaldo.

18. **Confirmación e interfaz:** `worker/PendingSyncWorker.kt:36` — Solo después de la confirmación remota marca ENVIADO. Vuelve a los listados, actualiza y comprueba pedido, venta e inventario.

## Conexión a Firebase y seguridad

Al mostrar el primer repositorio, abre `firebase/FirebaseServices.kt`: provee FirebaseAuth y FirebaseFirestore del proyecto configurado con google-services.json. `di/AppModule.kt` enlaza BusinessRepository con FirestoreBusinessRepository e inyecta servicios, caché, cola y ViewModel mediante Koin. El dominio no necesita conocer la consola de Firebase ni utiliza controladores Spring.

Antes del resultado remoto, muestra `firestore.rules` en la raíz: `roleIn`, `v2OrderCreate`, `v2Decision`, `ventas_v2`, `v2Stock` y `movimientos_v2`. Las reglas comprueban roles, precios, sumas, estados y el vínculo entre venta/movimientos/stock. La protección no depende solo de los controles de pantalla.

## Demo de aceptación

1. Publica las reglas Firestore actuales. Ingresa como VENTAS o ADMIN y prepara un cliente sincronizado.
2. Ten Acero a S/ 100 con stock 30 y Tubo a S/ 50 con stock 20.
3. Abre Pedidos → Nuevo pedido directo. Busca y selecciona el cliente; agrega 10 de Acero y 2 de Tubo. El descuento automático es 5 % para Acero y 0 % para Tubo.
4. Comprueba subtotal neto S/ 1.050, IGV S/ 189 y total S/ 1.239. Guarda y sincroniza; muestra la operación PENDIENTE en Borradores y espera ENVIADO.
5. Actualiza Pedidos. Abre el detalle PENDIENTE y pulsa Validar pedido. Registra un sustento operativo de prueba, confirma y espera ENVIADO. Actualiza: aparece Pedido validado. Stock sigue en 30 y 20.
6. Confirma venta, espera ENVIADO y actualiza. El pedido muestra Venta registrada; la venta está CONFIRMADA por S/ 1.239. Stock queda en 20 y 18. Consulta los movimientos VENTA.
7. Explica el reintento: la fila conserva su UUID y execute reconoce operaciones_v2. No crees otro pedido para simular el mismo reintento. Una venta ya confirmada tampoco puede volver a confirmar ese pedido.
8. Como prueba de error, intenta otro pedido por más unidades de las disponibles. La operación debe quedar ERROR, sin inventario negativo ni venta parcial. Consulta su mensaje en Borradores.
9. Si deseas demostrar HU03 como extensión, prepara otro pedido sin red, conserva la sesión, cierra/reabre normalmente y recupera conexión. Android administra el momento del envío; no prometas ejecución inmediata.

No cambies stock manualmente en la consola durante el recorrido principal. El control de cambios concurrentes se puede mostrar después como prueba adicional.

## Guion hablado breve

“Esta funcionalidad permite al vendedor registrar una solicitud de compra y convertirla en una venta consistente. Empiezo consultando clientes y catálogo desde el ViewModel. Los repositorios obtienen la información de Firestore y conservan una copia por usuario en SQLite.

En el formulario busco al cliente y los productos, seleccionando registros por ID. Introduzco cantidades; los precios salen del catálogo y los descuentos se calculan automáticamente. El dominio valida las líneas y calcula subtotal, IGV y total en céntimos.

Al confirmar no digo todavía que el pedido está guardado en la nube: primero persisto una operación con UUID en Room. WorkManager espera conexión y una sesión del propietario. Cuando ejecuta el comando, el repositorio comprueba si ese ID ya se procesó. Si es nuevo, valida nuevamente los datos vigentes y crea el pedido en Firestore.

La validación del pedido deja constancia de quién autorizó atenderlo y por qué. No certifica un pago y no descuenta stock. El paso decisivo ocurre cuando confirmo la venta: leemos todos los productos, verificamos disponibilidad y escribimos venta, estado del pedido y movimientos de inventario en una sola transacción. Si falta un producto, no confirmamos parcialmente los demás.

Las reglas Firebase también exigen los permisos y la coherencia de esas escrituras. Solo después de su confirmación marcamos la operación como enviada. Vuelvo a la interfaz y compruebo una venta por el mismo importe y el descuento exacto de existencias. Ese es el cierre de esta historia.”

## Cómo escoger otras historias

| Historia | Adecuación para exponer | Qué debes demostrar para darla por aceptada |
|---|---|---|
| HU05 — Pedidos y ventas | Primera recomendación por trazabilidad y transacción de inventario | Recorrido anterior con Firestore real; prueba negativa de stock |
| HU03 — Offline y sincronización | Muy sólida si quieres explicar SQLite, WorkManager y reintentos | Cierre/reapertura, reconexión, aislamiento de cuenta y confirmación sin duplicados |
| HU06 — PDF | Resultado visual fácil de demostrar; Storage ya puede configurarse | Generar, previsualizar, guardar/compartir, subir y descargar con tu bucket |
| HU08 — Proveedores y compras | Flujo completo de abastecimiento y recepción | Proveedor válido, presupuesto, orden y recepción que incrementa stock una vez |
| HU04 — Cotizaciones | Buena para interfaces, reglas e importes; guía alternativa incluida | Para cubrir todo el criterio de REST, consulta RUC real con token propio además del registro/listado |
| HU09 — Cámara/OCR | Buena si prefieres recurso móvil e IA | Captura real, corrección de datos, guardado y subida de imagen |

HU07 no debe presentarse como completada de extremo a extremo si todavía no se desplegó/probó el emisor FCM. El aviso local no sustituye el push.

## Cambio visual de esta entrega

Los formularios de cotización/pedido ahora usan selectores con búsqueda para clientes y productos. Compras usa los mismos selectores para proveedor y producto; Movimientos para producto. Se busca sobre el catálogo que ya fue cargado: no se añadieron consultas a Firestore por cada tecla. El listado visible utiliza LazyColumn y desplazamiento dentro de un cuadro acotado. No se eliminaron datos ni se modificaron reglas o configuración Firebase para este ajuste visual.

La compilación `:app:assembleDebug` pasó. Los selectores necesitan tu comprobación visual en el teléfono: abrir, escribir nombre o RUC, desplazarte, elegir una opción, cerrar y volver a abrir. No se añadieron pruebas de negocio porque esta modificación solo cambia la selección visual.
