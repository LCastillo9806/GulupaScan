# Guía de Contribución — GulupaScan (UNAL)

¡Gracias por tu interés en contribuir a **GulupaScan**! Este proyecto es desarrollado en la **Universidad Nacional de Colombia (UNAL)** para el diagnóstico fitosanitario de gulupa (*Passiflora edulis* Sims).

## 1. Flujo de Ramas y Pull Requests

1. Crea una rama descriptiva a partir de `main`:
   - `feat/nombre-funcionalidad` para nuevas características en Android, iOS o Backend.
   - `docs/nombre-documento` para mejoras en fichas técnicas de modelos o guías.
   - `refactor/nombre-modulo` para mejoras de estilo (PEP 8, Kotlin, Swift).
2. Verifica la consistencia de los modelos ONNX con el script de referencia antes de abrir el Pull Request:
   ```bash
   python scripts/referencia_onnx.py --resolucion high ruta/a/foto.jpg
   ```

## 2. Coautoría Académica en Commits (`Co-authored-by`)

Este repositorio incluye la plantilla `.gitmessage` preconfigurada para registrar la coautoría del equipo de investigación. Para activarla en un clon nuevo, ejecuta:

```bash
git config commit.template .gitmessage
```

## 3. Estándares de Código

- **Python (`scripts/`, `app/backend/`)**: Cumplir con **PEP 8**, incluir anotaciones de tipo (`from __future__ import annotations`) y docstrings en todas las funciones públicas.
- **Android (`app/` y `app/android/`)**: Mantener sincronizados ambos árboles de código para garantizar la compilación automática del APK en GitHub Actions (`.github/workflows/build-apk.yml`).
- **iOS (`iosApp/`)**: Mantener compatibilidad con iOS `16.0+` y regenerar el proyecto con `xcodegen generate` si se agregan nuevas carpetas de recursos.
