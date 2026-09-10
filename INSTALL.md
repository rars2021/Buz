# Cómo instalar y probar Buz

## 1. Requisitos (una sola vez)

Instalar en tu PC:

- **Android Studio Hedgehog o superior** — https://developer.android.com/studio
  - Al abrirlo la primera vez acepta el SDK y deja que baje: Android SDK 34, Build Tools, Platform Tools.
- **JDK 17** — Android Studio ya lo incluye; si preferís aparte: https://adoptium.net (Temurin 17).

En tu teléfono Android:

- **Activar Opciones de desarrollador**: Ajustes → Acerca del teléfono → tocar 7 veces "Número de compilación".
- **Depuración USB** activada en Ajustes → Sistema → Opciones de desarrollador.
- Cable USB que soporte datos (no solo carga).

## 2. Abrir el proyecto

1. Abrir Android Studio → **Open** → seleccionar la carpeta `C:\Users\RENATO\Downloads\Port\DipsMobile`.
2. La primera vez tardará unos minutos: bajará Gradle 8.5, Kotlin 1.9, AGP 8.5, Compose BOM 2024.05.
3. Cuando termine el "Gradle sync", en la barra superior debería aparecer el módulo `app` como run configuration.

Si el sync falla:
- Comprobá que la ruta no tenga acentos (ok: `Port`).
- File → Invalidate Caches → Invalidate and Restart.

## 3. Correr los tests (sin teléfono)

Abrir la terminal integrada de Android Studio (View → Tool Windows → Terminal) y correr:

```bash
gradlew.bat test
```

En Linux/Mac o Git Bash:

```bash
./gradlew test
```

Los tests validan la matemática pura sin necesitar dispositivo:
- `CoreTest` — proyecciones y Fisher
- `ImportTest` — CSV/TSV/Terzaghi
- `SetsExportTest` — ventanas de sets y export CSV

Deberías ver `BUILD SUCCESSFUL`.

## 4. Correr en un emulador

1. En Android Studio: **Device Manager** (icono a la derecha) → **Create Device** → Pixel 6 → System Image API 34.
2. Iniciar el emulador (▶).
3. Con el emulador corriendo, tocar el ▶ verde de la barra superior (run 'app').
4. Compila y despliega. Primera vez ~2 min.

## 5. Correr en tu teléfono real

1. Conectar el teléfono por USB.
2. En el teléfono aparecerá "¿Permitir depuración USB?" → Sí, siempre.
3. En Android Studio, en el desplegable de dispositivos elegí tu teléfono.
4. ▶ para instalar y correr.

## 6. Generar APK instalable (para compartir el .apk sin Android Studio)

En la terminal del proyecto:

```bash
gradlew.bat assembleDebug
```

El APK queda en:

```
app/build/outputs/apk/debug/app-debug.apk
```

Copiar ese archivo al teléfono (Drive, USB, WhatsApp a vos mismo) y abrirlo desde el explorador
de archivos → aceptar "Instalar desde origen desconocido".

Para una versión firmada y liviana:

```bash
gradlew.bat assembleRelease
```

Requiere un keystore — Android Studio te lo genera desde **Build → Generate Signed Bundle / APK**.

## 7. Probar la app

1. Copiar `examples/ejemplo.csv` al teléfono (Descargas, o Drive).
2. Abrir Buz → **Abrir** → seleccionar el CSV.
3. Explorar las pestañas: **Red / Rosa / Datos / Estad.**
4. En la pestaña Red:
   - Tocar **Contornos** para ver la densidad Kamb.
   - Tocar **Planos** para ver los círculos mayores de cada medición.
   - Tocar **Terzaghi** para aplicar la corrección (usa eje vertical por defecto).
   - Tocar **Dibujar set** → luego arrastrar un rectángulo sobre la red. Los polos dentro se colorean.
   - Repetir para varios sets. Cada set queda listado en la pestaña **Estad.** con su media Fisher.
5. Con el botón **PNG** de la barra superior, guardar la imagen del plot en `Pictures/Buz`.
6. Con **CSV**, exportar los datos y las estadísticas de sets a `Documents/Buz`.

## 8. Importar los `.DIP` originales de Rocscience

Copiar cualquier archivo `EXAMPCLIN.DIP`, `EXAMPPIT.DIP`, etc. al teléfono y abrirlo con **Abrir**.
Buz reconoce los 5 flags de orientación (dip/dipdir, strike/dip, strike+quadrant, trend/plunge,
plunge/trend), quantity, traverses y columnas extra.

## 9. Solución de problemas

| Problema | Causa | Solución |
|---|---|---|
| "SDK location not found" | Gradle no encontró el SDK | File → Project Structure → SDK Location, indicar la ruta |
| "Unsupported class file version" | JDK viejo | Usá JDK 17 (Android Studio → Settings → Build Tools → Gradle → Gradle JDK) |
| El teléfono no aparece | Falta driver ADB (Windows) | Instalar Google USB Driver desde SDK Manager |
| PNG no se guarda | Falta permiso de almacenamiento en API < 29 | Ajustes del teléfono → Buz → Permisos → Almacenamiento |
| El XLSX viene vacío | Está guardado como .xls viejo (no XML) | Reguardar como .xlsx desde Excel/LibreOffice |

## 10. Próximas mejoras sugeridas

- Firma release y publicación en Play Store (o distribución vía F-Droid).
- Ícono de la app (`res/mipmap-*`) — hoy usa el genérico de Android.
- Ventanas de sets con formas libres (polígonos, no solo rectángulos).
- Escritura de `.DIP` para exportar de vuelta al formato original.
- Uso de la brújula del teléfono para tomar mediciones en campo (si querés reactivarlo).
