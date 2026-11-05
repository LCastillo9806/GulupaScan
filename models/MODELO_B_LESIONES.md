# Modelo B (`gulupa_lesiones.onnx`) — Segmentación Fitosanitaria de Lesiones

## 1. Descripción General
- **Proyecto**: *Detection and Classification of Epidermal Damage in Purple Passion Fruit (`Passiflora edulis` Sims)*
- **Rol en el Pipeline**: **Etapa B** — Recibe el recorte del fruto con fondo gris `RGB(128, 128, 128)` generado por el Modelo A y segmenta simultáneamente la superficie del fruto y las lesiones epidérmicas.
- **Arquitectura**: `YOLO26n-seg` (End-to-End Instance Segmentation)
- **Fecha de exportación**: `2026-10-31T06:17:30`

## 2. Especificación de Tensores

| Tensor | Nombre | Forma (Shape) | Tipo | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| **Entrada** | `images` | `[1, 3, 640, 640]` | `float32` | Recorte del fruto con *letterbox* a `640×640` y relleno `RGB(114, 114, 114)` |
| **Salida 0** | `output0` | `[1, 300, 38]` | `float32` | 300 detecciones: `[x1, y1, x2, y2, confianza, clase, coef_0 ... coef_31]` |
| **Salida 1** | `output1` | `[1, 32, 160, 160]` | `float32` | 32 prototipos espaciales de máscara de `160×160` píxeles |

### Clases del Modelo B
| ID | Clave Interna | Nombre en Español | Agente Causal | Color en Máscara |
| :---: | :--- | :--- | :--- | :--- |
| `0` | `fruit` | Fruto de gulupa | *Passiflora edulis* Sims | Verde (`#4CAF50`) |
| `1` | `scab` | Sarna (Roña) | *Cladosporium cladosporioides* | Rojo (`#E53935`) |
| `2` | `anthracnose` | Antracnosis | *Colletotrichum gloeosporioides* | Morado (`#8E24AA`) |
| `3` | `oil_spot` | Mancha de aceite (Bacteriosis) | *Xanthomonas axonopodis* pv. *passiflorae* | Naranja (`#FB8C00`) |

## 3. Cálculo Cuantitativo de Severidad y Modo Multi-Escala `2×2`

1. **Fórmula de Severidad Total**:
   $$\text{Severidad Total (\%)} = 100 \times \frac{\text{Área}(\text{Lesiones} \cap \text{Fruto})}{\text{Área}(\text{Fruto} \cup \text{Lesiones})}$$
2. **Umbral de Fruto Sano**: Si $\text{Severidad Total} < 0.5\%$, el fruto se clasifica como **`Healthy` (`Sano`)**. En caso contrario, se reporta la patología predominante y el desglose individual por enfermedad en la escala 1–10.
3. **Inferencia Multi-Escala `2×2` (Modo Ultra)**:
   - Cuando el usuario selecciona la resolución **`Ultra 2×2`**, además de la inferencia global `640×640`, la aplicación divide el recorte del fruto en **4 cuadrantes superpuestos al 60 %** y proyecta sus máscaras sobre la rejilla del fruto, alcanzando una resolución neuronal efectiva de **`1280×1280` píxeles**.

## 4. Métricas de Verificación (ONNX vs. PyTorch Original)

Evaluación realizada sobre el lote de control de **30 imágenes de validación**:

| Métrica | Exportación `FP32` | Exportación `INT8` (Integrada en App) |
| :--- | :---: | :---: |
| **Archivo** | `gulupa_lesiones_yolo26n-seg.onnx` | `gulupa_lesiones_yolo26n-seg_int8.onnx` |
| **Tamaño en disco** | `11.14 MB` | **`3.30 MB`** (-70.4 %) |
| **Decodificación exacta (%)** | `100.0 %` | **`100.0 %`** |
| **IoU medio vs. PyTorch** | `0.9029` | **`0.9022`** |
| **IoU mínimo vs. PyTorch** | `0.1284` | **`0.4413`** |
| **Coincidencia detección principal (%)** | `96.7 %` | **`100.0 %`** |
