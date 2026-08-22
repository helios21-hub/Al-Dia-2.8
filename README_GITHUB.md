# Al Día — Android

Proyecto Android de **Al Día**, una app personal para control de vencimientos y pedidos.

## Compilar el APK con GitHub Actions

1. Crear un repositorio nuevo en GitHub.
2. Subir **el contenido de esta carpeta** a la raíz del repositorio.
3. Abrir la pestaña **Actions** del repositorio.
4. Elegir **Compilar APK Al Día**.
5. Pulsar **Run workflow**.
6. Cuando termine, abrir la ejecución y bajar hasta **Artifacts**.
7. Descargar **Al-Dia-APK**.
8. Dentro del ZIP del artefacto estará `Al-Dia-debug.apk`.

El workflow también se ejecuta automáticamente al hacer `push` a `main` o `master`.

## Instalar en Android

Copiar `Al-Dia-debug.apk` al teléfono y abrirlo. Android puede pedir habilitar **Instalar apps desconocidas** para el navegador o gestor de archivos que uses.

## Importante

Este es un APK **debug**, ideal para pruebas personales. Para una versión final conviene generar un APK firmado con una clave propia.


## Actualización 1.1

Esta versión agrega la sección **Muestras**, confirmaciones propias de la app, animaciones entre pestañas, accesos rápidos mejorados, notas en Pedidos, selector de destino al exportar copias y notificaciones configurables.

### Importante para esta actualización

A partir de esta versión el proyecto incluye una clave **debug fija** para que los próximos APK generados por este mismo proyecto puedan instalarse como actualización sin perder los datos.

Si el APK que ya tenés instalado fue generado con la versión anterior del workflow, Android puede indicar que no puede actualizarlo porque la firma es distinta. En ese caso:

1. Exportá primero una copia de seguridad desde la app actual.
2. Desinstalá la versión anterior.
3. Instalá el nuevo APK.
4. Importá la copia de seguridad.

Después de hacer esto una vez, las futuras compilaciones de este proyecto usarán la misma firma debug.

## v2.17
Incluye reposición por código de barras desde Inicio, biblioteca local de productos escaneados, creación automática de Nota rápida y corrección de solapamientos en Base de aprendizaje.

## v2.18
Corrección de compilación del escáner de códigos de barras: AndroidX queda habilitado en gradle.properties (`android.useAndroidX=true`). No cambia el esquema de datos respecto de v2.17.
