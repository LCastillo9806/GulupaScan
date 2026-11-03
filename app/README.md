# app — GulupaScan (Android · Kotlin)

Aplicación móvil nativa de GulupaScan. Es la adaptación a **Kotlin + Jetpack Compose** de la
app web PowderyVision7 (`proyecto-base/powderyvision7`), con el diseño del prototipo del
**Reto 2** (`docs/retos/reto-02-canvas-prototipo`).

```
app/
├── android/      ← Proyecto de Android Studio (Kotlin, Compose, Material 3)
└── backend/      ← api.py: API JSON que reutiliza el pipeline de PowderyVision7
```

## Arquitectura

```
 Celular (Kotlin)                                Servidor (Hugging Face / Cloud Run)
┌────────────────────────────┐   POST /api/analyze  ┌──────────────────────────────────┐
│ Cámara (CameraX) / Galería │ ───── imagen JPEG ──▶│ api.py                           │
│ Procesando…                │                      │  └─ pipeline.py (YOLO11 cls+seg) │
│ Tipos de daño / Detalle    │ ◀── JSON + imágenes ─│  └─ segment_mildew.py (Chan-Vese)│
│ Resultado / Detalles       │      (base64)        └──────────────────────────────────┘
│ Historial local (offline)  │
└────────────────────────────┘
```

- El **pipeline de visión no cambia**: `api.py` importa `pipeline.py` y `segment_mildew.py`
  tal cual y solo agrega la ruta `/api/analyze`. La web original (`/`) sigue funcionando.
- La app guarda el historial **en el teléfono** (funciona sin internet para consultar reportes).
- **Modo demostración**: reproduce los valores del mockup (roña 12.5 %, daño mecánico 5.5 %,
  trips 2.1 %) sin servidor. Útil para exponer en clase.

## Migración a iPhone

La guía para construir la versión de iOS está en [`docs/migracion-ios.md`](../docs/migracion-ios.md).
Para validar sus resultados contra los modelos, usa `scripts/referencia_onnx.py`.

## Cómo poner la foto de Diego (o cambiar una foto del equipo)

1. Recorta la foto en formato cuadrado, con la cara centrada, de unos 320 × 320 px.
2. Guárdala como `team_diego.jpg` en `app/android/app/src/main/res/drawable-nodpi/`. El nombre
   solo admite minúsculas, números y guion bajo.
3. En `app/android/app/src/main/java/co/edu/unal/gulupascan/ui/screens/InfoScreens.kt`, dentro de
   la lista `team`, cambia `null` por `R.drawable.team_diego` en la línea de Diego:

   ```kotlin
   TeamMember("Diego Humberto Lavado González", R.string.team_role_systems, R.string.team_fac_engineering, R.drawable.team_diego),
   ```
4. Compila de nuevo el APK. Para cambiar otra foto, basta con reemplazar el archivo `team_*.jpg`
   correspondiente conservando su nombre.

## Pantallas (según el mockup del Reto 2)

| Mockup | Pantalla en la app | Archivo |
|--------|--------------------|---------|
| 1 · Abrir app | Splash verde con logo | `ui/screens/AuthScreens.kt` |
| Iniciar sesión / Regístrate | Login local | `ui/screens/AuthScreens.kt` |
| 1 · Tomar o seleccionar foto | Cámara FOTO/GALERÍA, flash, cambiar cámara, consejos | `ui/screens/CameraScreen.kt` |
| 2 · Detección y segmentación | Procesando imagen + progreso + cancelar | `ui/screens/ScanFlowScreens.kt` |
| 3 · Tipos de daño identificados | Lista por tipo de daño con % de área | `ui/screens/ScanFlowScreens.kt` |
| 4 · Severidad por daño | Máscara con opacidad + escala 1-10 | `ui/screens/ScanFlowScreens.kt` |
| 5 · Resultado y recomendaciones | Clasificación general, resumen, guardar, compartir | `ui/screens/ScanFlowScreens.kt` |
| 5 · Detalles | Original, sin fondo, máscara, resultado, canal H, panel | `ui/screens/ScanFlowScreens.kt` |
| Perfil de usuario | Perfil + menú | `ui/screens/ManagementScreens.kt` |
| Mis reportes / Alertas | Historial (exporta CSV) y alertas | `ui/screens/ManagementScreens.kt` |
| Acerca de nosotros | Misión, equipo, cómo funciona, alcance | `ui/screens/ManagementScreens.kt` |

Lo que se conserva de la versión web: flujo cargar → analizar → resultados → detalles técnicos,
mensajes de error del pipeline (EN/ES según el idioma del teléfono), consejos para la foto
(antes el tutorial), alcance del modelo y exportación a CSV.

## Descargar el APK (sin Android Studio)

GitHub compila el APK solo, con el flujo `.github/workflows/build-apk.yml`:

- **Cada push** que cambie `app/android/` genera el APK: pestaña **Actions** › *Compilar APK* ›
  la ejecución más reciente › sección **Artifacts** › `GulupaScan-apk` (se descarga un .zip con
  el .apk adentro; requiere haber iniciado sesión en GitHub; se guarda 30 días).
- **Releases** (enlace público y permanente): *Releases › Draft a new release* › crea una
  etiqueta como `v0.3.0` › *Publish release*. En 5-10 minutos el APK aparece en *Assets*.
- Para instalarlo: abre el .apk en el celular y permite "Instalar apps de origen desconocido".

## Cómo ejecutar la app

1. Instala **Android Studio** (Koala 2024.1 o más reciente).
2. *File › Open…* y elige la carpeta `app/android`.
3. Espera a que termine *Gradle Sync* (descarga las dependencias la primera vez).
4. Conecta un celular con *Depuración USB* (o crea un emulador) y pulsa **Run ▶**.
5. Crea tu cuenta (se guarda solo en el teléfono).
6. Si aún no desplegaste la API: *Perfil › Configuración de cuenta › Modo demostración*.

Versiones: Kotlin 2.0.20 · AGP 8.5.2 · Gradle 8.9 · compileSdk 34 · minSdk 26 (Android 8.0).
Si Android Studio propone actualizar AGP/Gradle, puedes aceptarlo.

## Cómo desplegar la API (`backend/api.py`)

En el Space de Hugging Face `jf-floresriera/powderyvision7` (o en Cloud Run):

1. Copia `app/backend/api.py` junto a `app.py`.
2. En el `Dockerfile`, cambia la última línea a:
   ```dockerfile
   CMD gunicorn --bind 0.0.0.0:$PORT --workers 1 --timeout 120 --keep-alive 5 api:app
   ```
3. Haz commit en el Space. Prueba: `https://jf-floresriera-powderyvision7.hf.space/api/health`
   debe responder `{"ok": true, "api_version": 1}`.

La URL del servidor viene configurada en `app/android/app/build.gradle.kts`
(`DEFAULT_SERVER_URL`) y se puede cambiar dentro de la app.

### Respuesta de `/api/analyze`

```json
{
  "ok": true,
  "cls_name": "pea_powderymildew", "cls_conf": 0.93,
  "is_healthy": false, "severidad_pct": 12.4,
  "damages": [ {"key": "powdery_mildew", "pct": 12.4}, {"key": "healthy", "pct": 87.6} ],
  "images": { "original": "<b64>", "crop": "...", "leaf": "...", "mask": "...",
              "overlay": "...", "result": "...", "hue": "...", "panel": "..." }
}
```

## Cuando exista el modelo de gulupa

Ya está listo: `backend/gulupa_pipeline.py` se activa solo cuando el servidor tiene
`models/gulupa_seg.pt`. Pasos completos (clases a anotar, entrenamiento, dónde subir los pesos
y cómo verificar): [`docs/guia-modelos.md`](../docs/guia-modelos.md).

## Identidad visual

- Logo y letrero: `res/drawable-nodpi/logo_gulupa.png`, `word_gulupa.png`, `word_scan.png`
  (las palabras se colorean desde el código, ver `ui/components/Brand.kt`).
- Paleta: ciruela `#5D2741`, verde `#005828`, amarillo `#FEDC7A`, morado `#9034BD`,
  hoja `#00A85A`, fondo `#F7F7F7`.
- Transición de colores (`AnimatedBrandLockup`): en el splash y durante la inferencia.

## Temas

*Perfil › Configuración de cuenta › Tema de la app*: **Verde** (mockup) o **Morado gulupa**
(colores y formas orgánicas de la presentación). Se aplica al instante y queda guardado.

## Limitaciones conocidas

- El inicio de sesión es local (prototipo); para producción usar un backend (p. ej. Firebase Auth).
- El modelo actual reconoce **mildiu polvoso en hojas de arveja y tomate**, no daños de gulupa.
- El área en cm² solo aparece en modo demostración (el pipeline no conoce la escala de la foto).
