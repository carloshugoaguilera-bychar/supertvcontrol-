# Control TV (control remoto universal por IR)

App Android que usa el emisor infrarrojo del celular para controlar TVs.

## Cómo funciona

1. Escribes la marca (ej. LG): la app descarga solo esa marca.
2. Pones un rango de códigos (ej. 1 al 20) o directamente tu número de código.
3. La app envía “encender/apagar” código por código; cuando el TV responde, tocas **¡Responde!**.
4. Pruebas los demás botones y marcas cuáles responden (Sí / No).
5. Guardas el código con un nombre (ej. “TV sala”). Si hay varios que funcionan, la app muestra cuál es el mejor.

Si tu marca no está, usa **“No está mi marca: probar con todas las marcas”** (prueba todos los códigos juntos) o **“Escribir un código manual”** (protocolo, dirección y comandos en hexadecimal).

**Botonera ampliada:** números 0-9, apps (Netflix, YouTube, Prime), reproducción, colores y guía, ajustes (Settings, Tools, Aspecto, Audio) y Sleep. Los botones que le faltan a un código se completan con otros controles de la misma marca, mismo protocolo y misma dirección (se marcan con * hasta que los pruebes). En un TV guardado, el botón **Completar botones desde la base** hace lo mismo, **Buscar comando de un botón** prueba comandos uno por uno (con el protocolo y la dirección de tu TV) hasta que el TV responde, y lo guarda en el botón que elijas. **Agregar botón a mano** permite escribir protocolo, dirección y comando.

El **número de código** identifica un perfil completo de una marca (todos sus botones). Los más comunes van primero.

## Pasos para tenerla en el celular (sin instalar nada en la computadora)

1. Crea un repositorio **público** en GitHub y sube todos los archivos de esta carpeta (respetando las rutas).
2. En GitHub, pestaña **Actions** → **Generar códigos por marca** → **Run workflow**. Crea la carpeta `brands/`.
3. En **Actions** → **Compilar APK** → **Run workflow**. Al terminar, descarga `ControlTV-APK` y dentro está `app-debug.apk`.
4. Instala el APK en el celular (permite “instalar apps desconocidas”).

La app toma sola la dirección de descarga de tu repositorio (usuario/repo), no hay que editar nada.

## Archivos y rutas

| Ruta | Para qué sirve |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` | Configuración del proyecto |
| `app/build.gradle.kts` | Configuración de la app y dirección de descarga |
| `app/src/main/AndroidManifest.xml` | Permisos (IR e internet) |
| `app/src/main/java/com/claudia/controltv/IrEngine.kt` | Convierte cada protocolo (NEC, Samsung, Sony, RC5, RC6, RCA, Panasonic, RAW) en pulsos IR |
| `app/src/main/java/com/claudia/controltv/Data.kt` | Modelos, descarga por marca, caché y TVs guardados |
| `app/src/main/java/com/claudia/controltv/Ui.kt` | Botones de la botonera |
| `app/src/main/java/com/claudia/controltv/MainActivity.kt` | Pantalla de inicio y botonera de cada TV |
| `app/src/main/java/com/claudia/controltv/ManualDialog.kt` | Ventana para agregar un botón a mano |
| `app/src/main/java/com/claudia/controltv/ScanDialog.kt` | Búsqueda de comando de un botón |
| `app/src/main/java/com/claudia/controltv/SearchScreen.kt` | Buscar marca, probar rango, verificar botones y guardar |
| `tools/build_brands.py` | Convierte la base Flipper-IRDB a JSON por marca |
| `.github/workflows/generar-codigos.yml` | Genera la carpeta `brands/` |
| `.github/workflows/compilar-apk.yml` | Compila el APK |

## Notas

- Solo funciona en celulares Android con emisor IR (varios Xiaomi, Poco, Redmi y algunos Huawei/Samsung antiguos). iPhone no tiene IR.
- Los códigos vienen de la base abierta Flipper-IRDB (licencia MIT). No cubre todos los modelos.
- Protocolos no incluidos todavía: Pioneer.
