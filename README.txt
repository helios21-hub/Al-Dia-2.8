AL DÍA — PROYECTO ANDROID v2.30

Este proyecto contiene la app completa embebida dentro del APK. El uso principal funciona de forma local; algunas funciones, como comprobar actualizaciones, requieren Internet.

CONFIGURACIÓN
- applicationId: com.aldia.app
- minSdk: 26 (Android 8.0)
- targetSdk / compileSdk: 36
- orientación: vertical
- Java: 17
- versión: 2.30
- backup: v30

NOVEDADES v2.28
- Carbón únicamente viernes, con panel informativo y lógica operativa pendiente.
- Formularios Carnes-Vegetales: placeholders Código y Nombre Producto.
- Alta de Pedidos ubicada antes de la planilla y acceso destacado al formulario.
- Notas rápidas: tarjetas de ancho completo, búsqueda, filtros, editor simplificado y borradores recuperables independientes.
- Tema Claro renovado para mejorar contraste y lectura en teléfonos.
- Se conservan los datos, historial, aprendizaje y compatibilidad de backup/importación.
- Backup v28; versión 2.28; versionCode base 228000.
- El APK NO fue compilado localmente; validación definitiva por GitHub Actions.


NOVEDADES v2.30
- Pedidos: entrega automática jueves/sábado/martes según la pestaña.
- 4ta Gama: visible solamente en Martes y Jueves.
- Pedidos: un único acceso superior para Añadir producto.
- Carbón Viernes: acceso Añadir producto incorporado al panel.
- Próximo pedido: día + fecha automática en formato d/m/aa.
- Exportación XLSX universal completada con el archivo Java auxiliar correspondiente.
- Esta versión parte directamente del proyecto v2.28 real.
- El APK NO fue compilado localmente; validación definitiva por GitHub Actions.

HISTORIAL v2.27
- Rediseño operativo de Vencimientos, Pedidos y Muestras con planillas móviles.
- Vencimientos separados por retiro el día y retiro 10 días antes.
- Pedidos renombrados y recordatorios SRA / 4ta Gama; Carbón añadido como opción pendiente.
- Se mantiene retirado el flujo Revisar pedido anterior.

NOVEDADES v2.25 (base conservada)
- Recetas locales deja de depender de un JS externo para mostrarse: las 28 recetas quedan integradas también dentro de index.html, conservando el XLSX y recetas_locales.js como fuentes del proyecto.
- Pedidos corrige la pérdida de foco al cambiar Stock, Precio o Movimiento: se actualiza únicamente la ficha afectada.
- Al añadir un producto a Pedidos se puede elegir explícitamente Frutas, Vegetales u Hortalizas.
- Reordenamiento por pulsación larga mejorado con bloque flotante, hueco de destino visible, auto-desplazamiento cerca de los bordes y límite estricto dentro de la categoría de origen.
- Historial de Pedidos simplificado a Nombre, Stock y Pedido. Solo Pedido > 0 se resalta; fecha y Compartir se mantienen.
- Los nuevos historiales guardan la fotografía completa del pedido, incluidos los productos con Pedido 0, sin eliminar los datos internos usados para aprendizaje.
- Aprendizaje de Pedidos reforzado de forma compatible: control de valores atípicos, confianza sensible a variabilidad y referencia mensual gradual cuando existe evidencia suficiente.
- Se restaura/define la vista de revisión de pedidos anteriores, que estaba invocada pero no definida en la base anterior.
- El APK NO fue compilado localmente durante esta preparación; la compilación definitiva debe realizarse con GitHub Actions.

COMPILAR CON GITHUB ACTIONS
1. Subir el proyecto conservando app/, .github/, gradle/ y los archivos de raíz.
2. Abrir Actions > Compilar APK Al Día.
3. Pulsar Run workflow.
4. El workflow instala Android SDK 36 y Gradle 8.13.
5. Ejecuta lintDebug antes de assembleDebug.
6. Descargar Artifacts > Al-Dia-APK.

GENERAR EL APK EN ANDROID STUDIO
1. File > Open y elegir esta carpeta.
2. Esperar a que Android Studio sincronice Gradle y Android SDK.
3. Build > Build APK(s).
4. El APK debug aparecerá en app/build/outputs/apk/debug/app-debug.apk.

IMPORTANTE
- El proyecto conserva una clave debug fija para facilitar actualizaciones de la app.
- El script ./gradlew incluido históricamente es solo un aviso porque gradle-wrapper.jar no está incluido. Los workflows NO lo usan; configuran Gradle 8.13 directamente.
- La app conserva sus datos en WebView/localStorage y mantiene una copia nativa de estado; Exportar/Importar permite respaldarlos.
