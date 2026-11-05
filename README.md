# GulupaScan (v0.3.0) — Android & iOS

**Detección y cuantificación de daños fitosanitarios en frutos de gulupa (*Passiflora edulis* Sims f. *edulis*) mediante visión por computador y segmentación profunda en dos etapas (YOLO26n-seg + ONNX Runtime) 100 % en el dispositivo.**

Desarrollado en la **Universidad Nacional de Colombia (UNAL)**.

---

## Tabla de Contenidos
1. [Arquitectura del Repositorio](#1-arquitectura-del-repositorio)
2. [Stack Tecnológico por Sistema Operativo](#2-stack-tecnológico-por-sistema-operativo)
3. [Pipeline de Inteligencia Artificial (Modelos ONNX)](#3-pipeline-de-inteligencia-artificial-modelos-onnx)
4. [Selector de Resolución de Procesamiento (Media / Alta / Ultra Alta)](#4-selector-de-resolución-de-procesamiento-media--alta--ultra-alta)
5. [Guía Rápida: Compilar y Ejecutar en Android Studio](#5-guía-rápida-compilar-y-ejecutar-en-android-studio)
6. [Guía Rápida: Compilar y Ejecutar en Xcode (iOS / iPhone)](#6-guía-rápida-compilar-y-ejecutar-en-xcode-ios--iphone)
7. [Comandos para Subir / Actualizar en GitHub](#7-comandos-para-subir--actualizar-en-github)

---

## 1. Arquitectura del Repositorio

El repositorio contiene **ambas aplicaciones nativas** (Android y iOS) compartiendo exactamente los mismos modelos neuronales `.onnx`, activos gráficos institucionales, tipografía y lógica matemática:

```text
gulupascaner/
├── app/                                  # Aplicación Nativa Android (Kotlin + Jetpack Compose)
│   ├── build.gradle.kts                  # Configuración Gradle, CameraX, ONNX Runtime Android
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/models/                # Modelos neuronales ONNX integrados
│       │   ├── gulupa_fruto.onnx         # Etapa A: Segmentación de fruto (480×480, ~3.1 MB)
│       │   ├── gulupa_lesiones.onnx      # Etapa B: Segmentación de lesiones (640×640, ~3.2 MB)
│       │   ├── metadata_fruto.json
│       │   └── metadata_lesiones.json
│       ├── java/co/edu/unal/gulupascan/
│       │   ├── MainActivity.kt           # Navegación y cambio dinámico de idioma/tema
│       │   ├── AppViewModel.kt           # Estado reactivo y flujo de escaneo
│       │   ├── data/
│       │   │   ├── OnnxAnalyzer.kt       # Inferencia ONNX en dos etapas + Multi-Tile 2×2
│       │   │   ├── PdfReportGenerator.kt # Generador de informes técnicos PDF A4
│       │   │   └── SessionStore.kt       # Persistencia local (DataStore)
│       │   ├── domain/                   # Modelos de dominio, catálogo de daños y severidad 1–10
│       │   └── ui/                       # Pantallas Compose, animaciones de marca y temas
│       └── res/                          # Textos bilingües (ES/EN), fuente Patrick Hand y fotos
│
├── iosApp/                               # Aplicación Nativa iOS (Swift + SwiftUI)
│   ├── GulupaScan.xcodeproj/             # Proyecto oficial de Xcode listo para abrir
│   ├── project.yml                       # Especificación XcodeGen reproducible
│   └── GulupaScan/
│       ├── Info.plist                    # Permisos de cámara/fotos y registro de fuente
│       ├── Models/
│       │   └── Analysis.swift            # Dominio, paletas Morado/Verde, escala 1–10, resoluciones
│       ├── Services/
│       │   └── GulupaAnalyzer.swift      # Motor ONNX Runtime C++ Bridge + PDF Generator (CoreGraphics)
│       ├── Views/
│       │   └── GulupaScanApp.swift       # Interfaz completa SwiftUI (11 pantallas + animaciones)
│       └── Resources/                    # Modelos .onnx, fotos del equipo, logos y patrick_hand.ttf
│
├── build.gradle.kts                      # Configuración raíz de Gradle
├── settings.gradle.kts
└── README.md                             # Este documento
```

---

## 2. Stack Tecnológico por Sistema Operativo

| Componente | Android (`app/`) | iOS (`iosApp/`) |
| :--- | :--- | :--- |
| **Lenguaje** | Kotlin 2.2 | Swift 5.9+ |
| **Framework de UI** | Jetpack Compose + Material 3 | SwiftUI + Canvas vectorial |
| **Motor de Inferencia IA** | `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` | `onnxruntime-objc` (`1.24.2` vía Swift Package Manager) |
| **Cámara y Galería** | AndroidX CameraX (`1.4.1`) + PhotoPicker | `UIImagePickerController` + `PhotosPicker` (`PhotosUI`) |
| **Generación de PDF** | `android.graphics.pdf.PdfDocument` | `UIGraphicsPDFRenderer` (CoreGraphics A4) |
| **Persistencia Local** | Jetpack DataStore (`Preferences`) + JSON | `UserDefaults` + `FileManager` JSON |
| **Idiomas Soportados** | Español (`es`) / Inglés (`en`) en tiempo real | Español (`es`) / Inglés (`en`) en tiempo real |
| **Temas Visuales** | Morado Gulupa (`#9034BD` / `#5D2741`) y Verde Campo (`#005828`) | Morado Gulupa (`#9034BD` / `#5D2741`) y Verde Campo (`#005828`) |

---

## 3. Pipeline de Inteligencia Artificial (Modelos ONNX)

Ambas aplicaciones ejecutan exactamente el mismo pipeline secuencial **100 % offline en la CPU del teléfono**:

1. **Etapa A — Segmentación y Aislamiento del Fruto (`gulupa_fruto.onnx`)**:
   - Arquitectura: **YOLO26n-seg** exportada a ONNX (`opset 17`).
   - Entrada: Tensor `[1, 3, 480, 480]` normalizado en `[0, 1]` con *letterbox* gris `RGB(114, 114, 114)`.
   - Salida: `output0 [1, 300, 38]` (cajas, confianza, clase y 32 coeficientes) + `output1 [1, 32, 120, 120]` (prototipos de máscara).
   - Recorta el fruto principal con un margen del **10 %** (`margenRecorte = 0.10`), interpola bilinealmente la máscara y reemplaza el fondo exterior por **gris neutro `RGB(128, 128, 128)`** con holgura morfológica (`holguraFondo = 0.004`).

2. **Etapa B — Segmentación de Lesiones Epidérmicas (`gulupa_lesiones.onnx`)**:
   - Arquitectura: **YOLO26n-seg** entrenada sobre frutos aislados.
   - Entrada: Tensor `[1, 3, 640, 640]` del fruto aislado.
   - Clases del modelo:
     - `0`: `fruit` (superficie de referencia)
     - `1`: `scab` — **Sarna / Roña** (*Cladosporium cladosporioides*)
     - `2`: `anthracnose` — **Antracnosis** (*Colletotrichum gloeosporioides*)
     - `3`: `oil_spot` — **Mancha de aceite / Bacteriosis** (*Xanthomonas axonopodis* pv. *passiflorae*)

3. **Cálculo Matemático de Severidad y Escala Agronómica (1–10)**:
   $$\text{Severidad}_c (\%) = \frac{|\text{Máscara}_c \cap \text{Máscara}_{\text{fruto}}|}{|\text{Máscara}_{\text{fruto}}|} \times 100$$
   - Clasificación comercial automática:
     - **Fruto Sano (`< 1.0 %`)**: Calidad Extra — apto para exportación.
     - **Daño Leve (`1.0 – 5.0 %`)**: Categoría I — requiere selección en poscosecha.
     - **Daño Moderado (`5.0 – 15.0 %`)**: Categoría II — mercado nacional o agroindustria (pulpa).
     - **Daño Severo (`> 15.0 %`)**: Descarte fitosanitario.

---

## 4. Selector de Resolución de Procesamiento (Media / Alta / Ultra Alta)

Sin necesidad de reentrenar los modelos `.onnx`, el usuario puede elegir en la **pantalla de Cámara** o en **Configuración** entre tres modos de resolución antes de analizar:

- **Media (`1024 px`)**: Pre-escala la fotografía a un lado máximo de `1024 px` y ejecuta 1 pasada rápida (~0.4–0.8 s). Ideal para monitoreo rápido de lotes grandes.
- **Alta (`2048 px` — Recomendada)**: Conserva hasta `2048 px` en el recorte del fruto para obtener bordes de máscara nítidos con excelente velocidad (~0.8–1.4 s).
- **Ultra Alta (`4096 px + Tiling 2×2`)**: Conserva hasta `4096 px` y, además de la inferencia global del fruto, divide el recorte aislado en **4 cuadrantes solapados (2×2)** al 65 % del tamaño del fruto, ejecutando el Modelo B (`640×640`) sobre cada cuadrante y fusionando las máscaras de micro-lesiones a resolución nativa.

---

## 5. Guía Rápida: Compilar y Ejecutar en Android Studio

### Requisitos
- **Android Studio Ladybug / Meerkat** (o superior) con **JDK 17+**.
- Android SDK `35` (mínimo `API 26` / Android 8.0).

### Pasos (3 pasos)
1. Clona el repositorio:
   ```bash
   git clone https://github.com/jf-floresriera/gulupascaner.git
   cd gulupascaner
   ```
2. Abre la carpeta raíz `gulupascaner` en **Android Studio** y espera a que termine el **Gradle Sync**.
3. Selecciona tu teléfono Android o un emulador y pulsa **Run (`▶`)**, o genera el APK desde terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   El archivo instalable quedará en:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 6. Guía Rápida: Compilar y Ejecutar en Xcode (iOS / iPhone)

### Requisitos
- Un computador Mac con **Xcode 16+** instalado.
- Simulador de iOS instalado en Xcode (`Xcode -> Settings -> Components -> iOS Simulator`) o un iPhone físico conectado por cable.

### Pasos (3 pasos)
1. Abre directamente el proyecto de Xcode desde la terminal:
   ```bash
   open iosApp/GulupaScan.xcodeproj
   ```
2. Xcode descargará automáticamente el paquete `onnxruntime-objc` vía Swift Package Manager.
3. En la barra superior de Xcode, elige cualquier **iPhone Simulator** (por ejemplo *iPhone 16* o *iPhone Air*) y pulsa **Run (`▶` / `Cmd + R`)**.

### Compilar y lanzar en el Simulador por Terminal (un solo comando)
```bash
xcodebuild -project iosApp/GulupaScan.xcodeproj \
  -scheme GulupaScan \
  -sdk iphonesimulator \
  -configuration Debug \
  build CODE_SIGNING_ALLOWED=NO
```

*(Nota: Si en algún momento agregas nuevos archivos a `iosApp/GulupaScan/` y usas `xcodegen`, puedes regenerar el proyecto ejecutando `cd iosApp && xcodegen generate`).*

---

## 7. Comandos para Subir / Actualizar en GitHub

Copia y pega estos comandos en la terminal dentro de la carpeta del proyecto para subir tanto **Android (`app/`)** como **iOS (`iosApp/`)** y este **`README.md`** a tu repositorio de GitHub:

```bash
cd /Users/joaquinguillermoramirez-gil/AndroidStudioProjects/MyApplication

# 1. Verificar estado de los archivos de Android + iOS
git status

# 2. Agregar todos los cambios (Android, iOS, modelos ONNX y documentación)
git add app/ iosApp/ README.md build.gradle.kts settings.gradle.kts gradle/

# 3. Crear el commit descriptivo
git commit -m "feat(android,ios): versión 0.3.0 paridad completa Android/iOS, selector de resolución (Media/Alta/Ultra 2x2) y documentación"

# 4. Subir a GitHub (rama actual y/o main)
git push origin HEAD
```
