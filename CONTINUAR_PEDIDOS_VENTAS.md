> DOCUMENTO HISTÓRICO. Para la entrega 0.6.0 usa GUIA_SPRINTS_1_2_3.md y README.md. Sus indicaciones y alcance reemplazan esta referencia anterior.

> Versión actual 0.5.0: este documento conserva referencia de una etapa anterior. Usa GUIA_PRIMER_AVANCE.md y README.md para la configuración y el alcance actuales. Storage, borradores, proveedores y OCR ya tienen código en esta versión; deben configurarse/probarse según la guía. JDK 21 para Gradle.

# Continuar con pedidos y ventas — versión 0.4.0

Proyecto completo basado en la entrega de interfaz anterior. Abrir la carpeta `SiderurgicaFirebase` con `settings.gradle.kts` en Android Studio. Mantiene el mismo applicationId y la misma configuración Firebase. Conserva perfiles y roles existentes, incluida tu cuenta ADMIN. No requiere Render, Railway o Neon.

## Antes de ejecutar los módulos nuevos

1. Guarda un respaldo de las reglas vigentes del proyecto Firebase.
2. Firestore → Reglas: revisa y publica el nuevo archivo `firestore.rules` de este ZIP. Las reglas anteriores no autorizan pedidos, ventas o aprobaciones.
3. Abre el proyecto nuevo, sincroniza y ejecuta. Ingresa con ADMIN o VENTAS y correo verificado.
4. No borres colecciones ni recrees cuentas. Se utilizan `usuarios`, `productos` y `cotizaciones` existentes. Las nuevas colecciones `pedidos` y `ventas` aparecen al ejecutar sus respectivas operaciones.

Si el proyecto Firebase también sirve a otras aplicaciones, integra sus reglas con cuidado: la política proporcionada es para Siderúrgica y cierra colecciones fuera de alcance. Codex no ha publicado reglas ni modificado tus datos reales.

## Recorrido de demostración

1. Con ADMIN/ALMACEN, registra un producto o utiliza uno existente con stock suficiente. En esta etapa solo hay alta inicial; ajustes y entradas/salidas manuales siguen pendientes.
2. Ventas → Cotizaciones: registra la propuesta; puedes probar cantidades 10/50/100 para los descuentos.
3. En la tarjeta pulsa Aprobar cotización y confirma. No se cambia stock en este paso.
4. Pulsa Crear pedido. Se escribe un pedido y la cotización pasa a CONVERTIDA en una sola transacción.
5. Abre Pedidos desde la sección comercial, Inicio o Más. Aprobar pedido comprueba el stock vigente y lo descuenta junto con la transición a APROBADO. Si no alcanza, se rechaza la operación sin aprobar ni modificar el stock.
6. Un pedido PENDIENTE también se puede rechazar o anular sin afectar stock. No se habilita anulación de un pedido ya aprobado: requiere una política de reversión que está fuera de esta entrega.
7. En el pedido APROBADO pulsa Generar venta. Se crea una venta y el pedido queda FACTURADO, sin volver a descontar stock.
8. Abre Ventas y revisa cliente, producto, cantidad, subtotal, IGV y total. Esta etapa no emite comprobantes tributarios ni PDF.
9. Actualiza Inventario o Inicio para ver el stock nuevo; sus listados no son listeners en tiempo real.

## Política de importes

Conservamos el descuento aceptado de la cotización. El subtotal del pedido es el total neto de esa propuesta y se agrega IGV de 18 %, redondeado HALF_UP a céntimos. Esta decisión corrige la pérdida de descuento observada en la conversión del ERP de referencia y está indicada en la confirmación de la app.

Ejemplo: 10 unidades a S/ 1.01 → subtotal original S/ 10.10 → descuento 5 % → neto S/ 9.60 → IGV S/ 1.73 → total pedido/venta S/ 11.33.

## Consistencia y permisos

- ADMIN/VENTAS pueden gestionar los procesos comerciales; ALMACEN/PENDIENTE no pueden aprobar pedidos ni crear ventas.
- Las reglas exigen escrituras relacionadas: no se puede convertir sin crear pedido, aprobar sin descontar stock o facturar sin crear venta.
- Pedido y venta utilizan IDs estables derivados de su origen. Reintentar conversión, aprobación o venta verifica el resultado existente y no repite el efecto. Esto también funciona tras reiniciar porque el ID de origen ya existe en Firestore.
- El reintento del registro inicial de una cotización aún conserva el ID solo durante la vida de su ViewModel. La cola persistente offline será otra etapa; revisar el listado después de un envío incierto y reinicio.
- Las transacciones requieren conexión. No se anuncia aprobación o venta confirmada mientras el dispositivo está offline.
- El creatorUid de la propuesta se conserva; convertedBy, updatedBy y createdBy registran a quienes ejecutan los pasos siguientes.
- Los códigos PED-/VEN- de las tarjetas son referencias visuales abreviadas del ID, no numeración fiscal ni un correlativo empresarial.

## Alcance implementado y pendiente

Implementado: autenticación/perfiles/roles, catálogo y stock consultable, cotizaciones con estados, pedido desde cotización, aprobación/rechazo/anulación de pedido pendiente, descuento de stock y venta desde pedido aprobado. Conserva encabezados, barra inferior, tarjetas y menús.

Pendiente: pedido directo sin cotización, reversión de pedidos aprobados, edición/movimientos manuales de inventario, proveedores, presupuesto/compras/recepción, PDF/Storage/documentos, Room/WorkManager, API externa/Retrofit, despacho/RRHH, dashboards avanzados, IA y Play Store. No se presentan estos módulos como completos.

## Verificación

APK debug compilado. Ocho pruebas unitarias y doce pruebas de reglas en emulador local aprobadas. Se probaron descuentos/IGV, permisos, escrituras atómicas, rechazo de importes manipulados, reintentos y concurrencia sobre stock. Las pruebas de reglas ejercitan Firestore con cliente de prueba; no sustituyen una demo de la app Android conectada a tu proyecto real.

No se ejecutó verificación visual Android ni se modificó Firebase remoto. Mantener una copia del proyecto anterior como respaldo y continuar desde esta versión si los recorridos de la demo funcionan con tu configuración.
