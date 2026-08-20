AL DÍA — PROYECTO ANDROID

Este proyecto contiene la app completa embebida dentro del APK.
No requiere hosting ni conexión a Internet.

CONFIGURACIÓN
- applicationId: com.aldia.app
- minSdk: 26 (Android 8.0)
- targetSdk / compileSdk: 36
- orientación: vertical
- versión: 1.0

GENERAR EL APK EN ANDROID STUDIO
1. Abrir Android Studio.
2. File > Open y elegir esta carpeta: AlDia_Android
3. Esperar a que Android Studio descargue/sincronice Gradle y Android SDK.
4. Build > Build APK(s).
5. El APK aparecerá en:
   app/build/outputs/apk/debug/app-debug.apk

Para un APK firmado:
Build > Generate Signed App Bundle or APK > APK.

La app:
- carga index.html desde android_asset;
- funciona offline;
- conserva localStorage dentro de WebView;
- permite importar/exportar copias usando el selector de archivos de Android;
- usa el icono elegido para Al Día.
