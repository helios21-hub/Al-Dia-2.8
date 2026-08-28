AL DÍA — PROYECTO ANDROID v2.25

Este proyecto contiene la app completa embebida dentro del APK. El uso principal funciona de forma local; algunas funciones, como comprobar actualizaciones, requieren Internet.

CONFIGURACIÓN
- applicationId: com.aldia.app
- minSdk: 26 (Android 8.0)
- targetSdk / compileSdk: 36
- orientación: vertical
- Java: 17
- versión: 2.25
- backup: v25

NOVEDADES v2.25
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
