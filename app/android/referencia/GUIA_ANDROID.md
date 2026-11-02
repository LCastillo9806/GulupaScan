# Integración en GuluScan (Android · Kotlin)

Esta guía explica cómo integrar los modelos de daño epidérmico en la app GuluScan, hecha en Android Studio. Está escrita para quien vaya a programar la app.

La app recibe una foto y entrega el **tipo de daño** (Healthy, Scab, Anthracnose u Oil spot) y la **severidad**, es decir, el porcentaje del fruto cubierto por lesiones. Para eso usa dos modelos YOLO26n-seg que corren en el propio celular, sin conexión a internet.

```
foto → Modelo A (localiza el fruto) → recorte + fondo gris → Modelo B (lesiones) → daño + severidad
```

## 1. Dependencia

En `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
}
```

## 2. Modelos

Copia estos archivos a `app/src/main/assets/` (se generan con `python 06_exportar.py`):

| Archivo en assets | Origen | Tamaño aprox. |
|---|---|---|
| `gulupa_fruto.onnx` | `resultados/despliegue/gulupa_fruto_*/…_int8.onnx` | ~3 MB |
| `gulupa_lesiones.onnx` | `resultados/despliegue/gulupa_lesiones_*/…_int8.onnx` | ~3 MB |

La versión `_int8` pesa unas 4 veces menos y tiene la misma calidad, según la verificación en `*_metadata.json`. Si hiciera falta máxima fidelidad, se pueden usar los `.onnx` FP32.

Para evitar que Android comprima los modelos, agrega en `app/build.gradle.kts`:

```kotlin
android { androidResources { noCompress += "onnx" } }
```

## 3. Código

Copia `AnalizadorGulupa.kt` al paquete `co.edu.unal.guluscan.ml`. Las constantes de `ConfigGulupa` deben coincidir con `config.yaml` y con los `*_metadata.json`.

## 4. Uso

```kotlin
// Una sola instancia (cargar los modelos tarda ~1 s): por ejemplo, en el ViewModel
private val analizador by lazy { AnalizadorGulupa(appContext) }

// Siempre fuera del hilo principal
viewModelScope.launch(Dispatchers.Default) {
    val foto = cargarFotoEnderezada(uri)          // ver sección 5
    val r = analizador.analizar(foto)
    withContext(Dispatchers.Main) {
        textoClase.text = r.clase                                  // "Scab"
        textoSeveridad.text = "%.1f %%".format(r.severidadTotal)   // "12.4 %"
        imagen.setImageBitmap(r.recorte)
        if (!r.frutoLocalizado) mostrarAviso("No se encontró el fruto: centre la gulupa en la foto")
    }
}

override fun onCleared() { analizador.close() }
```

## 5. Enderezar la foto (obligatorio)

Las cámaras guardan la orientación en el EXIF y no en los píxeles. Los modelos se entrenaron con fotos enderezadas, así que la foto tiene que llegar derecha:

```kotlin
fun cargarFotoEnderezada(context: Context, uri: Uri): Bitmap {
    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
    val orientacion = context.contentResolver.openInputStream(uri).use {
        ExifInterface(it!!).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    val grados = when (orientacion) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (grados == 0f) return bitmap
    val m = Matrix().apply { postRotate(grados) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
}
```

Requiere `implementation("androidx.exifinterface:exifinterface:1.3.7")`.

## 6. Cómo se validó

- `06_exportar.py` incluye `inferencia_referencia`, una implementación en NumPy + ONNX Runtime de los mismos pasos que `AnalizadorGulupa.kt`: letterbox, decodificación de `[1,300,38]` y de los prototipos `[1,32,h,w]`, umbral 0.5, recorte a la caja y eliminación del relleno.
- Esa referencia dio **las mismas detecciones que Ultralytics en el 100 % de las imágenes** sobre el mismo archivo ONNX.
- El código Kotlin **no se compiló en este equipo** (no hay Android SDK instalado). Al integrarlo, compara 5 o 10 fotos contra `python 05_predecir.py`: la clase debe coincidir y la severidad diferir menos de 1 punto porcentual. Pueden aparecer diferencias mínimas en los bordes por el remuestreo.

## 7. Detalles técnicos del modelo

| | Modelo A (fruto) | Modelo B (lesiones) |
|---|---|---|
| Entrada | `[1, 3, 480, 480]` float32, RGB, 0-1 | `[1, 3, 640, 640]` float32, RGB, 0-1 |
| `output0` | `[1, 300, 38]`: x1, y1, x2, y2, confianza, clase, 32 coeficientes | igual |
| `output1` | `[1, 32, 120, 120]` prototipos | `[1, 32, 160, 160]` prototipos |
| Clases | 0 `fruit` | 0 `fruit`, 1 `scab`, 2 `anthracnose`, 3 `oil_spot` |
| NMS | No requiere (modelo end-to-end) | No requiere |

Máscara de cada detección: `sigmoide(coeficientes · prototipos)`, ampliada a la entrada, recortada a la caja y con umbral 0.5.
