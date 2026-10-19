> Para el recorrido completo desde proveedores e integración HU09, consulta [GUIA_EXPOSICION_COMPLETA_HU08_HU09.md](GUIA_EXPOSICION_COMPLETA_HU08_HU09.md). Incluye narración, código exacto y enlaces de fuentes.

# HU08 — Compras claras y recepción con respaldo opcional

## Qué se ingresa manualmente y dónde se usa la cámara

| Momento | Datos que registra el trabajador | Fotografía |
|---|---|---|
| Registrar proveedor | RUC, contacto y teléfono; razón social/dirección pueden consultarse por API | No necesaria |
| Crear orden según cotización recibida | Productos, cantidades, precios de compra sin IGV, referencia del acuerdo y entrega prevista | No necesaria en este avance; no se extraen líneas de productos por OCR |
| Recibir la mercadería | Comprueba todas las cantidades y registra número de factura o guía | **Opcional, dentro de la recepción**. Respalda lo que llegó y puede sugerir el número mediante OCR |

El precio de compra viene del acuerdo o cotización externa del proveedor. El OCR actual no reconoce de forma confiable tablas de productos ni los asocia al catálogo; por eso no completa cantidades/precios. No se debe afirmar en la exposición que lo hace. Una foto de cotización como adjunto no se incorporó en este cambio.

## Antes de probar

1. Publica **los dos archivos actuales `firestore.rules` y `storage.rules`** en tu proyecto Firebase. Hay permisos nuevos para fotos vinculadas a recepciones. No se publicaron automáticamente.
2. Ejecuta la app actualizada. Usa cuenta verificada, activa, ADMIN; para separación de tareas usa otra ALMACEN.
3. Ten proveedor y producto sincronizados. La consulta RUC directa sigue configurándose con DECOLECTA_TOKEN en local.properties, sin token en interfaz; ver `CONFIGURACION_CONSULTA_RUC.md`.
4. Las órdenes recibidas anteriormente siguen siendo historial. Las pendientes antiguas sin período, referencia y entrega deben cancelarse y recrearse.

## Pantallas actuales

- **Órdenes de compra:** botón Nueva orden, Actualizar, búsqueda y filtros. Más opciones agrupa Proveedores y Envíos pendientes. Ya no hay accesos sueltos a OCR/movimientos ni texto técnico permanente. ADMIN ve una tarjeta de presupuesto.
- **Nueva orden:** formulario separado de la consulta. Precio de compra vacío al elegir producto, independiente del precio de venta. Solicitar aprobación envía la orden; Guardar solo borrador no la envía.
- **Detalle:** tarjetas de proveedor, condiciones, productos, importes, decisión y entrega. Identificador corto OC-xxxxxxxx; no se muestran UID de operadores ni identificadores largos. Los identificadores completos siguen en Firebase para trazabilidad. El código corto es una referencia visual, no un correlativo fiscal.
- **Registrar entrega:** comprobación de cantidades, documento, observaciones y foto opcional en la misma pantalla.
- Estados visibles: **Por aprobar → Por recibir → Recibida**. También Rechazada y Cancelada.

## Prueba completa paso a paso

Ejemplo: stock inicial 10; compra 5 unidades a S/20 sin IGV. Subtotal S/100, IGV S/18 y total S/118.

1. Consulta Stock y anota la cantidad inicial. Desde Stock puedes entrar a Reponer mediante orden de compra.
2. Como ADMIN, administra presupuesto del mes y configura S/1,000 si se trata de un período sin compras previas. Espera confirmación del envío y actualiza.
3. Nueva orden: selecciona proveedor, producto, cantidad 5 y precio 20.00. Agrega el producto, referencia COT-PROV-001 y fecha real AAAA-MM-DD. Pulsa Solicitar aprobación.
4. Tras confirmar el envío, Actualizar muestra **Por aprobar**. Stock 10; disponible S/1,000. Crear no reserva presupuesto ni aumenta inventario.
5. Detalle: ADMIN escribe motivo y pulsa **Aprobar compra**. Tras confirmarse: Por recibir, reserva S/118 y disponible S/882. Stock sigue 10.
6. En la aprobada, Documento para enviar al proveedor permite desplegar generación/visualización/compartición del PDF. Se comparte por un canal externo; no envía automáticamente al proveedor ni acredita pago.
7. Pulsa **Registrar entrega** como ALMACEN o ADMIN. Comprueba las 5 unidades y marca la casilla. Registra F001-00123 o el número real de la guía/factura.
8. Si quieres respaldo, pulsa **Tomar foto de guía o factura** o **Elegir una imagen**. No tienes que salir a Documentos. Espera la lectura. Si el número estaba vacío y se reconoce uno, se sugiere en el campo; revísalo. Puedes corregirlo manualmente. Si no se reconoce, escríbelo. Puedes quitar/reemplazar la foto o continuar sin ella.
9. Pulsa Confirmar recepción completa y confirma. Se guardan dos envíos locales si hay foto: **Recepción de compra** y **Foto de la entrega**. Sin foto se guarda solo recepción.
10. La recepción confirmada deja la orden Recibida, stock 15, reserva cero, recibido S/118 y disponible S/882. Verifica Stock y Movimientos. No vuelvas a registrar una entrada manual.
11. La foto se envía por separado una vez confirmada la recepción. En detalle Recibida → Entrega recibida → **Consultar foto de la entrega** podrás abrirla cuando se haya subido. ADMIN y ALMACEN pueden consultarla; quien recibió es quien puede subir el respaldo de esa recepción.

## Si no hay internet o falla la imagen

La imagen preparada queda en el almacenamiento interno de la app. La recepción y su tarea de foto se insertan juntas en SQLite (Room), con identidades estables, antes de programar WorkManager. WorkManager espera red y procesa primero recepción, luego foto. No cambió la versión del esquema local.

La recepción y sus movimientos de stock siguen siendo atómicos en Firestore. La foto usa Storage y un registro vinculado en `recepciones_v2/{orderId}/adjuntos/{photoId}`: no es parte de esa transacción de inventario. Por eso una foto con ERROR no revierte una recepción ya confirmada. Reintentar **Foto de la entrega** no vuelve a sumar stock. Si falló la recepción, corrige/reintenta primero esa operación y después la foto.

En Más opciones → Envíos pendientes revisa cada envío. Las pérdidas temporales de red se reintentan con los límites existentes del worker. Los errores definitivos necesitan revisión/reintento. No desinstales ni borres los datos de la aplicación si necesitas conservar imágenes aún no enviadas. La foto opcional no es obligatoria para completar una compra.

## Pruebas de control

- Recibe una orden sin foto: debe terminar normalmente.
- Recibe otra con foto: ambas tareas quedan guardadas y la imagen puede consultarse al terminar su envío.
- Con datos cargados, activa modo avión, confirma con foto y cierra/reabre la app. Al volver la red se procesan recepción y foto. Comprueba un solo incremento de stock.
- Si la foto falla por Storage/reglas, revisa su error y reintenta después de corregir configuración; la recepción confirmada no debe repetirse.
- Sin presupuesto suficiente, aprobar falla sin aumentar stock. Cancelar una aprobada libera su reserva una vez.
- ALMACEN no autoriza ni cambia presupuesto; sí recibe órdenes aprobadas.
- Si faltan cantidades, no marques recepción completa. Las recepciones parciales siguen fuera del alcance.

## Límite de productos

Se conservan **3 productos distintos por orden**. Es un límite técnico de este avance para respetar validaciones y lecturas de seguridad de la transacción atómica, no una política de la empresa. El botón Agregar se deshabilita al alcanzar 3. Ampliar a 4, 5 o más requiere rediseñar y probar las reglas/operaciones, no solo cambiar una etiqueta. Temporalmente se usan órdenes separadas.

## Para exponer

“Registramos la compra según la cotización del proveedor. Administración aprueba y reserva presupuesto. Al recibir, verificamos cantidades y anotamos la guía o factura. Si necesitamos respaldo, tomamos la foto aquí mismo y revisamos el número sugerido. La recepción actualiza inventario de forma atómica; la foto se sincroniza aparte, para que reintentar una imagen no duplique stock.”

Archivos clave: PurchaseScreen.kt, BusinessScreen.kt, PurchasePhotoCapture.kt, BusinessViewModel.kt, QueueRepository.kt, LocalDatabase.kt, PendingSyncWorker.kt, FirestoreBusinessRepository.kt, PurchaseEvidenceRepository.kt, firestore.rules y storage.rules. Las fuentes de la HU08 están en el paquete principal `pe.edu.unmsm.siderurgica`. El guion con fragmentos está en `GUION_HU08_FLUJO_COMPLETO.md`.

No se añadieron pagos, préstamos, fabricación, recepciones parciales, lectura de tablas de cotización ni despliegue de FCM. No se probó cámara/compartición en un teléfono real durante esta actualización.
