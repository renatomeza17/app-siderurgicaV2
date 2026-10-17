# Siderúrgica Perú — Firebase 0.6.0

Proyecto Android Studio completo para HU03, HU04, HU05, HU06, HU07 y HU08. Conserva Authentication, interfaz, roles, biometría y captura/OCR de la entrega anterior. **Lee GUIA_SPRINTS_1_2_3.md antes de ejecutar.**

- Kotlin, Compose/Material 3, ViewModels, repositorios, inyección Koin.
- SQLite/Room por usuario: perfiles, catálogo, listados y operaciones. WorkManager con reintentos, confirmación, estados y notificaciones.
- Clientes y consulta RUC opcional con Retrofit/corrutinas. Cotizaciones/pedidos directos con hasta tres productos, descuentos por línea y cálculo de IGV antes de confirmar.
- Pedido aprobado sin descuento de stock; confirmación de venta atómica con todos sus movimientos. Sin sobreventa ni ventas parciales.
- Boleta/factura PDF académica: generación local, detalle, vista previa, guardar/compartir nativo, subida y descarga de Storage. Sin emisión tributaria/SUNAT.
- Inventario: entradas, salidas, ajustes con comprobación de versión del stock e historial de respaldo.
- Proveedores, órdenes de compra con presupuesto y recepción completa que incrementa stock una sola vez. Cola offline.
- Revisión periódica WorkManager, notificación local y receptor FCM con tokens privados. Emisor FCM en Kotlin/JVM para Cloud Run dentro de `cloud-notifier`, con instrucciones de despliegue.

La app no consume el ERP de Render/Railway ni Neon. El emisor FCM es un componente confiable de notificaciones, separado del APK. Requiere despliegue y configuración en tu cuenta para recibir push real. No se publicaron recursos/reglas ni se activó facturación.

Abre esta carpeta en Android Studio, usa JDK 21 y SDK 36. Conserva tu `google-services.json` y publica las reglas nuevas integrándolas con otras aplicaciones si comparten proyecto. No borres usuarios, productos o datos anteriores.

VALIDACION.md documenta pruebas y límites reales. GUIA_PRIMER_AVANCE.md y los demás documentos antiguos son históricos; esta guía de sprints tiene prioridad para la versión 0.6.0.
