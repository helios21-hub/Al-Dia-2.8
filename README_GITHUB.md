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

Los workflows están configurados en modo **manual**. Después de cada actualización, entrá en **Actions** y ejecutá **Run workflow** cuando quieras compilar.


## Estado actual — v2.26

- Se retiró la tarjeta y flujo **Revisar pedido anterior** de Pedidos.
- Se conserva el resto de v2.25 sin cambios funcionales: edición estable, selector de categoría, reordenamiento guiado, historial simple y sugerencias.
- Historial mantiene **Nombre, Stock y Pedido**, fecha y Compartir; solo Pedido > 0 se destaca.
- Los datos aprendidos o restaurados desde backups anteriores no se eliminan.
- Backup v26, compatible con restauración de copias anteriores.
- versionName 2.26 y versionCode base 226000 + GITHUB_RUN_NUMBER.

- **Recetas locales** conserva las 28 recetas de `Recetas LOCALES.xlsx` (14 FRUTA y 14 VERDURA), pero ahora los datos también están integrados directamente en `index.html` para evitar una pantalla vacía si falla la carga del JS externo.
- El XLSX original continúa en `app/src/main/assets/biblioteca/Recetas_LOCALES.xlsx` y `recetas_locales.js` se conserva como fuente separada.
- **Pedidos** deja de reconstruir toda la lista al cambiar Stock, Precio o Movimiento; la sugerencia/confianza de la ficha se actualiza en su lugar.
- Al añadir productos se elige explícitamente la categoría.
- El arrastre por pulsación larga muestra un bloque flotante y un hueco de destino, hace auto-scroll y no permite cruzar de categoría.
- El Historial de Pedidos muestra por producto solo **Nombre, Stock y Pedido**; únicamente Pedido > 0 se destaca. Fecha y Compartir se mantienen.
- Los nuevos historiales guardan todos los productos del día, incluso Pedido 0, conservando en segundo plano los datos técnicos necesarios para aprendizaje.
- El aprendizaje incorpora control de observaciones atípicas, confianza basada también en estabilidad y una referencia mensual gradual cuando ya hay al menos dos ciclos de ese mes.
- Se incorpora la función de revisión del pedido anterior que la interfaz ya intentaba usar pero no estaba definida en la base previa.
- Backup v25, compatible con restauración de copias anteriores.
- Las mejoras de notificaciones de v2.24 se mantienen sin cambios.
- Los workflows ejecutan `lintDebug` antes de compilar y configuran Gradle 8.13 directamente.

> Nota: el paquete histórico sigue sin `gradle-wrapper.jar`; GitHub Actions no depende de ese wrapper. Durante esta preparación no se compiló un APK localmente.

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



## v2.20
- Escáner de tickets de Vencimientos reforzado para priorizar PLU y fecha de vencimiento.
- La fecha del ticket se busca por estructura: línea de Vence / fecha inferior antes del PLU; se ignora L/Etq.
- El nombre OCR ya no se carga si es dudoso: primero se consulta PLU conocido y luego coincidencia inteligente contra Base de aprendizaje y catálogos internos.
- Sugerencias de nombre de confianza media se muestran para confirmar en vez de completar texto incorrecto.
- OCR nativo ordena las líneas según su posición visual antes de procesar tickets.
- Agrupación de vencimientos prioriza Código/EAN o PLU para mantener varias fechas en una misma ficha.
- Backup v20 y versionName 2.20.

## v2.19
- Escaneo de reposición mejorado con nombre/presentación y foto opcional.
- Escaneo de Vencimientos por Código/EAN, foto o ticket de balanza (PLU + Vence).
- Varias fechas de vencimiento agrupadas visualmente por producto.
- OCR local ML Kit y backup v19.


## v2.21
- Ticket/etiqueta usa ML Kit Document Scanner con corrección de perspectiva y mejora de imagen.
- OCR del ticket por geometría + doble lectura (normal y alto contraste) fusionada en todos los tickets.
- Vence se determina por la fecha inferior más cercana al PLU, penalizando L/Etq.
- Nombre asociado por PLU o similitud contra Base de aprendizaje y catálogos conocidos.
- Backup v21 y versionName 2.21.
