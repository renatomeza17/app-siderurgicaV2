# Trazabilidad de la entrega solicitada

El alcance es HU03, HU04, HU05, HU06, HU07 y HU08 del archivo PRODUCT_BACKLOG_FINAL.xlsx. HU01/HU02/HU09 se conservan. No incluye HU10–HU14 ni una certificación de aceptación en el entorno real.

| Historia | Implementación principal | Activación/aceptación por realizar |
|---|---|---|
| HU03 | data/local, worker/PendingSyncWorker, notifications/AppNotifications; caché por UID, comandos durables, reintento con mismo ID y confirmación atómica remota | Probar modo avión, reinicio, reconexión y cambio de cuenta en teléfono; conceder notificaciones |
| HU04 | ui/screens/business/BusinessScreen y BusinessViewModel; domain/rules/BusinessRules; FirestoreBusinessRepository; RucLookup/Retrofit | Token personal y consulta API real; recorrido de selección, filtros, detalle y errores |
| HU05 | FirestoreBusinessRepository: create, state, fulfill; pedidos/ventas y movimientos ligados por reglas | Probar compra simultánea de existencias desde dos dispositivos y reintento tras desconexión |
| HU06 | data/documents/ReceiptRepository y ui/screens/business/ReceiptActions; PdfDocument, FileProvider, selector Android y Storage | Configurar bucket/reglas; probar visor, destino de guardado, compartir y descarga. Comprobante académico, sin emisión SUNAT |
| HU07 | BusinessScreen movimientos, BusinessRules.stock, historial Firestore; InventoryReviewWorker, InventoryMessagingService, cloud-notifier Kotlin | Desplegar emisor privado Cloud Run y Scheduler; registrar token y comprobar FCM real. El aviso local no reemplaza esa prueba |
| HU08 | Proveedores existentes más formularios/órdenes/presupuesto/recepción en BusinessScreen y FirestoreBusinessRepository | Probar cola offline y recepción física de demo; actualizar reglas y verificar incremento único |

Hasta tres productos distintos por documento; recepción completa de la compra. Los datos del formato anterior se conservan como histórico. Los archivos nuevos en Kotlin siguen las capas de UI/ViewModel, dominio/repositorio y datos/Firebase. Consulta GUIA_SPRINTS_1_2_3.md para el recorrido exacto.
