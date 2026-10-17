# Flujo comercial aclarado — actualización sobre 0.6.0

Cambios locales sobre la carpeta actual. No se modificó google-services.json, Authentication, usuarios remotos, Storage ni datos en Firebase. No se agregó un módulo de pagos ni se cambió el momento de descuento de stock.

## Pantallas

En Ventas, cada pestaña muestra un listado. Nueva cotización / Nuevo pedido directo abre una vista de registro separada. Tocar una tarjeta abre el detalle con importes y acciones. Registrar otro cliente abre su propia vista y permite volver al formulario. Atrás en Android vuelve al listado o al formulario según corresponda. Compras, inventario, proveedores, OCR, huella, sincronización y comprobantes de venta se conservan.

## Descuentos nuevos

El precio se obtiene del catálogo. El vendedor introduce cantidad, sin elegir un descuento: menos de 10 unidades 0 %, de 10 a 49 5 %, de 50 a 99 7 % y desde 100 10 %. Se calcula por línea tanto en cotizaciones como en pedidos directos. Kotlin y las reglas Firestore validan la misma política. Las compras conservan su precio pactado y sin descuento adicional.

Las cotizaciones ya guardadas conservan sus importes y descuentos acordados al generar PDF o convertirlas en pedido. No se reescriben los registros existentes. Los borradores antiguos sin enviar que incluyan un descuento manual distinto a la nueva política pueden quedar ERROR: revisa su resultado antes de preparar un borrador nuevo. Las confirmaciones remotas antiguas siguen reconociéndose aunque su comando no tenga el nuevo campo de canal.

## Aceptación y validación

Cotización PENDIENTE se muestra como Esperando respuesta. El vendedor registra aceptación/rechazo comunicado por el cliente, con canal y observación de 5–240 caracteres. Firestore guarda UID del operador y fecha del servidor. La cotización aceptada puede convertirse a pedido sin volver a introducir productos.

El pedido PENDIENTE necesita Validar pedido o Rechazar pedido y un motivo operativo. La validación comprueba stock pero no lo reserva ni confirma pagos. Confirmar venta vuelve a comprobar y descuenta todos los productos de manera atómica, sin duplicar en los reintentos. No se exige registrar depósitos, vouchers ni operaciones bancarias.

## PDF de cotización

En el detalle: Generar PDF de cotización → Ver PDF / Guardar PDF / Compartir cotización. Utiliza los importes de la propuesta guardada; puede generarse desde datos previamente cargados sin conexión. El selector Android permite elegir WhatsApp, correo u otra aplicación instalada. Compartir no certifica entrega ni aceptación y no cambia el estado automáticamente. Es una propuesta, no un comprobante ni evidencia de pago. El archivo queda localmente en la carpeta privada cotizaciones/{uid}; no se agregó una subida nueva a Storage para estas propuestas. Los PDF de ventas mantienen su subida y descarga existentes.

## Para probar

1. Publica el firestore.rules actualizado en el proyecto correcto (integra reglas de otras apps si comparten Firebase). No hace falta cambiar storage.rules por estos ajustes.
2. Compila y ejecuta en Android Studio. No desinstales ni borres datos si quieres conservar borradores y archivos locales.
3. Abre Ventas: comprueba que el listado no contiene formularios ni botones de aprobación en cada tarjeta.
4. Crea una propuesta de 9 unidades y verifica 0 %. Quita la línea y prueba 10, 50 y 100: 5 %, 7 % y 10 %. Revisa subtotal neto, IGV y total.
5. Sincroniza y abre el detalle. Genera, guarda y comparte PDF. Revisa que muestra los importes de esa propuesta.
6. Registra aceptación con canal y observación. Espera ENVIADO, actualiza y comprueba operador, fecha y sustento en el detalle.
7. Crea pedido, espera confirmación, valida con motivo y comprueba que el stock no cambió.
8. Confirma venta: stock se descuenta una vez y se conserva la generación de boleta/factura PDF.
9. Comprueba una cotización anterior con descuento manual: conserva ese descuento al consultar/PDF/convertir.
10. Prueba borrador sin red, reinicio y reconexión; verifica también compras, documentos y biometría.

Los códigos internos de estado siguen iguales para preservar datos: APROBADA se muestra como Aceptada por el cliente; APROBADO como Pedido validado; FACTURADO como Venta registrada. El registro de decisiones nuevo exige publicar las reglas nuevas antes de probarlo. No se publicaron reglas remotamente desde esta actualización.
