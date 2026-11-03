# Changelog

## [camara-info-ios] - 2026-11-03
- Cámara: máxima calidad, mayor resolución 4:3, enfoque antes de disparar, tocar para enfocar y vista
  previa igual a la foto. Las imágenes se analizan a 2048 px, como en el entrenamiento.
- Análisis: si no hay fruto se pide otra foto en lugar de informar "sano"; se elimina la confianza fija
  de 88 %; aviso cuando el fruto ocupa pocos píxeles.
- Nuevas secciones en Perfil: Instrucciones de uso e Información técnica. Acerca de nosotros incluye al equipo.
- Idioma: el selector cambia al instante toda la app, incluido el PDF, sin cerrar la cámara.
- `docs/migracion-ios.md`: guía para migrar a iPhone. `scripts/referencia_onnx.py`: resultado de referencia
  para validar cualquier implementación.

## [logos-transicion] - 2026-10-30
- Nueva identidad visual: logo del fruto y letrero "GulupaScan" del equipo (imágenes con fondo
  transparente), paleta ciruela/verde/amarillo/morado e ícono de la app sobre morado.
- Transición de colores del logo en el inicio (splash) y mientras se hace la inferencia.
- Lema "Detecta tu roña en la gulupa" con tipografía Patrick Hand (licencia OFL).

## [tema-morado-modelo-gulupa] - 2026-10-30
- App: tema **Morado gulupa** (colores y formas de la presentación) seleccionable en Configuración.
- Backend: `gulupa_pipeline.py` (YOLO11-seg: fruto, roña, daño mecánico, trips) que se activa al
  existir `models/gulupa_seg.pt`; `/api/health` informa el motor activo.
- App: máscara propia por tipo de daño en el detalle.
- Guía `docs/guia-modelos.md` para entrenar y publicar el modelo.

## [app-kotlin] - 2026-10-30
- Se agrega `app/android/`: app nativa en Kotlin + Jetpack Compose con el diseño del prototipo del Reto 2
  (splash, login, cámara, procesando, tipos de daño, severidad, resultado, detalles técnicos,
  historial con CSV, alertas, perfil, acerca de nosotros). Español e inglés. Modo demostración.
- Se agrega `app/backend/api.py`: ruta `/api/analyze` que devuelve JSON reutilizando sin cambios
  `pipeline.py` y `segment_mildew.py` de PowderyVision7.

## [proyecto-base] - 2026-10-13
- Se agrega `proyecto-base/powderyvision7/` (copia del Space de Hugging Face PowderyVision7) como referencia para GulupaScaner.

## [reto-02] - 2026-09-17
- Canvas y prototipo.

## [reto-01] - 2026-09-08
- Propuesta de proyecto.
