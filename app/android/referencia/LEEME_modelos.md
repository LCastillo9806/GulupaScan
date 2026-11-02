# GuluScan · Modelos de daño epidérmico en gulupa

Generado: 2026-10-31 17:26
Autor: Luis Alberto Castillo Sanabria · Agrocomputation and Epidemiological Analysis Lab, UNAL

| Carpeta | Contenido | Para qué |
|---|---|---|
| `1_pesos_pytorch/` | `best.pt` (mejor época) y `last.pt` (último estado) de cada modelo | Reentrenar, evaluar o exportar de nuevo con Ultralytics |
| `2_android/assets/` | `gulupa_fruto.onnx` y `gulupa_lesiones.onnx` (INT8) | Copiar a `app/src/main/assets/` de la APK |
| `2_android/` | `AnalizadorGulupa.kt` y `GUIA_ANDROID.md` | Código Kotlin y pasos de integración |
| `3_python/` | `gulupa_inferencia.py` | Mismo análisis en Python, solo con ONNX Runtime |
| `4_resultados/` | Métricas (CSV), figuras (PNG 300 dpi y PDF), curvas y registros | Presentación y artículo |
| `5_codigo/` | Todos los scripts `.py`, `config.yaml` y `requirements.txt` | Reproducir el trabajo |
| `6_dataset/1_clasificacion_fotos/` | Fotos originales balanceadas TRAIN/VAL/TEST por clase, divididas por fruto | Clasificación y referencia |
| `6_dataset/2_segmentacion_lesiones_yolo/` | Recortes del fruto (fondo gris) + etiquetas YOLO-seg, `data.yaml` | Reentrenar el Modelo B |
| `6_dataset/3_localizacion_fruto_yolo/` | Fotos completas + contorno del fruto, `data.yaml` | Reentrenar el Modelo A |
| `6_dataset/control_calidad/` | Exclusiones con su motivo, partición de cada imagen | Trazabilidad / metodología |

Modelo A: localiza el fruto en la foto. Modelo B: segmenta fruto y lesiones (scab, anthracnose y oil_spot).
Severidad = 100 × área(lesiones ∩ fruto) / área(fruto).
Escala de grados (% del fruto): 0 < 0,5 · 1 = 0,5–5 · 2 = 5–10 · 3 = 10–25 · 4 = 25–50 · 5 ≥ 50.

Las etiquetas de lesiones son pseudoetiquetas automáticas (PatchCore + SAM 2.1) con control de calidad;
se recomienda revisión experta del conjunto de test antes de publicar.
Retirada del dataset publicado: Oil_spot/IMG_1225.jpg (fruto cortado, no es epidermis).

## Métricas en test (n = 142)

| Clase | Precisión | Recall | F1 | AUC-ROC |
|---|---|---|---|---|
| Healthy | 0.56 | 0.94 | 0.70 | 0.861 |
| Scab | 0.95 | 1.00 | 0.97 | 0.996 |
| Anthracnose | 1.00 | 0.79 | 0.88 | 0.897 |
| Oil_spot | 1.00 | 0.52 | 0.69 | 0.774 |
| macro | 0.88 | 0.81 | 0.81 | 0.882 |

Exactitud: 80.3% (IC 95 %: 73.0%–86.0%). Severidad total: CCC de Lin = 0.85, MAE = 6.0 pp.
