> DOCUMENTO HISTÓRICO. Para la entrega 0.6.0 usa GUIA_SPRINTS_1_2_3.md y README.md. Sus indicaciones y alcance reemplazan esta referencia anterior.

> Versión actual 0.5.0: este documento conserva referencia de una etapa anterior. Usa GUIA_PRIMER_AVANCE.md y README.md para la configuración y el alcance actuales. Storage, borradores, proveedores y OCR ya tienen código en esta versión; deben configurarse/probarse según la guía. JDK 21 para Gradle.

> Versión actual 0.4.0: esta guía explica la conexión inicial. Para pedidos y ventas sigue CONTINUAR_PEDIDOS_VENTAS.md y publica el firestore.rules nuevo. Los módulos comerciales ahora incluyen conversión, aprobación y venta; Storage permanece pendiente.

# Conectar el proyecto Firebase del equipo

No necesitas Render, Railway, Neon ni una cuenta ERP. La configuración se realiza en el proyecto Firebase actual; no uses el de la profesora.

## 1. Aplicación Android

En Firebase Console → Configuración del proyecto → Tus apps, confirma el paquete `pe.edu.unmsm.siderurgica`. Coloca su `google-services.json` en `app/`. Si el ZIP ya lo incluye, procede del proyecto original, sin cuenta de servicio ni contraseña; comprueba que sea el proyecto que deseas usar. Es configuración cliente, no una llave de administrador. No se imprime su contenido en reportes y no debe confundirse con reglas de autorización.

Si cambias ese archivo, sincroniza Gradle y reconstruye. No modifiques el applicationId para conservar el vínculo. El ZIP omite local.properties: Android Studio configura el SDK local.

## 2. Authentication

Authentication → Sign-in method → habilita Correo/contraseña. Conserva los usuarios existentes. Revisa las plantillas de verificación y recuperación si deseas personalizarlas.

En la app ingresa con un correo existente o registra uno nuevo. Recibirás un enlace de verificación; ábrelo y pulsa «Ya verifiqué mi correo». El botón refresca el usuario y su token para que las reglas reciban email_verified. No almacenes contraseñas en Firestore. Un error al enviar el correo no elimina la cuenta; usa Reenviar enlace.

## 3. Firestore

Firestore Database → Crear base de datos (si no existe) → utiliza la base `(default)` y modo de producción. Selecciona una región deliberadamente; no se crea automáticamente desde el código.

Antes de reemplazar reglas existentes, guarda una copia y revisa si protegen otras apps del mismo proyecto. `firestore.rules` corresponde a esta etapa y deniega colecciones fuera de su alcance; si el proyecto ya tiene datos de otros módulos, integrar las reglas, no borrar su política accidentalmente.

Publica el contenido revisado de `firestore.rules` en la pestaña Reglas. No uses reglas públicas de prueba para solucionar permisos. La publicación no la ha realizado Codex.

Tras autenticación y verificación, la app crea el documento `usuarios/{UID}` con campos uid, email, nombre, rol=PENDIENTE, activo=true, createdAt y updatedAt. Si falla, confirma Firestore/default y las reglas; la app permite Reintentar. La asignación utiliza UID exacto, no el prefijo de correo ni IDs de Neon.

## 4. Primer administrador y roles

Ingresa y verifica la cuenta que será administradora para que aparezca su perfil. Desde Firestore Console → Datos → usuarios → documento UID, cambia `rol` a `ADMIN` (tipo string), conserva `activo=true` (boolean) y los demás campos. Solo una persona autorizada a gestionar Firebase debe hacerlo.

Para cuentas de prueba existentes, primero entrar/verificar para crear perfil y luego asignar `VENTAS`, `ALMACEN` o `RRHH` desde consola. Pulsa «Actualizar perfil y permisos» en Android. El usuario normal no puede editar rol ni estado desde la app. La gestión visual de roles está pendiente; las reglas ya reservan esa actualización a ADMIN.

No asignes ADMIN automáticamente al primer registro público ni incluyas una contraseña administrativa en Kotlin. PENDIENTE no obtiene acceso empresarial. RRHH existe como rol, pero su módulo no está migrado aún.

## 5. Demostración inicial

1. Usuario sin verificar: recibe pantalla de verificación y no accede a datos.
2. Usuario verificado sin perfil: obtiene perfil PENDIENTE.
3. Asignar ADMIN o ALMACEN: crear producto con precio, stock inicial y mínimo.
4. Cuenta VENTAS: consultar productos y registrar cotización (10/50/100 unidades cambian descuento).
5. Actualizar y cerrar sesión; iniciar con otra cuenta sin mostrar los listados de la anterior.
6. Desactivar perfil o cambiar rol desde consola: actualizar la app y comprobar restricción; las reglas protegen cada consulta incluso antes de actualizar la UI.

La consulta de cotizaciones se comparte entre VENTAS/ADMIN de esta empresa. creatorUid registra autor, no es un filtro para ocultar todas las cotizaciones de otros vendedores. Este esquema supone una sola empresa; no es multitenant.

## 6. Storage y etapas siguientes

Storage no está habilitado ni usado en este entregable. Se incorporará junto con documentos/PDF y sus reglas. Tampoco se mantienen llamadas al ERP como respaldo.

Mantener los requisitos futuros de API externa/Retrofit, Room, WorkManager, recursos móviles, IA y Play Store. Firebase no elimina esos puntos del curso.

## 7. Pruebas locales de reglas (opcionales para la conexión)

Con Node y Firebase CLI instalados: en rules-tests ejecuta npm install. Desde la raíz del proyecto ejecuta:

```text
firebase emulators:exec --only firestore --project demo-siderurgica "npm --prefix rules-tests test"
```

El ID demo-siderurgica y el puerto local 8080 impiden que estas pruebas apunten a los datos del equipo. No ejecutar pruebas destructivas contra Firestore real. Las pruebas usan JS solo como herramienta de verificación; el código de la app es Kotlin y no hay backend JS.

## Referencias oficiales

- Gestión de cuentas Android: https://firebase.google.com/docs/auth/android/manage-users
- Restricciones por campo: https://firebase.google.com/docs/firestore/security/rules-fields
