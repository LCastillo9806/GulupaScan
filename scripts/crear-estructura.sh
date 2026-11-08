#!/usr/bin/env bash
# Crea la estructura del repositorio GulupaScan.
# Uso (desde la raíz del proyecto, en Git Bash):  bash scripts/crear-estructura.sh
set -e

echo "📁 Creando carpetas..."
mkdir -p app models \
  docs/retos/{reto-01-propuesta,reto-02-canvas-prototipo,reto-03-primer-avance,entrega-final} \
  docs/{presentaciones,diagramas,assets}

# crear_readme <ruta> <contenido>: solo crea el archivo si no existe
crear_readme () {
  if [ ! -f "$1" ]; then
    printf "%b" "$2" > "$1"
    echo "  ✔ $1"
  else
    echo "  • $1 ya existe, no se modifica"
  fi
}

echo "📝 Creando README de cada sección..."
crear_readme app/README.md "# app\n\nCódigo fuente de la aplicación móvil.\n\n- Tecnología: _por definir (Flutter / Kotlin / React Native)_\n- Cómo ejecutar: _agregar comandos_\n"
crear_readme models/README.md "# models\n\nModelos usados por la app (versionados con Git LFS).\n\n| Archivo | Versión | Origen / datos de entrenamiento | Licencia | Precisión |\n|---------|---------|---------------------------------|----------|-----------|\n| _pendiente_ | | | | |\n"
crear_readme docs/retos/reto-01-propuesta/README.md "# Reto 1 · Propuesta de proyecto\n\n**Fecha de entrega:** 08/SEP/2026\n\n**Documento:** [reto-01-propuesta-proyecto.pdf](reto-01-propuesta-proyecto.pdf)\n\n## Resumen\n_Describe la idea de la app, el problema y el público objetivo._\n\n## Retroalimentación del profesor\n_Pendiente._\n"
crear_readme docs/retos/reto-02-canvas-prototipo/README.md "# Reto 2 · Canvas y Prototipo\n\n**Fecha de entrega:** 17/SEP/2026\n\n**Documento:** [reto-02-canvas-prototipo.pdf](reto-02-canvas-prototipo.pdf)\n\n## Resumen\n_Resume el Canvas y enlaza el prototipo (Figma u otro)._\n\n## Retroalimentación del profesor\n_Pendiente._\n"
crear_readme docs/retos/reto-03-primer-avance/README.md "# Reto 3 · Primer avance del proyecto\n\n**Fecha de entrega:** _por definir_\n\n## Qué se entrega\n_Pendiente._\n"
crear_readme docs/retos/entrega-final/README.md "# Entrega final\n\n**Fecha de entrega:** _por definir_\n\n## Qué se entrega\n_Pendiente._\n"
crear_readme CHANGELOG.md "# Changelog\n\n## [reto-02] - 2026-09-17\n- Canvas y prototipo.\n\n## [reto-01] - 2026-09-08\n- Propuesta de proyecto.\n"

# Git no guarda carpetas vacías: se agrega un .gitkeep
for d in docs/presentaciones docs/diagramas docs/assets; do
  [ -z "$(ls -A "$d")" ] && touch "$d/.gitkeep"
done

echo "✅ Estructura lista. Revisa con:  ls -R"
