# -*- coding: utf-8 -*-
"""Automatización de la insignia 'Quickdraw' en GitHub usando PyGithub.

Este script se autentica en el repositorio `jf-floresriera/gulupascaner`, abre un
nuevo Issue de verificación, espera un intervalo configurable (por defecto 60 s,
dentro de la ventana oficial de 5 minutos de GitHub) y lo cierra automáticamente.

Requisitos:
    pip install PyGithub

Variables de entorno:
    GITHUB_TOKEN: Token personal de acceso (PAT) o token obtenido vía `gh auth token`.

Uso:
    export GITHUB_TOKEN=$(gh auth token)
    python scripts/github_quickdraw.py --wait 60
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time


def obtener_token() -> str:
    """Obtiene el token de GitHub desde `GITHUB_TOKEN` o desde GitHub CLI (`gh`)."""
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        return token
    try:
        proc = subprocess.run(
            ["gh", "auth", "token"],
            check=True,
            capture_output=True,
            text=True,
        )
        return proc.stdout.strip()
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(
            "No se encontró GITHUB_TOKEN ni sesión activa en `gh auth token`."
        ) from exc


def ejecutar_quickdraw(
    repo_name: str = "jf-floresriera/gulupascaner",
    title: str = "Test Issue: Verificación automática de pipeline ONNX",
    wait_seconds: int = 60,
) -> None:
    """Abre un Issue con PyGithub, espera `wait_seconds` y lo cierra."""
    from github import Auth, Github

    token = obtener_token()
    gh = Github(auth=Auth.Token(token))
    repo = gh.get_repo(repo_name)

    issue = repo.create_issue(
        title=title,
        body=(
            "Issue generado automáticamente para verificación rápida del flujo "
            "de integración continua y modelos ONNX en GulupaScan."
        ),
    )
    print(f"[Quickdraw] Issue #{issue.number} abierto exitosamente: {issue.html_url}")

    if wait_seconds > 0:
        print(f"[Quickdraw] Esperando {wait_seconds} segundos antes de cerrar...")
        time.sleep(wait_seconds)

    issue.create_comment(
        "Verificación completada dentro de la ventana de 5 minutos. Cerrando automáticamente."
    )
    issue.edit(state="closed")
    print(f"[Quickdraw] Issue #{issue.number} cerrado exitosamente (< 5 min).")


def parse_args(argv: list[str]) -> argparse.Namespace:
    """Procesa los argumentos de línea de comandos."""
    parser = argparse.ArgumentParser(
        description="Automatización de Issue para la insignia Quickdraw con PyGithub."
    )
    parser.add_argument(
        "--repo",
        default="jf-floresriera/gulupascaner",
        help="Nombre completo del repositorio (usuario/repo).",
    )
    parser.add_argument(
        "--title",
        default="Test Issue",
        help="Título del Issue a crear y cerrar.",
    )
    parser.add_argument(
        "--wait",
        type=int,
        default=60,
        help="Segundos de espera antes de cerrar el Issue (por defecto 60).",
    )
    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    ejecutar_quickdraw(repo_name=args.repo, title=args.title, wait_seconds=args.wait)
