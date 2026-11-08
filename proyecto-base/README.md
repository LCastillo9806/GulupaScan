# 🧭 Proyecto base

Esta carpeta guarda proyectos previos que sirven como **inspiración y protocolo**
para el desarrollo de GulupaScan. No es código de la app: es material de referencia.

| Proyecto | Qué es | Origen |
|----------|--------|--------|
| [`powderyvision7/`](powderyvision7) | App web (PWA) de detección de oídio (*powdery mildew*) en hojas de arveja y tomate con modelos YOLO11, servida con Docker. | [Hugging Face Space](https://huggingface.co/spaces/jf-floresriera/powderyvision7) |

## Qué tomar de PowderyVision7 para GulupaScan

- **Pipeline de visión**: `pipeline.py` y `segment_mildew.py` (detección + segmentación con YOLO11).
- **Servidor e interfaz**: `app.py`, `templates/`, `static/` (PWA con `manifest.json` y `sw.js`).
- **Despliegue**: `Dockerfile` y `requirements.txt`.
- **Modelos**: `models/*.pt` (guardados con Git LFS).

## Cómo se actualiza

La copia se hace con el flujo de GitHub Actions
[`importar-powderyvision7.yml`](../.github/workflows/importar-powderyvision7.yml).
Para volver a sincronizarla con el Space: pestaña **Actions** →
*Importar PowderyVision7 (proyecto base)* → **Run workflow**.
El archivo `powderyvision7/ORIGEN.txt` indica de qué commit del Space viene la copia.
