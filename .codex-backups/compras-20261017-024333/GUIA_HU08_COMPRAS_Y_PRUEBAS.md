# HU08 — Compras y recepción conectadas al inventario HU07

## Antes de probar

1. Abre esta misma carpeta en Android Studio y ejecuta la versión actualizada. No hace falta otro ZIP.
2. Publica **todo `firestore.rules` de esta carpeta** en Firebase Console → Firestore Database → Reglas. No mezcles fragmentos con las reglas antiguas. No hemos publicado reglas ni escrito datos en tu Firebase real.
3. Usa una cuenta verificada con perfil `activo: true`, rol `ADMIN`. Para probar separación de tareas usa otra cuenta verificada `ALMACEN`. `VENTAS` no autoriza compras ni administra presupuesto.
4. Debe existir al menos un proveedor sincronizado y un producto. Si no tienes token de consulta RUC puedes registrar el proveedor manualmente con RUC válido; la consulta Decolecta sigue siendo opcional y no se reconfiguró en este cambio.
5. Actualiza los listados después de que Borradores indique ENVIADO. Guardar en cola no confirma aún el estado comercial.

## Recorrido de prueba principal

Ejemplo sin descuentos de compra: producto con stock inicial 10; comprar 5 unidades a S/ 20.00 **sin IGV**. Subtotal S/ 100.00, IGV S/ 18.00, total S/ 118.00.

1. En Stock, verifica y anota el stock inicial. Usa Bajo o Agotado para mostrar la necesidad de reposición. Pulsa **Reponer mediante orden de compra** o entra a Compras desde Más.
2. Como ADMIN, entra a **Administrar presupuesto**. Consulta el período actual (AAAA-MM) y guarda un total autorizado de S/ 1,000.00. Espera ENVIADO en Borradores y actualiza compras. Disponible: S/ 1,000.00; reservado: cero; recibido: cero.
3. Pulsa **Nueva orden de compra**. Selecciona proveedor y producto. El precio de compra empieza vacío: escribe 20.00, cantidad 5 y agrega la línea. Indica referencia `COT-PROV-001` y una fecha real AAAA-MM-DD. Guarda y sincroniza.
4. Tras ENVIADO, actualiza. La orden figura **Pendiente de autorización**. Stock sigue 10 y disponible sigue S/ 1,000.00: crear no consume presupuesto ni inventario.
5. Abre el detalle como ADMIN. Escribe sustento `Reposición de mercadería autorizada` y pulsa **Autorizar y reservar presupuesto**. Confirma, espera ENVIADO y actualiza. Estado Aprobada, reservado S/ 118.00, disponible S/ 882.00. Stock sigue 10.
6. Abre el detalle aprobado. Genera, visualiza, guarda o comparte el **PDF de orden de compra**. El envío al proveedor es manual por correo/WhatsApp. La aplicación no envía al proveedor automáticamente ni demuestra que haya aceptado o recibido un pago.
7. Como ALMACEN o ADMIN, abre **Comprobar y recibir mercadería**. Marca cada producto solo después de verificar toda su cantidad. Ingresa factura o guía, por ejemplo `F001-00123`, y observaciones opcionales.
8. Confirma la recepción, espera ENVIADO y actualiza. Orden Recibida, stock 15. Presupuesto: reservado cero, recibido S/ 118.00, disponible S/ 882.00. Recibir convierte la reserva en consumo del límite; no descuenta dos veces ni registra pago.
9. En Movimientos verifica una entrada COMPRA por 5, antes 10, después 15 y el código de la orden de origen. En el detalle de la orden consulta documento, usuario receptor y fecha confirmada por Firebase.
10. Ya no aparece la acción de recibir. No agregues una entrada manual por esa misma compra. Reintentar la operación original conserva su identificador y no suma stock nuevamente.

## Pruebas alternativas

- **Presupuesto insuficiente:** configura S/ 100.00 en otro período sin compromisos y crea una orden de S/ 118.00. Se puede registrar la propuesta, pero autorizar falla y no aumenta stock ni reserva fondos. Revisa el motivo en Borradores. ADMIN puede ampliar el presupuesto y reintentar la autorización fallida.
- **Cancelación:** autoriza otra orden y luego cancélala con sustento. Su reserva se libera una vez; no aumenta stock. Una recibida no se puede cancelar por esta vía; devoluciones son otro proceso.
- **Rechazo:** rechaza una orden pendiente. No modifica stock ni presupuesto. Historial conservado.
- **Roles:** ALMACEN crea órdenes y recibe las aprobadas, pero no ve acciones de autorización ni configuración de presupuesto. Las reglas bloquean esos cambios incluso si se intentan fuera de la interfaz.
- **Sin conexión:** carga antes los datos; activa modo avión y registra una orden o una recepción de una orden ya aprobada. Queda en SQLite y Borradores como PENDIENTE. Al reconectar, WorkManager reintenta. La confirmación remota aplica orden, recepción, presupuesto y movimientos conjuntamente. Si otro usuario canceló la orden, la recepción falla sin tocar stock.
- **Tres productos:** verifica que la recepción completa afecte todos los productos en una sola transacción. Si una línea excedería el límite de stock, ninguna debe confirmarse.
- **Entrega incompleta:** no marques todos los productos ni confirmes recepción completa. Este avance no registra recepciones parciales ni acumulaciones de entregas. No debe simularse una entrega completa.
- **Órdenes anteriores:** no se borran. Las pendientes sin período/cotización/entrega pueden cancelarse con ADMIN y reemplazarse por una nueva. Los borradores de compra antiguos deben recrearse si fallan por datos faltantes. Las órdenes históricas recibidas se consultan como historial; no se imputan retrospectivamente al presupuesto nuevo.

## Documentos y Storage

La recepción guarda **referencia textual de factura/guía**, observación, operador y fecha; no adjunta automáticamente un archivo. Documentos/OCR mantiene su captura, revisión y subida de imágenes a Storage como funcionalidad separada. Puedes guardar el mismo número como referencia, pero no existe todavía un vínculo automático entre ambos registros. El PDF de orden se genera localmente, se guarda o comparte; no se sube a Storage. No se modificaron las reglas de Storage.

## Flujo técnico para estudiar

1. `ui/screens/products/ProductScreen.kt`: consulta de existencias, filtros de stock y entrada a reposición (HU07).
2. `ui/screens/business/PurchaseScreen.kt`: lista, detalle, presupuesto, autorización y pantalla de recepción separadas. `RecordForm` en `BusinessScreen.kt` construye la orden con precio de compra independiente.
3. `ui/screens/business/BusinessViewModel.kt`: valida campos y delega guardado a la cola. No escribe inventario directamente.
4. `domain/rules/PurchaseRules.kt` y `BusinessRules.kt`: metadatos, presupuesto, precios, cantidades, IGV y límites de stock.
5. `domain/model/Business.kt` y `domain/repository/BusinessRepository.kt`: comandos, registros y contrato de persistencia.
6. `data/local/QueueRepository.kt`, `LocalDao.kt`, `LocalDatabase.kt`: SQLite mediante Room; operación particionada por usuario e identificador estable. No se cambió el esquema local.
7. `worker/PendingSyncWorker.kt`: WorkManager requiere red y ejecuta la operación con corutinas, registrando ENVIADO o ERROR.
8. `firebase/FirestoreBusinessRepository.kt`: `create` registra PENDIENTE; `purchaseState` autoriza/reserva o cancela/libera; `fulfill` registra recepción, consume reserva y actualiza inventario en una transacción. `execute` verifica el identificador en `operaciones_v2` para no repetir una operación ya confirmada.
9. `firestore.rules`: comprueba rol, transiciones, invariantes de presupuesto y vínculos entre orden, recepción, movimiento y producto. No basta con ocultar botones.
10. `data/documents/PurchasePdfRepository.kt` y `ui/screens/business/PurchasePdfActions.kt`: orden aprobada a PDF y compartición Android.
11. `ui/screens/drafts/DraftScreen.kt`: inspección de las operaciones locales y errores. Es independiente del estado comercial de cada orden.

Colecciones: `compras_v2`, `presupuestos_compras/{AAAA-MM}`, `recepciones_v2/{orderId}`, `movimientos_v2`, `productos`, `operaciones_v2`, `proveedores`, `usuarios`.

### Guion breve del recorrido

“Comenzamos en inventario: detectamos la necesidad de reponer un producto que distribuimos. Registramos una orden con el proveedor y el precio que acordamos fuera del sistema. Aquí no usamos el precio de venta ni modificamos stock. El ViewModel valida y conserva el comando en SQLite; WorkManager lo sincroniza con Firebase. Administración autoriza la orden y reserva presupuesto de manera transaccional. Podemos compartir el PDF con el proveedor. Cuando llega la mercadería, almacén verifica todas las cantidades y registra el documento de entrega. Al confirmar, Firestore guarda la recepción, cierra la orden y genera las entradas de inventario conjuntamente. Si se corta la red, la operación espera; si se reintenta después de confirmarse, su identificador evita que vuelva a aumentar el stock.”

## Herramientas y límites del avance

HU08 y su conexión HU07 usan Compose/Material, ViewModel/StateFlow, corutinas, Firebase Authentication para identidad, Firestore para datos/reglas/transacciones, Room/SQLite y WorkManager. Retrofit sigue en la consulta RUC opcional; no interviene en las transacciones Firestore. Storage/OCR pertenecen al flujo documental separado. No se implementaron pagos, préstamos, fabricación, devoluciones ni recepciones parciales. No se desplegó ni probó FCM remoto: la infraestructura existente de notificaciones conserva sus requisitos de despliegue. No se declara HU07 completamente terminada por este cambio.
