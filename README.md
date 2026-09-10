# Buz

App Android (Kotlin + Jetpack Compose) para análisis estereográfico de discontinuidades.
Reimplementación desde cero inspirada en DIPS de Rocscience. **No** reutiliza código ni binario del original.
"Buz" = **buzamiento**.

## Estado final del scaffold

| Módulo | Estado |
|---|---|
| Proyecciones equiareal (Schmidt) / equiangular (Wulff) | ✅ |
| Ploteo de polos + planos (círculos mayores) | ✅ |
| Contornos Kamb + marching squares (σ-levels) | ✅ |
| Estadística Fisher (media, k, cono 95%) | ✅ |
| K-means axial para sets automáticos | ✅ |
| Diagrama de rosetas (con pesos) | ✅ |
| Corrección de Terzaghi (cap 15°) | ✅ |
| Import `.DIP` (formato 4.x/5.x) | ✅ |
| Import CSV/TSV (columnas por nombre) | ✅ |
| Import XLSX (sin Apache POI) | ✅ |
| Ventanas de sets dibujables con el dedo | ✅ |
| Coloreo de polos por set + estadística Fisher por set | ✅ |
| Export PNG del plot (Pictures/Buz) | ✅ |
| Export CSV de datos y estadísticas (Documents/Buz) | ✅ |

## Formatos de entrada

| Extensión | Notas |
|---|---|
| `.dip` | Parser propio, respeta títulos, traverses, flag orientación 0–4, quantity, columnas extra |
| `.csv` / `.tsv` / `.txt` | Detecta `,` `;` `\t`; coma decimal OK; columnas por nombre: `dip`, `dipdir`, `strike`, `trend`, `plunge`, `quantity`, `traverse`. Sin nombres → posicional (col 0 y 1) |
| `.xlsx` | Descomprime el ZIP y parsea SAX sobre `sharedStrings.xml` y la primera hoja. Sin dependencias externas |

Ejemplo: [examples/ejemplo.csv](examples/ejemplo.csv)

## Estructura

```
app/src/main/java/com/buz/
├── core/                    # Puro Kotlin, testeable sin Android
│   ├── Orientation.kt       # Vec3, Pole, conversiones dip/strike/trend/plunge
│   ├── Projection.kt        # Equiareal + equiangular, círculos mayores/menores
│   ├── Statistics.kt        # Fisher + densidad Kamb
│   ├── ClusterSets.kt       # k-means axial
│   ├── Rose.kt              # Binning rosetas
│   ├── Terzaghi.kt          # 1/sin θ con cap
│   ├── SetWindows.kt        # Ventanas rectangulares + asignación
│   ├── Export.kt            # Serialización CSV
│   ├── DipFile.kt           # Parser .DIP
│   ├── CsvFile.kt           # Parser CSV + mapping por nombre
│   └── XlsxFile.kt          # Parser .xlsx standalone
├── ui/
│   ├── MainActivity.kt      # 4 pestañas + botones Abrir / PNG / CSV
│   ├── StereonetView.kt     # Canvas + gesture para ventanas de sets
│   ├── RoseView.kt          # Diagrama de rosas
│   ├── PngRender.kt         # Render off-screen del estereográfico
│   └── ExportUtils.kt       # Bitmap + MediaStore (scoped storage)
└── src/test/…               # JUnit: math, imports, sets, export
examples/ejemplo.csv         # Datos de prueba
```

## Instalación y prueba

Ver [INSTALL.md](INSTALL.md) para pasos detallados.


