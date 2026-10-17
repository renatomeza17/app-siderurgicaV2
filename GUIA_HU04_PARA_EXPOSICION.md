> Consulta RUC vigente: directa desde Android con Retrofit/corutinas, sin campo de token en pantalla. Configura `DECOLECTA_TOKEN` en `local.properties` y recompila. No requiere Cloud Run. Consulta `CONFIGURACION_CONSULTA_RUC.md` para pasos y límites del modo académico.

> Selección principal: consulta GUIA_HISTORIA_PARA_EXPOSICION.md para HU05. Esta guía conserva la alternativa de cotizaciones (HU04) y su rama de API externa.

# Historia recomendada para la exposición: HU04 — Registrar y consultar cotizaciones

La HU04 es una alternativa si puedes demostrar también la consulta API real: tiene una entrada visible, selección de cliente/productos, precios de catálogo, descuentos automáticos, cálculo de IGV, validación, operación local duradera, sincronización, reglas Firebase y consulta final con búsqueda/filtros/detalle. Permite recorrer MVVM y persistencia sin mezclar toda la historia con pedidos, ventas o pagos.

Está implementada en código. Para presentarla como aceptada en tu entorno, realiza una prueba completa en el teléfono y publica las reglas Firestore actualizadas. El criterio REST de HU04 se muestra mediante la consulta RUC del alta de cliente con Retrofit/corrutinas; requiere un token personal y conexión. El registro manual no necesita ese token. No presentes una prueba MockWebServer como si fuera una consulta real.

## Recorrido de ejecución para abrir el código en orden

| Paso | Archivo relativo a app/src/main/java/pe/edu/unmsm/siderurgica | Punto que debes mostrar |
|---|---|---|
| 1 | navigation/AppNavigation.kt | La ruta quotes verifica el rol y abre BusinessScreen; Koin aporta el ViewModel |
| 2 | ui/screens/business/BusinessScreen.kt | BusinessScreen dirige el módulo comercial a CommercialScreen |
| 3 | ui/screens/business/CommercialScreen.kt | Listado, Nueva cotización, formulario separado y detalle; collectAsStateWithLifecycle y refresh |
| 4 | ui/screens/business/BusinessViewModel.kt | refresh carga catálogo con commerce.products(), clientes con repo.clients() y cotizaciones con repo.records("quotes") |
| 5 | firebase/FirestoreCommerceRepository.kt | products consulta productos en Firestore y conserva la copia por cuenta |
| 6 | firebase/FirestoreBusinessRepository.kt | clients y records consultan clientes_v2/cotizaciones_v2; cached utiliza SQLite cuando falta red |
| 7 | ui/screens/business/BusinessScreen.kt | RecordForm selecciona IDs, agrega líneas y prepara BusinessCommand("QUOTE_CREATE",...) |
| 8 | ui/common/SearchableSelector.kt | Campo compacto y lista de búsqueda con LazyColumn; conserva el ID, no un texto arbitrario |
| 9 | domain/rules/BusinessRules.kt | automaticDiscount, lines y total: catálogo, cantidades, productos distintos, descuentos e IGV en céntimos |
| 10 | ui/screens/business/BusinessViewModel.kt | submit vuelve a validar y delega el guardado a queue.save("BUSINESS",...) |
| 11 | data/local/QueueRepository.kt | Guarda UUID, propietario y payload en Room; programa trabajo con conexión requerida |
| 12 | data/local/LocalDatabase.kt | PendingOperation y LocalDao.insert/ready/status; persistencia SQLite y separación por UID |
| 13 | worker/PendingSyncWorker.kt | Recupera la operación, comprueba la sesión y llama BusinessRepository.execute con el mismo ID |
| 14 | firebase/FirestoreBusinessRepository.kt | execute comprueba el registro de operación y create consulta cliente/productos, valida precio y escribe la cotización dentro de una transacción |
| 15 | firestore.rules, en la raíz del proyecto | roleIn, v2Line/v2Base y cotizaciones_v2: permisos, política de descuento y cálculos también se protegen fuera de la UI |
| 16 | worker/PendingSyncWorker.kt | Marca ENVIADO tras confirmación o ERROR/PENDIENTE según la causa y los reintentos |
| 17 | ui/screens/business/CommercialScreen.kt | Actualizar listado vuelve a consultar la cotización; filtrar, buscar y abrir el detalle muestran el resultado |

FirebaseServices.kt proporciona FirebaseAuth/FirebaseFirestore del proyecto; di/AppModule.kt enlaza las interfaces con sus repositorios e inyecta las dependencias. Puedes mostrarlos al introducir el primer acceso a Firebase: el SDK se configura con google-services.json; no se llama al ERP de Render.

No uses QuoteScreen.kt, QuoteViewModel.kt ni CommerceRepository.createQuote como si fueran el flujo nuevo: corresponden al formato histórico de una línea. El flujo nuevo usa BusinessScreen/CommercialScreen, BusinessViewModel, la operación BUSINESS y cotizaciones_v2.

## Rama de API externa de la misma historia

Desde Nueva cotización → Registrar otro cliente → Consultar razón social por RUC:

ClientRegistrationPage (CommercialScreen.kt) → BusinessViewModel.lookupClient → RucLookup.lookup (data/remote/RucApi.kt) → RucApi.lookup, GET con Retrofit y función suspend → mapeo/validación de la respuesta → StateFlow lookupName → revisión del nombre en el formulario → guardar cliente → cola SQLite/WorkManager → clientes_v2.

La consulta API no guarda automáticamente el cliente ni guarda el token en Firebase. El expositor revisa los datos y confirma el registro. No muestres un token en la presentación.

## Demo concreta de una sola historia (aproximadamente 6–8 minutos)

1. Ingresa como VENTAS o ADMIN. Ten un cliente sincronizado y dos productos de demostración: Acero a S/ 100 y Tubo a S/ 50. Para esta historia no necesitas confirmar ninguna venta.
2. Abre Cotizaciones: explica que es una propuesta y que crearla no descuenta stock ni demuestra pago.
3. Pulsa Nueva cotización. Elige cliente mediante el selector y busca Acero; agrega 10 unidades. La app aplica 5 % sin permitir editarlo.
4. Busca Tubo y agrega 2 unidades: 0 %. Los importes esperados son S/ 1.050 de subtotal neto, S/ 189 de IGV y S/ 1.239 de total.
5. Si necesitas demostrar el criterio de API externa, registra antes un cliente por consulta RUC con token propio, espera ENVIADO y actualiza clientes. Si aún no tienes token, demuestra el alta manual y declara la consulta externa pendiente de prueba real.
6. Guarda y sincroniza. Salta a la cola SQLite y muestra por qué el mensaje inicial es PENDIENTE, no una confirmación remota.
7. Abre Borradores y espera ENVIADO. Actualiza Cotizaciones y muestra búsqueda, filtro Esperando respuesta y detalle con las mismas cantidades e importes.
8. Como extensión opcional: abre el detalle, genera y comparte el PDF de la propuesta. Esta ampliación no es la boleta/factura de HU06.
9. Como extensión de HU03: repite otra propuesta sin red, cierra/reabre sin cerrar sesión ni borrar datos, comprueba el pendiente y recupera conexión. La ejecución depende de WorkManager; espera su confirmación.

## Guion hablado breve siguiendo el hilo

“Parto de la solicitud de un cliente al vendedor. En la pantalla de cotizaciones recibo sus productos y cantidades; los selectores trabajan con registros ya cargados, identificados por su ID. Cuando abro la vista, el ViewModel solicita clientes y catálogo a los repositorios. Ellos consultan Firestore y conservan una copia por usuario en SQLite.

Al agregar una línea, la regla del dominio toma el precio del catálogo y calcula el descuento por cantidad. Con diez unidades aplica cinco por ciento; yo no escribo un porcentaje arbitrario. Con los importes netos calculamos IGV y total en céntimos, antes de enviar.

Cuando confirmo, el ViewModel valida otra vez y guarda un comando con un identificador estable en Room. Por eso puedo conservarlo aunque desaparezca internet. WorkManager espera conexión, verifica qué cuenta está activa y pasa el mismo comando al repositorio Firebase.

Aquí salto a la transacción: primero comprobamos si este identificador ya fue procesado. Si es nuevo, leemos el cliente y los productos vigentes, verificamos precios y calculamos los importes. Guardamos la cotización junto con el registro de la operación. Las reglas de Firestore también exigen el rol adecuado y la política de descuentos: ocultar un campo en pantalla no basta para proteger el negocio.

Solo después de confirmar Firebase marcamos ENVIADO. Vuelvo al listado, actualizo y encuentro mi propuesta con los mismos importes. Puedo filtrarla, consultar el detalle y compartir una copia PDF con el cliente. En este punto terminé la historia de cotización; aceptar la propuesta, crear un pedido y confirmar una venta son pasos posteriores.”

## Otras historias que puedes escoger

| Historia | Ventaja para exponer | Condición que debes comprobar |
|---|---|---|
| HU03 — Offline y sincronización | SQLite, ciclo de vida, WorkManager, idempotencia, confirmación y errores | Demo real de pérdida/recuperación de red; no presentar la cola como envío instantáneo |
| HU05 — Pedido y venta | La transacción de venta, stock de varios productos y prevención de sobreventa forman un flujo sólido | Tener pedido validado; mostrar que aprobar no es confirmar pago y que el stock cambia al confirmar venta |
| HU06 — Comprobante PDF | Resultado visual claro: venta confirmada → PDF → guardar/compartir → Storage | Bucket/reglas listos y visor Android; es comprobante académico, sin emisión SUNAT |
| HU08 — Proveedores y compra | RUC, presupuesto, recepción y actualización atómica de inventario | Tener proveedor/productos y demostrar recepción única, no solo registrar proveedor |
| HU09 — Cámara y OCR | Recurso móvil, reconocimiento, revisión humana y archivo en Storage | Probar cámara/ML Kit/subida en teléfono; la extracción no es infalible |
| HU02 — Acceso y biometría | Auth, perfil, roles y protección local | Verificación de correo y huella física compatibles |

No escogería HU07 para afirmar el flujo completo si todavía no desplegaste y probaste el emisor FCM. El aviso local de inventario no demuestra una entrega push. Tampoco conviene presentar todo cotización→pedido→venta como si fuera una sola historia: HU04 tiene por sí misma un principio y un cierre claros.
