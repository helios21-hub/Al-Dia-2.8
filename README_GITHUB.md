# Al Día — Android

Proyecto Android de **Al Día**, una app personal para control diario de vencimientos, notas, bajas de precios y consulta de códigos.

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


## Estado actual — v2.35

La base válida para continuar es **v2.35**, creada sobre la **v2.34 corregida para Lint/API 26**. La cadena conserva la base real v2.28 y las mejoras acumuladas posteriores. No se reconstruyó la app ni se alteraron `applicationId`, firma debug fija, importación/exportación de copias ni los datos existentes.

### Secciones activas

- **Inicio**: resumen diario, buscador global y recordatorios de pedidos por día.
- **Vencimientos**: productos agrupados con múltiples fechas independientes.
- **Ofertas**: bloques completos con fechas, productos, tipo de promoción y recordatorios.
- **Notas rápidas**: editor amplio, Cancelar/Guardar accesibles y checklist interactivo.
- **Baja de precios**: reemplaza visualmente a Carnes-Vegetales y exporta `Planilla vegetales-DD-MM-AAAA.xlsx`.
- **Lista de códigos**: reemplaza Biblioteca con las 8 listas de `Planilla Lista de Codigos.xlsx`, editables y exportables a XLSX.
- **Ajustes**: tema Clásico único, notificaciones y copias de seguridad.

### Cambios estructurales

- Navegación principal fija abajo: **Inicio, Vencimientos, Notas, Ofertas y Más**; Más agrupa Baja de precios, Lista de códigos y Ajustes. Se retiró el swipe lateral entre secciones para no interferir con tablas desplazables.
- Identidad visual unificada con el logo **Al Dia** azul/verde, fondo blanco puro, tarjetas limpias, botones amplios y estados verde/naranja/rojo/azul.
- **Pedidos** y **Muestras** dejan de formar parte de la interfaz activa. Sus datos heredados se conservan internamente y en las copias para no destruir información histórica.
- Los recordatorios de pedidos pasan a **Inicio** y a la notificación diaria: Martes/Jueves (SRA 217, 4° Gama y Jumbo Retail), Sábado (Jumbo Retail) y Viernes (Carbón), con sus horarios límite.
- **Consultor** deja de exponerse como sección principal.
- `versionName`: **2.35**.
- `versionCode` base: **235000 + GITHUB_RUN_NUMBER**.
- Backup interno: **v35**.

El APK no fue compilado en el entorno donde se preparó esta actualización. La validación definitiva se realiza con `gradle :app:lintDebug --stacktrace` y luego `gradle :app:assembleDebug --stacktrace` en GitHub Actions.

Consultar `CAMBIOS_V2_35.txt` y `VALIDACION_V2_35.txt` para el detalle de esta versión.

## Instalar en Android

Copiar `Al-Dia-debug.apk` al teléfono y abrirlo. Android puede pedir habilitar **Instalar apps desconocidas** para el navegador o gestor de archivos que uses.

## Importante

Este es un APK **debug**, ideal para pruebas personales. Para una versión final conviene generar un APK firmado con una clave propia.


## Historial de versiones anteriores

Las secciones siguientes son antecedentes y no sustituyen el estado vigente v2.35.

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


## Cambios v2.32
- Buscador global por nombre o código/EAN con ubicación y acceso directo.
- Vencimientos agrupa todas las fechas bajo un solo producto y permite editar/retirar cada fecha o el producto completo.
- Carbón Viernes usa fichas de Pedidos, con Stock/Pedido/Precio/Movimiento manuales y sin aprendizaje.
- Recordatorio de FRUTA SECA, JUGOS Y SEMILLAS con tabla IMAGEN/DÍA/ANTES DE LA HORA.
- Botones de Añadir producto unificados en azul de alto contraste.
