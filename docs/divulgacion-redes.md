# Kit de Divulgación Científica, Redes Sociales (`Starstruck`) y Respuestas Técnicas (`Galaxy Brain`)

Este documento reúne:
1. **Publicaciones listas para LinkedIn y Reddit (Español e Inglés)** para atraer estrellas al repositorio (**Insignia Starstruck**).
2. **Preguntas y Respuestas Magistrales para GitHub Discussions** para obtener respuestas aceptadas (**Insignia Galaxy Brain**).
3. **Guía rápida de Coautoría en Git** (**Insignia Pair Extraordinaire**).

---

## 1. Publicación para LinkedIn (Español)

> **🌱📱 ¡Lanzamos GulupaScan v0.3.0 (Open Source): Inteligencia Artificial 100 % Offline para el diagnóstico fitosanitario de gulupa (*Passiflora edulis* Sims) en Android e iOS!**
>
> En Colombia, la gulupa es una de las frutas de exportación más importantes, pero enfermedades epidérmicas como la **Sarna (*Cladosporium cladosporioides*)**, la **Antracnosis (*Colletotrichum gloeosporioides*)** y la **Mancha de aceite (*Xanthomonas axonopodis*)** generan pérdidas críticas en cosecha y poscosecha.
>
> Desde la **Universidad Nacional de Colombia (UNAL)** desarrollamos **GulupaScan**, una aplicación móvil nativa (Jetpack Compose + SwiftUI) que ejecuta en el borde (**Edge AI**, sin necesidad de internet en la finca) un pipeline en cascada de dos redes de segmentación **YOLO26n-seg cuantizadas a INT8 (ONNX Runtime)**:
>
> ✅ **Etapa A (`gulupa_fruto.onnx` · 3.23 MB · IoU 98.13 %)**: Localiza el fruto, recorta con margen del 10 % y aísla el fondo natural reemplazándolo por gris neutro `RGB(128,128,128)`.
> ✅ **Etapa B (`gulupa_lesiones.onnx` · 3.30 MB · IoU 90.22 %)**: Segmenta píxel a píxel cada patología sobre la corteza, calcula el porcentaje exacto de área afectada y lo ubica en una **escala fitosanitaria del 1 al 10**.
> ✅ **Modo Multi-Escala `2×2` (Ultra)**: Permite capturar en la resolución nativa del sensor del teléfono y analizar 4 cuadrantes superpuestos para alcanzar una resolución neuronal efectiva de `1280×1280` píxeles sin reentrenar los modelos.
> ✅ **Exportación de Informes PDF** con evidencia visual y recomendaciones agronómicas.
>
> 🔗 **Repositorio completo (Código Android, iOS, Backend Docker/Conda y Modelos ONNX):**
> 👉 https://github.com/jf-floresriera/gulupascaner
>
> ⭐ **¡Si trabajas en visión por computador, fitopatología, agricultura digital o Edge AI, te invitamos a explorar el repositorio y regalarle una estrella (`Star`) en GitHub!**
>
> `#AgTech` `#ComputerVision` `#PlantPathology` `#EdgeAI` `#ONNX` `#YOLO` `#AndroidDev` `#SwiftUI` `#UNAL` `#OpenSource`

---

## 2. LinkedIn & Reddit Post (English — `r/MachineLearning`, `r/computervision`, `r/Agriculture`)

> **Title:** `[P] GulupaScan v0.3.0: On-device dual-stage YOLO26n-seg INT8 ONNX pipeline for passion fruit disease segmentation & severity quantification (Android & iOS, 100% offline)`
>
> Hi everyone! 👋
>
> We just open-sourced **GulupaScan v0.3.0**, an end-to-end mobile Edge AI system developed at the **National University of Colombia (UNAL)** for automated detection and pixel-level severity quantification of epidermal diseases in purple passion fruit (*Passiflora edulis* Sims):
> - **Scab** (*Cladosporium cladosporioides*)
> - **Anthracnose** (*Colletotrichum gloeosporioides*)
> - **Bacterial Oil Spot** (*Xanthomonas axonopodis* pv. *passiflorae*)
>
> ### Technical Highlights:
> - **Dual-Stage Cascade Architecture (`ONNX Runtime` INT8)**:
>   1. **Stage A (`gulupa_fruto.onnx`, `480×480`, 3.23 MB)**: Segments the fruit boundary in the full camera frame (`IoU = 0.9813` vs. PyTorch FP32), crops with a 10% margin, dilates the mask by `0.4%`, and masks out complex field backgrounds (leaves, soil, hands) with neutral `RGB(128,128,128)`.
>   2. **Stage B (`gulupa_lesiones.onnx`, `640×640`, 3.30 MB)**: Segments individual lesions on the isolated fruit crop (`IoU = 0.9022`, `100%` top-class match in INT8) and computes exact affected surface percentage:
>      $$\text{Severity (\%)} = 100 \times \frac{|\text{Lesions} \cap \text{Fruit}|}{|\text{Fruit} \cup \text{Lesions}|}$$
> - **Training-Free `2×2` Multi-Tile Ultra Mode**: Captures at full native sensor resolution and runs Stage B across 4 overlapping 60% quadrants + 1 global view, reaching **`1280×1280` effective neural resolution** for micro-lesions.
> - **Cross-Platform Native Apps**: Full feature parity across **Android** (Kotlin, Jetpack Compose, CameraX) and **iOS** (Swift, SwiftUI).
>
> 🔗 **GitHub Repository:** https://github.com/jf-floresriera/gulupascaner
>
> If you find this useful for your research in agricultural computer vision or mobile ONNX deployment, **please consider giving the repo a ⭐ Star on GitHub!** Feedback and PRs are very welcome!

---

## 3. Guía para Ganar la Insignia `Galaxy Brain` en GitHub Discussions

Ya dejamos habilitada la pestaña **Discussions** en tu repositorio `jf-floresriera/gulupascaner`. Para desbloquear **Galaxy Brain** (se obtiene cuando **2 respuestas tuyas** en categoría `Q&A` son marcadas como *Marked as Answer*):

### Pregunta 1 (Para que un compañero la publique en la categoría `Q&A`):
> **Título:** *¿Cómo se decodifican las máscaras de `output0` y `output1` en los modelos `YOLO26n-seg` ONNX sin depender de Ultralytics en Android/iOS?*

### Respuesta Magistral Lista para Pegar:
```markdown
Excelente pregunta. Los modelos integrados en **GulupaScan** (`gulupa_fruto.onnx` y `gulupa_lesiones.onnx`) fueron exportados con cabeceras *end-to-end* de segmentación de instancias (`YOLO26n-seg`), por lo que producen directamente dos tensores sin requerir NMS clásico externo:

1. **`output0` con forma `[1, 300, 38]`**:
   - Columnas `0..3`: coordenadas de la caja `[x1, y1, x2, y2]` en el espacio *letterbox* (`480×480` en Etapa A o `640×640` en Etapa B).
   - Columna `4`: puntaje de confianza (`>= 0.25`).
   - Columna `5`: índice de clase (`0 = fruit`, `1 = scab`, `2 = anthracnose`, `3 = oil_spot`).
   - Columnas `6..37`: vector de **32 coeficientes de máscara** $\mathbf{c} \in \mathbb{R}^{32}$.
2. **`output1` con forma `[1, 32, H_p, W_p]`**:
   - Contiene los **32 prototipos espaciales** ($120\times 120$ en Etapa A y $160\times 160$ en Etapa B).

### Paso a paso matemático y en Python/NumPy:

Para cada detección que supere el umbral de confianza (`0.25`):
1. Se calcula el producto punto entre los 32 coeficientes y los 32 prototipos y se aplica la función sigmoide $\sigma(z) = \frac{1}{1 + e^{-z}}$.
2. Se interpola bilinealmente al tamaño del *letterbox* (`480×480` o `640×640`).
3. Se recorta la máscara a los límites de su caja delimitadora `[x1, y1, x2, y2]` y se binariza con umbral `0.5`:

```python
import cv2
import numpy as np

def decodificar_mascara(det: np.ndarray, protos: np.ndarray, lado: int, px: int, py: int) -> np.ndarray:
    # 1. Combinación lineal de los 32 prototipos + sigmoide
    logits = np.tensordot(det[6:38], protos, axes=1)
    mascara_proto = 1.0 / (1.0 + np.exp(-logits))

    # 2. Interpolación bilineal al tamaño del tensor de entrada (480 o 640)
    mascara_full = cv2.resize(mascara_proto, (lado, lado), interpolation=cv2.INTER_LINEAR) > 0.5

    # 3. Restricción a la caja delimitadora y zona útil del letterbox
    recortada = np.zeros_like(mascara_full, dtype=bool)
    x0, y0 = max(int(det[0]), px), max(int(det[1]), py)
    x1, y1 = min(int(np.ceil(det[2])), lado - px), min(int(np.ceil(det[3])), lado - py)
    recortada[y0:y1, x0:x1] = mascara_full[y0:y1, x0:x1]
    return recortada
```

Con este procedimiento obtenemos un **100 % de coincidencia exacta** frente al tensor original de PyTorch tanto en `scripts/referencia_onnx.py` como en `OnnxAnalyzer.kt` y `GulupaAnalyzer.swift`.
```
*(Cuando tu compañero haga clic en **"Mark as answer"**, sumarás inmediatamente para la insignia **Galaxy Brain**).*
