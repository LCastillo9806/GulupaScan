# Modelo A (`gulupa_fruto.onnx`) — Localización y Aislamiento del Fruto

## 1. Descripción General
- **Proyecto**: *Detection and Classification of Epidermal Damage in Purple Passion Fruit (`Passiflora edulis` Sims)*
- **Rol en el Pipeline**: **Etapa A** — Localiza el fruto principal en la fotografía completa y genera la máscara binaria para aislar el fruto del fondo natural (hojas, ramas, mano o mesa).
- **Arquitectura**: `YOLO26n-seg` (End-to-End Instance Segmentation, sin NMS externo)
- **Fecha de exportación**: `2026-10-30T19:01:23`
- **Runtime móvil**:
  - Android: `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`
  - iOS: `onnxruntime-objc:1.24.2`

## 2. Especificación de Tensores

| Tensor | Nombre | Forma (Shape) | Tipo | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| **Entrada** | `images` | `[1, 3, 480, 480]` | `float32` | Imagen RGB normalizada `pixel / 255.0` (`0.0 – 1.0`) con *letterbox* centrado y relleno `RGB(114, 114, 114)` |
| **Salida 0** | `output0` | `[1, 300, 38]` | `float32` | 300 candidatos: `[x1, y1, x2, y2, confianza, clase, coef_0 ... coef_31]` |
| **Salida 1** | `output1` | `[1, 32, 120, 120]` | `float32` | 32 prototipos espaciales de máscara de `120×120` píxeles |

### Clases del Modelo A
| ID | Etiqueta | Descripción |
| :---: | :--- | :--- |
| `0` | `fruit` | Fruto completo de gulupa (*Passiflora edulis* f. *edulis*) |

## 3. Preprocesamiento, Decodificación y Postprocesamiento
1. **Filtro de confianza**: Se conservan las detecciones con `confianza >= 0.25` y se selecciona la detección de clase `0 (fruit)` de mayor confianza.
2. **Decodificación de máscara**:
   $$\text{Máscara}_{120\times 120} = \sigma\left(\sum_{k=0}^{31} \text{coef}_k \cdot \text{proto}_k\right)$$
3. **Interpolación bilineal y recorte**: Se amplía de `120×120` a `480×480` mediante interpolación bilineal, se restringe a la caja `[x1, y1, x2, y2]` y se binariza con umbral `0.5`.
4. **Recorte con margen y aislamiento de fondo**:
   - Se añade un margen de **10 %** (`recorte_margen = 0.10`) del lado mayor de la caja delimitadora.
   - Se dilata la máscara binaria con radio `max(1, round(0.004 × max(w, h)))` para preservar el borde epidérmico del fruto.
   - Todo píxel exterior a la máscara dilatada se reemplaza por gris neutro `RGB(128, 128, 128)`.

## 4. Métricas de Verificación (ONNX vs. PyTorch Original)

Evaluación realizada sobre el lote de control de **30 imágenes de validación**:

| Métrica | Exportación `FP32` | Exportación `INT8` (Integrada en App) |
| :--- | :---: | :---: |
| **Archivo** | `gulupa_fruto_yolo26n-seg.onnx` | `gulupa_fruto_yolo26n-seg_int8.onnx` |
| **Tamaño en disco** | `11.07 MB` | **`3.23 MB`** (-70.8 %) |
| **Decodificación exacta (%)** | `100.0 %` | **`100.0 %`** |
| **IoU medio vs. PyTorch** | `0.9819` | **`0.9813`** |
| **IoU mínimo vs. PyTorch** | `0.9678` | **`0.9689`** |
| **Coincidencia de fruto principal (%)** | `100.0 %` | **`100.0 %`** |
