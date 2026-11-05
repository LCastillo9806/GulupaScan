# Changelog — GulupaScan

Todos los cambios relevantes de este proyecto se documentan en este archivo siguiendo [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y [Semantic Versioning](https://semver.org/lang/es/).

## [0.3.0] — 2026-11-05

### Agregado (Added)
- **Aplicación Nativa iOS (`iosApp/`)**: Paridad funcional y visual 1:1 con Android usando SwiftUI, Canvas nativo, `PhotosPicker`, `UIGraphicsPDFRenderer` y `onnxruntime-objc:1.24.2`.
- **Selector de 5 Niveles de Resolución de Cámara e IA (`AnalysisResolution`)**:
  - `Baja (640 px · VGA)` para captura ultrarrápida de baja latencia.
  - `Media (1024 px · HD)` para monitoreo rápido en campo.
  - `Alta (2048 px · 2K)` como estándar recomendado de exportación.
  - `Original del Equipo (Sensor Nativo 100 %)` sin reescalado previo.
  - `Ultra 2×2 (Nativa + Tiling 2×2)` con 5 inferencias de `gulupa_lesiones.onnx` (`1280×1280` píxeles efectivos sobre la corteza).
- **Documentación Científica y Fichas de Modelos ONNX**:
  - [`models/MODELO_A_FRUTO.md`](./models/MODELO_A_FRUTO.md): Ficha técnica y métricas de `gulupa_fruto.onnx` (`IoU = 0.9813` INT8).
  - [`models/MODELO_B_LESIONES.md`](./models/MODELO_B_LESIONES.md): Ficha técnica y métricas de `gulupa_lesiones.onnx` (`IoU = 0.9022` INT8).
  - [`CITATION.cff`](./CITATION.cff): Metadatos oficiales de citación científica para GitHub.

### Mejorado (Changed)
- **Identidad Visual y Animaciones**: `AnimatedBrandLockup`, `Wordmark` tipográfico (`Patrick Hand`) y `OrganicBlobs` sincronizados entre Android e iOS.
- **Contraste en Tema Morado**: Tarjetas y formularios sobre fondo blanco de alto contraste e independencia del modo oscuro del sistema.
- **Calidad de Código Python (PEP 8)**: Tipado estático (`typing`), docstrings y flag `--resolucion` en [`scripts/referencia_onnx.py`](./scripts/referencia_onnx.py), [`app/backend/api.py`](./app/backend/api.py) y [`app/backend/gulupa_pipeline.py`](./app/backend/gulupa_pipeline.py).
