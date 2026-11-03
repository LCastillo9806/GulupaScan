# Migración de GulupaScan de Android (Kotlin) a iPhone (Swift)

Guía para quien vaya a construir la versión de iPhone a partir de este repositorio. Explica qué se reutiliza, qué se reescribe, en qué orden y cómo comprobar que la app de iPhone da los mismos resultados que la de Android.

> **Estado de esta guía.** Se escribió y se verificó en Windows, sin Mac. Los valores del análisis salen del código y de los metadatos del repositorio, y la API de ONNX Runtime se tomó de su documentación oficial en noviembre de 2026. **El código Swift de abajo no se ha compilado ni ejecutado**: es un punto de partida, y el paso 1 sirve para comprobarlo.

## 1. Qué se necesita

| Requisito | Detalle |
|---|---|
| Mac con Xcode | Xcode solo funciona en macOS. No se puede compilar para iPhone desde Windows |
| iPhone real | El simulador de iOS no tiene cámara; hace falta para probar la captura |
| Cuenta de Apple | Una cuenta gratuita sirve para instalar en su propio iPhone con limitaciones. Para TestFlight o App Store se necesita el Apple Developer Program, de pago |
| Git LFS | Los modelos `.onnx` están en LFS. Ejecute `git lfs install` antes de clonar |
| iOS mínimo | iOS 15 o superior, por el paquete de ONNX Runtime para Swift Package Manager |

## 2. Qué se reutiliza y qué se reescribe

Android y iOS no comparten código en este proyecto: **toda la app se reescribe en Swift**. Lo que se conserva son los modelos, los valores del análisis, los textos, las imágenes y el comportamiento.

**Se copia tal cual desde `app/android/app/src/main/`:**

| Recurso | Ruta | Uso en iOS |
|---|---|---|
| Modelos | `assets/gulupa_fruto.onnx`, `assets/gulupa_lesiones.onnx` | Agregarlos al target de Xcode (Copy Bundle Resources) |
| Metadatos de los modelos | `assets/metadata_*.json`, `assets/labels_*.txt` | Referencia de clases, entradas y salidas |
| Logo y letrero | `res/drawable-nodpi/logo_gulupa.png`, `word_gulupa.png`, `word_scan.png` | Asset Catalog |
| Fotos del equipo | `res/drawable-nodpi/team_*.jpg` | Asset Catalog |
| Tipografía | `res/font/patrick_hand.ttf` | Agregar al target y declarar en `Info.plist` (licencia OFL en `app/android/LICENSE-PatrickHand-OFL.txt`) |

**Se traduce con cuidado:**

| Recurso | Ruta | Destino en iOS |
|---|---|---|
| Textos en español | `res/values/strings.xml` | String Catalog (`Localizable.xcstrings`) |
| Textos en inglés | `res/values-en/strings.xml` | El mismo String Catalog |

Al convertir los textos, los marcadores `%1$s` y `%1$d` pasan a `%1$@` y `%1$d`. Quite también los escapes de Android (`\'`, `&amp;`).

## 3. Equivalencias de tecnología

| Función | Android (actual) | iOS (propuesto) |
|---|---|---|
| Lenguaje e interfaz | Kotlin, Jetpack Compose, Material 3 | Swift, SwiftUI |
| Navegación | Navigation Compose | `NavigationStack` y `TabView` |
| Estado | `ViewModel` y `StateFlow` | `ObservableObject` con `@Published` (iOS 15) |
| Cámara | CameraX | AVFoundation (`AVCaptureSession`, `AVCapturePhotoOutput`) |
| Galería | Photo Picker | `PHPickerViewController` (iOS 15) |
| Inferencia | ONNX Runtime Android 1.20.0 | ONNX Runtime con Swift Package Manager |
| Preferencias | DataStore | `UserDefaults`; la contraseña en Keychain |
| Historial | Archivos y `analysis.json` | `FileManager` y `Codable`, con la misma estructura |
| Imágenes | Coil | `UIImage` / `Image` |
| PDF | `PdfDocument` | `UIGraphicsPDFRenderer` |
| Compartir | `Intent.ACTION_SEND` | `UIActivityViewController` |
| Guardar en galería | `MediaStore` | `PHPhotoLibrary` |
| Red (servidor opcional) | OkHttp | `URLSession` |
| Animaciones | `Canvas`, `rememberInfiniteTransition` | `Canvas` con `TimelineView` |

**Permisos en `Info.plist`:** `NSCameraUsageDescription` para la cámara y `NSPhotoLibraryAddUsageDescription` para guardar imágenes. `PHPickerViewController` no pide permiso para leer la galería.

## 4. El análisis que debe mantenerse idéntico

Esta es la parte crítica. Cualquier diferencia cambia los resultados, aunque la app parezca funcionar. La implementación de referencia es [`OnnxAnalyzer.kt`](../app/android/app/src/main/java/co/edu/unal/gulupascan/data/OnnxAnalyzer.kt).

| Constante | Valor | Dónde se usa |
|---|---|---|
| `LADO_MAXIMO_FOTO` | 2048 px | Lado mayor de la foto; igual que en el entrenamiento |
| Calidad JPEG | 95 | Al preparar la foto |
| `LADO_FRUTO` | 480 px | Entrada del Modelo A |
| `LADO_LESIONES` | 640 px | Entrada del Modelo B |
| `CONFIANZA` | 0,25 | Detección mínima en ambos modelos |
| `UMBRAL_MASCARA` | 0,5 | Binarización de la máscara |
| `MARGEN_RECORTE` | 10 % | Margen alrededor de la caja del fruto |
| `HOLGURA_FONDO` | 0,4 % | Dilatación de la máscara, sobre el lado mayor del recorte (mínimo 1 px) |
| Color de fondo | (128, 128, 128) | Lo que queda fuera de la máscara del fruto |
| Relleno letterbox | (114, 114, 114) | Bandas de la entrada al modelo |
| `UMBRAL_SANO` | 0,5 % | Severidad total por debajo de la cual el fruto es sano |

**Entradas y salidas de los modelos**

| | Modelo A (fruto) | Modelo B (lesiones) |
|---|---|---|
| Entrada `images` | `[1, 3, 480, 480]` float32 | `[1, 3, 640, 640]` float32 |
| `output0` | `[1, 300, 38]` | `[1, 300, 38]` |
| `output1` | `[1, 32, 120, 120]` | `[1, 32, 160, 160]` |
| Clases | 0 `fruit` | 0 `fruit`, 1 `scab`, 2 `anthracnose`, 3 `oil_spot` |

Cada fila de `output0` contiene `x1, y1, x2, y2, confianza, clase` y 32 coeficientes. Los modelos son end-to-end: **no aplique NMS**. Las entradas van en RGB, de 0 a 1, en formato planar: primero todo R, luego G, luego B.

**Pasos, en orden**

1. **Preparar la foto.** Corrija la orientación EXIF y limite el lado mayor a 2048 px.
2. **Modelo A.** Aplique letterbox a 480 px centrado (`padX = (480 - anchoEscalado) / 2`, división entera) y ejecute. Conserve la detección de clase 0 con mayor confianza. Si no hay ninguna, **pida otra foto y no entregue resultado**.
3. **Decodificar la máscara.** Calcule `sigmoide(Σ coefₖ · prototipoₖ)`, amplíe con interpolación bilineal a la entrada, conserve solo dentro de la caja y aplique el umbral 0,5.
4. **Recortar.** Pase la caja a coordenadas de la foto (`(x - padX) / escala`) y agregue el margen del 10 % del lado mayor de la caja, limitado a los bordes de la foto.
5. **Fondo gris.** Dilate la máscara con el radio de `HOLGURA_FONDO` y pinte de gris 128 todo lo que quede fuera.
6. **Modelo B.** Aplique letterbox a 640 px al recorte, ejecute y decodifique cada máscara igual que en el paso 3.
7. **Severidad.** Una las máscaras por clase y calcule:

   ```
   fruto    = máscaras de clase 0 ∪ todas las lesiones
   severidad = 100 × área(lesiones ∩ fruto) / área(fruto)
   ```

   Cada lesión usa la misma fórmula con su máscara. Si `área(fruto)` es 0, **pida otra foto**: nunca informe "sano" en ese caso. Si la severidad es menor a 0,5 %, la clase es sano; si no, es la lesión de mayor área.
8. **Clasificación y escala.** Copie [`Severity.kt`](../app/android/app/src/main/java/co/edu/unal/gulupascan/domain/Severity.kt) sin cambiar los umbrales.

### Errores típicos al pasar a iOS

- **Orden de canales.** `CGContext` suele entregar RGBA o BGRA con alfa premultiplicado. Dibuje en un contexto de formato conocido y compruebe que el rojo del tensor sea realmente el rojo de la foto. Un error aquí no produce fallos: solo resultados peores.
- **Orientación.** `UIImage.cgImage` ignora `imageOrientation`. Redibuje la imagen para fijar la orientación antes de leer los píxeles.
- **Escala de pantalla.** Cree los contextos de dibujo con `scale = 1`, o `UIGraphicsImageRenderer` multiplicará los píxeles por 2 o 3.
- **Interpolación.** Use calidad media o alta al escalar y compare con el script de referencia. Puede causar diferencias pequeñas en los bordes.

## 5. Punto de partida en Swift (sin compilar)

Agregue en Xcode el paquete `https://github.com/microsoft/onnxruntime-swift-package-manager`, producto `onnxruntime`. La etiqueta más reciente vista fue la 1.24.2; revise si hay una nueva. Según la documentación, la API de Objective-C se usa desde Swift con un bridging header que contenga `#import <onnxruntime.h>`; si el paquete expone otro módulo, siga el mensaje de Xcode.

```swift
/// Modelo de segmentación: entrada `lado`×`lado` y salidas output0 y output1.
final class ModeloSegmentacion {
    private let sesion: ORTSession
    private let lado: Int

    init(env: ORTEnv, archivo: String, lado: Int) throws {
        guard let ruta = Bundle.main.path(forResource: archivo, ofType: "onnx") else {
            throw CocoaError(.fileNoSuchFile)
        }
        let opciones = try ORTSessionOptions()
        try opciones.setIntraOpNumThreads(4)
        self.sesion = try ORTSession(env: env, modelPath: ruta, sessionOptions: opciones)
        self.lado = lado
    }

    /// `planar` contiene 3·lado·lado valores Float32: todo R, luego G, luego B, de 0 a 1.
    func ejecutar(planar: [Float32]) throws -> (filas: [Float32], protos: [Float32]) {
        let datos = NSMutableData(bytes: planar, length: planar.count * MemoryLayout<Float32>.stride)
        let forma: [NSNumber] = [1, 3, NSNumber(value: lado), NSNumber(value: lado)]
        let entrada = try ORTValue(tensorData: datos, elementType: .float, shape: forma)

        let nombreEntrada = try sesion.inputNames()[0]
        let nombres = try sesion.outputNames()   // Compruebe el orden: output0 y output1
        let salida = try sesion.run(
            withInputs: [nombreEntrada: entrada],
            outputNames: Set(nombres),
            runOptions: nil
        )
        guard let o0 = salida[nombres[0]], let o1 = salida[nombres[1]] else {
            throw CocoaError(.coderValueNotFound)
        }
        return (try leer(o0), try leer(o1))
    }

    private func leer(_ valor: ORTValue) throws -> [Float32] {
        let datos = try valor.tensorData() as Data
        return datos.withUnsafeBytes { Array($0.bindMemory(to: Float32.self)) }
    }
}

// Cree un solo ORTEnv antes de los modelos y consérvelo mientras se usen:
// let env = try ORTEnv(loggingLevel: .warning)
```

Ejecute la inferencia fuera del hilo principal, por ejemplo en `Task.detached`. La fila `i` de `output0` empieza en `i * 38`, y el prototipo `k` de `output1` empieza en `k * alto * ancho`.

## 6. La cámara: no repita los errores de Android

Estas decisiones corrigieron fallos reales de la versión Android. Están en [`CameraScreen.kt`](../app/android/app/src/main/java/co/edu/unal/gulupascan/ui/screens/CameraScreen.kt).

| Requisito | En Android | En iOS |
|---|---|---|
| Máxima calidad | `CAPTURE_MODE_MAXIMIZE_QUALITY` | `sessionPreset = .photo` y `photoQualityPrioritization = .quality` |
| Resolución | Mayor 4:3 disponible | Formato 4:3 de mayor resolución de la cámara trasera |
| Lo que se ve es lo que se captura | Vista previa en caja 3:4 | `AVCaptureVideoPreviewLayer` en una caja 3:4 |
| Enfoque antes de disparar | `startFocusAndMetering`, con espera máxima de 2 s | `focusPointOfInterest` y `exposurePointOfInterest`; dispare al terminar el ajuste, con la misma espera máxima |
| Tocar para enfocar | Convierte el toque en un punto de medición | `captureDevicePointConverted(fromLayerPoint:)` |
| Avisar si falla | Mensaje al no lograr enfocar o capturar | Igual |

Las fotos pueden llegar en HEIC. `UIImage` las lee sin problema, siempre que el flujo siguiente reciba la misma imagen preparada del paso 1.

## 7. Pantallas a reconstruir

| Pantalla | Archivo en Android |
|---|---|
| Inicio, inicio de sesión y registro | `ui/screens/AuthScreens.kt` |
| Cámara y consejos | `ui/screens/CameraScreen.kt` |
| Procesando, daños, detalle, resultado y detalles técnicos | `ui/screens/ScanFlowScreens.kt` |
| Historial, alertas, perfil y configuración | `ui/screens/ManagementScreens.kt` |
| Acerca de nosotros, información técnica e instrucciones de uso | `ui/screens/InfoScreens.kt` |
| Logo animado y paleta | `ui/components/Brand.kt` |
| Componentes comunes | `ui/components/Components.kt` |
| Temas verde y morado | `ui/theme/Theme.kt` |
| Reporte PDF | `data/PdfReportGenerator.kt` |
| Catálogo de daños | `domain/DamageCatalog.kt` |
| Datos de un análisis | `domain/Analysis.kt` |

**Idioma y tema.** Deben cambiar al instante desde Configuración, como en Android. En SwiftUI, guarde el idioma en el estado de la app y aplique `.environment(\.locale, ...)` en la raíz. Los textos generados fuera de las vistas, como el PDF y los errores, deben usar también el idioma elegido: en Android este fue el punto que se quedaba en español.

**Contraseña.** La versión Android guarda localmente un SHA-256 sin sal aleatoria, aceptable solo para un prototipo. En iOS, use Keychain y, si la cuenta va a ser real, un servicio de autenticación.

## 8. Orden de trabajo recomendado

1. **Comprobar los modelos.** Proyecto vacío con el paquete de ONNX Runtime y los dos `.onnx`. Ejecute ambos con una imagen gris e imprima la forma de las salidas. Si esto falla, nada más importa; hágalo primero.
2. **Análisis sin interfaz.** Implemente la sección 4 sobre fotos incluidas en el proyecto y compare con el script de referencia.
3. **Cámara y galería** conectadas al análisis, con la pantalla de resultado más simple.
4. **Flujo completo:** resultado, detalle, historial y PDF.
5. **Pantallas informativas**, temas e idiomas.
6. **Prueba en campo** con varios iPhone y distribución por TestFlight.

## 9. Cómo validar los resultados

No basta con que la app "funcione". Compare siempre con la referencia:

```
pip install onnxruntime opencv-python numpy
python scripts/referencia_onnx.py foto1.jpg foto2.jpg
```

**Criterio de aceptación:** con al menos 10 fotos de distintas clases, la clase debe coincidir y la severidad total debe diferir **menos de 1 punto porcentual**. Es el mismo criterio de [`GUIA_ANDROID.md`](../app/android/referencia/GUIA_ANDROID.md).

Valor de control ya medido, con la foto `TEST/Scab_ts/20250927_150409.jpg` del conjunto de datos:

| Implementación | Clase | Severidad total | Lado del recorte |
|---|---|---|---|
| Referencia en Python | Scab | 13,91 % | 644 px |
| App Android (emulador) | Scab | 13,1 % | 645 px |

Después, compare fotos del mismo fruto tomadas con la cámara de la app y con la cámara normal del iPhone. Si las de la app dan peores resultados, revise primero el enfoque y el tamaño del recorte, que la app muestra en los detalles técnicos.

## 10. Limitaciones que se heredan

La migración no mejora el modelo. En la partición de prueba (142 fotos), la exactitud por imagen fue de 80,3 %. El 44 % de las fotos llamadas sanas tenían lesiones, y solo se reconoció el 52 % de los frutos con mancha de aceite. Mantenga las advertencias de la pantalla de información técnica en la versión de iPhone.

Los rangos de clasificación del código (10 % y 30 %) no coinciden con los textos de recomendación (5 % y 20 %). Conviene que lo resuelva el equipo de fitopatología antes de duplicarlo en iOS.
