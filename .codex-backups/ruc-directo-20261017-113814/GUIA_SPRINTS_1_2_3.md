> Actualización consulta RUC: ya no se ingresan tokens en la interfaz. Consulta `CONFIGURACION_CONSULTA_RUC.md`: credencial en servicio Kotlin/Secret Manager y URL en Android. La consulta real requiere completar ese despliegue; el registro manual sigue disponible.

> El flujo vigente de compras está documentado en `GUIA_HU08_COMPRAS_Y_PRUEBAS.md`: autorización, presupuesto y recepción completa. Las instrucciones anteriores de recepción directa de pendientes quedan reemplazadas.

> ACTUALIZACIÓN LOCAL DEL FLUJO COMERCIAL: consulta CAMBIOS_FLUJO_COMERCIAL.md. Listado, registro y detalle están separados; descuentos automáticos por cantidad; aceptación del cliente con canal y sustento; PDF de cotización para compartir. No se agregó gestión de pagos. Publica el firestore.rules actualizado antes de probar las decisiones.

# Instalación y demostración — 0.6.0

## 1. Actualizar sin perder el proyecto anterior

1. Respalda tu carpeta actual y extrae el ZIP en una carpeta nueva. Abre `SiderurgicaFirebase` desde Android Studio: contiene `settings.gradle.kts`.
2. Selecciona **JDK 21** para Gradle y usa SDK 36. Sincroniza y ejecuta. El paquete Android y el proyecto Firebase permanecen iguales.
3. El ZIP conserva el `google-services.json` de la entrega anterior. Si lo reemplazaste después por otro proyecto, copia el de tu aplicación actual; verifica el mismo applicationId. No compartas cuentas de servicio ni claves de firma.
4. Publica `firestore.rules` y `storage.rules` en la consola del proyecto correcto. No dejes las reglas antiguas: las operaciones nuevas requieren las colecciones y validaciones nuevas. Si otras apps comparten el proyecto, integra sus reglas en lugar de sobreescribirlas sin revisar.
5. Authentication debe tener correo/contraseña habilitado. Usa correo verificado y perfil `usuarios/{uid}` con rol activo ADMIN, VENTAS o ALMACEN según el módulo. El administrador inicial se mantiene. No se agregan roles automáticamente.
6. Configura Cloud Storage y la facturación que exija tu proyecto antes de probar subidas. Si cambió el bucket, descarga de nuevo `google-services.json`. Las reglas Storage consultan Firestore para comprobar roles: habilita los permisos entre servicios que solicite Firebase Console.
7. En Más, pulsa **Habilitar notificaciones**. Sin permiso puedes consultar estados y errores dentro de Borradores. La huella conserva su configuración anterior.

## 2. Qué ocurre con los registros anteriores

Usuarios, productos, proveedores, documentos y SQLite se conservan. La base Room sigue en versión 1 porque no se eliminaron ni cambiaron sus tablas; los comandos nuevos utilizan la tabla de operaciones existente.

El esquema anterior admitía una sola línea y descontaba al aprobar el pedido. Para no reinterpretar ni volver a descontar ventas previas, los flujos nuevos utilizan `clientes_v2`, `cotizaciones_v2`, `pedidos_v2`, `ventas_v2`, `compras_v2`, `movimientos_v2`, `operaciones_v2` y `comprobantes_v2`. **No se migran automáticamente registros comerciales viejos.** Siguen visibles en Más → Histórico de la etapa anterior, como consulta. Inventario y proveedores son compartidos entre las etapas. No crees copias de pedidos antiguos como si fueran ventas nuevas.

Los borradores de cotizaciones anteriores siguen sincronizándose con su formato anterior y aparecen en el histórico. Las operaciones nuevas se hacen desde Ventas, Compras y Movimientos. El resumen de Inicio consulta las cotizaciones nuevas.

## 3. HU03 — modo offline y sincronización

- Ingresa al menos una vez con internet y carga perfil, productos y los listados que vayas a consultar. Cada cuenta mantiene su caché separada en SQLite.
- Guarda un cliente/proveedor y espera su confirmación antes de seleccionarlo en una cotización o compra. Son referencias compartidas que deben existir en Firestore.
- Desconecta la red. Puedes consultar la última copia y preparar cotizaciones/pedidos directos/compras/movimientos con los datos cargados. También se pueden encolar aprobaciones, rechazos, conversiones, confirmaciones de venta, recepciones y PDF de ventas ya confirmadas.
- **Guardar solo borrador** deja BORRADOR y nunca se envía por sí solo. **Guardar y sincronizar** deja PENDIENTE. WorkManager requiere conexión y sesión del propietario. ENVIADO significa confirmación remota; ERROR requiere revisión.
- La transacción registra el ID de operación junto con sus efectos. Si se pierde la respuesta o se cierra el proceso, reintentar el mismo ID reconoce la confirmación anterior. No genera una segunda venta, compra o movimiento.
- En Borradores puedes revisar el detalle, sincronizar y reintentar. Los reintentos de red usan espera exponencial; al agotar ocho intentos requieren intervención. Los errores de validación no se reintentan automáticamente.
- Cerrar sesión cancela los trabajos de esa cuenta y limpia notificaciones visibles. Al ingresar de nuevo se reprograman sus pendientes. Android decide cuándo ejecutar, incluso con conexión.
- Si cambió el precio, falta stock, cambió un estado o ya existe el RUC, el servidor rechaza la operación. No significa que se haya realizado parcialmente. Revisa/actualiza los datos antes de preparar un comando nuevo.
- Las notificaciones del sistema informan resultado sin mostrar datos sensibles. La pantalla de Borradores conserva el mensaje de error detallado aunque deniegues notificaciones.

## 4. HU04 — cotizaciones y API REST

1. En Ventas → Nueva cotización, registra un cliente. Puede ser persona con RUC vacío o empresa con RUC validado. El RUC identifica de forma única a una empresa.
2. Opcionalmente consulta RUC con Retrofit/corrutinas mediante Decolecta. Se requiere token personal del proveedor. Es temporal en memoria: no se guarda en SQLite/Firestore ni en el APK. Revisa la razón social antes de registrar. El registro manual funciona sin API.
3. Espera ENVIADO y actualiza el listado; selecciona el cliente.
4. Agrega de uno a **tres productos distintos**, con cantidad entera y descuento automático por línea: 0 % antes de 10 unidades, 5 % desde 10, 7 % desde 50 y 10 % desde 100. El vendedor no puede editarlo. El límite acota las lecturas de las reglas y la transacción atómica. Para modificar una línea, quítala y agrégala nuevamente.
5. Antes de guardar se ve subtotal neto, IGV 18 % y total. Los importes se calculan en céntimos y redondeo HALF_UP. Firestore vuelve a validar precios y sumas.
6. Guarda como borrador o sincroniza. Actualiza después de ENVIADO. Hay búsqueda por cliente/producto/código, filtro por estado, detalle y estados de carga, vacío y error.
7. Abre el detalle y registra aceptación o rechazo comunicado por el cliente, con canal y observación. Una propuesta aceptada puede convertirse una sola vez en pedido conservando precios, descuentos e impuestos aceptados. Desde el detalle también puedes generar, guardar y compartir su PDF comercial; compartirlo no cambia su estado automáticamente.

## 5. HU05 — pedidos y ventas

Se puede crear un pedido directamente con el mismo formulario de varios productos o convertir una cotización aprobada. Crear/aprobar valida cliente y disponibilidad vigente. **Aprobar no reserva ni descuenta stock**: dos pedidos aprobados pueden competir por existencias. La venta confirma nuevamente disponibilidad y modifica todos los productos en una sola transacción; solo la que dispone de stock se confirma.

Estados: PENDIENTE → APROBADO o RECHAZADO; APROBADO → FACTURADO al confirmar venta. La venta queda CONFIRMADA. Si falla un producto, no se factura ni descuenta ninguno. Cada descuento crea un movimiento VENTA con operador, stock anterior/posterior y referencia al pedido. Reintentar mantiene el ID y no descuenta dos veces. La anulación/devolución de ventas confirmadas no pertenece a estas historias y no está habilitada.

## 6. HU06 — PDF y Storage

En Ventas, abre una venta confirmada y elige BOLETA o FACTURA. Factura exige RUC válido. Puedes generar el PDF offline desde la copia cargada, ver el detalle y usar un visor PDF instalado, guardar en una ubicación elegida con Android o compartir mediante el selector nativo.

El PDF contiene cliente, productos, cantidades, precios, descuentos, subtotal, IGV y total. Es **un comprobante académico sin validez tributaria**: no implementa emisión electrónica, numeración fiscal, firma ni envío SUNAT.

Subir PDF crea una operación de WorkManager. Antes de subir se vuelve a obtener la venta confirmada desde Firestore; no se usan importes arbitrarios del formulario. La copia tiene ruta estable `usuarios/{uid}/comprobantes/{saleId}_{tipo}.pdf`, y metadatos propios en `comprobantes_v2/{uid}/items/{saleId}_{tipo}`. Reintentar reutiliza esa ruta. Descargar recupera la copia de Storage de esa misma cuenta; no crea URL pública. Una cuenta distinta puede generar su propia copia autorizada de una venta, pero no leer los archivos privados de otra cuenta.

## 7. HU07 — inventario y alertas

Desde Stock consulta indicadores normal/bajo/agotado. Más → Movimientos permite ENTRADA, SALIDA y AJUSTE, con producto, cantidad y motivo. Para AJUSTE se introduce el stock final contado. Un ajuste utiliza el stock cargado como referencia: si hubo otro movimiento antes del envío, se rechaza para no sobrescribirlo. Las entradas/salidas validan el stock vigente y sus límites en la transacción.

Cada modificación necesita un movimiento atómico de respaldo. La interfaz muestra historial, stock antes/después, operador y fecha. Ninguna cuenta VENTAS puede crear entradas manuales: sus descuentos solo se autorizan mediante ventas confirmadas.

ADMIN/ALMACEN programan una revisión periódica con WorkManager cada 15 minutos como intervalo mínimo; no implica puntualidad exacta. El trabajo consulta permisos, registra/refresca token, revisa existencias, muestra aviso local cuando cambia el stock crítico y solicita sincronizar pendientes. Deshabilitar permisos de Android impide mostrar el aviso, sin perder los estados internos.

**Push FCM real:** están implementados el receptor Android, registro privado del token y el emisor Kotlin de `cloud-notifier`. Debes desplegar ese emisor en Cloud Run del proyecto y programar Cloud Scheduler, siguiendo su README. El emisor vuelve a consultar roles y stock en Firestore; el teléfono no contiene credenciales administrativas. Un aviso local de WorkManager NO es prueba de envío FCM. Codex no desplegó el componente ni envió push a teléfonos reales.

## 8. HU08 — proveedores, presupuesto y recepción

1. Más → Proveedores: registra manualmente o consulta RUC con la API. Revisa y guarda. Se valida RUC en Kotlin y se impide duplicar su documento remoto.
2. Más → Compras: selecciona un proveedor sincronizado y hasta tres productos. Introduce cantidades, precios de compra pactados y presupuesto máximo con IGV.
3. El total muestra subtotal, IGV y total. No se permite superar el presupuesto. Guarda borrador o sincroniza, también desde modo offline.
4. Una compra PENDIENTE permite **Registrar recepción completa**. Confirma únicamente si recibiste físicamente todas las cantidades.
5. La transacción cambia a RECIBIDA e incrementa inventario con movimientos COMPRA. Un reintento no vuelve a incrementar. Puedes encolar la recepción sin red y esperar confirmación; la interfaz no presenta stock local como si ya estuviera confirmado en la nube.

No se incluyen recepciones parciales, cuentas por pagar o edición posterior de órdenes: no forman parte de los criterios solicitados.

## 9. Recorrido de aceptación recomendado en tu teléfono

- ADMIN: configura roles y crea tres productos con stock conocido.
- VENTAS: registra cliente, consulta API con token propio, prepara cotización de tres productos y comprueba descuentos/IGV.
- Activa modo avión, guarda borrador, cierra/reabre y comprueba su conservación. Encola y recupera red; confirma ENVIADO y notificación.
- Aprueba cotización, crea/aprueba pedido y observa stock sin cambio. Confirma venta y verifica descuento exacto de las tres líneas y sus movimientos.
- Reintenta desde el mismo registro local: no debe repetirse el descuento. Con dos teléfonos, intenta vender más stock del disponible: solo una operación debe confirmarse.
- Genera PDF, abre visor, guarda, comparte, sube y descarga. Comprueba permisos con otra cuenta.
- ALMACEN: crea proveedor y compra dentro del presupuesto; rechaza una prueba que lo supere. Encola recepción offline y verifica incremento único después de sincronizar.
- Registra entrada, salida y ajuste. Prueba salida insuficiente y un ajuste cuyo stock cambió antes del envío: deben fallar sin efectos parciales.
- Revisión periódica: valida aviso local. Después de desplegar el emisor, ejecuta Scheduler y prueba FCM en primer/segundo plano, cuenta sin rol y cierre de sesión.
- Comprueba cámara/OCR y huella conservados de la etapa anterior.

Estas pruebas en el Firebase real y el dispositivo siguen siendo necesarias aunque la compilación y los emuladores locales pasen. Las historias se pueden presentar como implementadas en código, distinguiendo la activación de servicios y aceptación real pendiente.
