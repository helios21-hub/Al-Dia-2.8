AL DÍA — PROYECTO ANDROID v2.23

Este proyecto contiene la app completa embebida dentro del APK. El uso principal funciona de forma local; algunas funciones, como comprobar actualizaciones, requieren Internet.

CONFIGURACIÓN
- applicationId: com.aldia.app
- minSdk: 26 (Android 8.0)
- targetSdk / compileSdk: 36
- orientación: vertical
- Java: 17
- versión: 2.23
- backup: v23

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
