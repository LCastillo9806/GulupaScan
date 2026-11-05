# -*- coding: utf-8 -*-
"""Resultado de referencia de los modelos ONNX de GulupaScan para una o varias fotos.

Sirve para validar cualquier implementación móvil (Android / iOS): con la misma foto y
el mismo modo de resolución, la clase diagnosticada debe coincidir y la severidad total
debe diferir menos de 1 punto porcentual.

Uso:
    pip install onnxruntime opencv-python numpy
    python scripts/referencia_onnx.py [--resolucion low|medium|high|original|ultra] foto1.jpg ...
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
from typing import Any

import cv2
import numpy as np
import onnxruntime as ort

ROOT_DIR = pathlib.Path(__file__).resolve().parent.parent
ASSETS_CANDIDATES = [
    ROOT_DIR / "app/src/main/assets",
    ROOT_DIR / "app/android/app/src/main/assets",
]
ASSETS = next((p for p in ASSETS_CANDIDATES if p.exists()), ASSETS_CANDIDATES[0])

RESOLUCIONES_PX: dict[str, int] = {
    "low": 640,
    "medium": 1024,
    "high": 2048,
    "original": 0,
    "ultra": 4096,
}

CALIDAD_JPEG = 95
LADO_FRUTO = 480
LADO_LESIONES = 640
CONFIANZA = 0.25
UMBRAL_MASCARA = 0.5
MARGEN_RECORTE = 0.10
HOLGURA_FONDO = 0.004
UMBRAL_SANO = 0.5
LESIONES: dict[int, str] = {1: "Scab", 2: "Anthracnose", 3: "Oil_spot"}


def preparar_foto(ruta: str | pathlib.Path, lado_maximo: int = 2048) -> np.ndarray:
    """Lee la foto, limita su lado mayor (si lado_maximo > 0) y simula la compresión JPEG."""
    img = cv2.imread(str(ruta))
    if img is None:
        raise ValueError(f"No se pudo leer la imagen: {ruta}")
    h, w = img.shape[:2]
    if lado_maximo > 0:
        escala = lado_maximo / max(h, w)
        if escala < 1.0:
            nuevo_tam = (round(w * escala), round(h * escala))
            img = cv2.resize(img, nuevo_tam, interpolation=cv2.INTER_AREA)
    ok, jpeg = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, CALIDAD_JPEG])
    if not ok:
        raise RuntimeError(f"Error al codificar JPEG en memoria para: {ruta}")
    decodificada = cv2.imdecode(jpeg, cv2.IMREAD_COLOR)
    if decodificada is None:
        raise RuntimeError(f"Error al decodificar JPEG en memoria para: {ruta}")
    return decodificada


def predecir(
    sesion: ort.InferenceSession,
    img: np.ndarray,
    lado: int,
) -> tuple[list[tuple[int, float, list[float], np.ndarray]], tuple[int, int, float]]:
    """Aplica letterbox, ejecuta inferencia ONNX y decodifica las máscaras."""
    h, w = img.shape[:2]
    escala = min(lado / h, lado / w)
    nw, nh = round(w * escala), round(h * escala)
    px, py = (lado - nw) // 2, (lado - nh) // 2

    lienzo = np.full((lado, lado, 3), 114, dtype=np.uint8)
    lienzo[py : py + nh, px : px + nw] = cv2.resize(
        img, (nw, nh), interpolation=cv2.INTER_LINEAR
    )
    tensor_entrada = (
        cv2.cvtColor(lienzo, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    ).transpose(2, 0, 1)[None]

    nombre_input = sesion.get_inputs()[0].name
    filas_batch, protos_batch = sesion.run(None, {nombre_input: tensor_entrada})
    filas, protos = filas_batch[0], protos_batch[0]

    salida: list[tuple[int, float, list[float], np.ndarray]] = []
    for det in filas[filas[:, 4] >= CONFIANZA]:
        mascara_baja = 1.0 / (1.0 + np.exp(-np.tensordot(det[6:], protos, axes=1)))
        mascara = (
            cv2.resize(mascara_baja, (lado, lado), interpolation=cv2.INTER_LINEAR)
            > UMBRAL_MASCARA
        )
        recortada = np.zeros_like(mascara)
        x0, y0 = max(int(det[0]), px), max(int(det[1]), py)
        x1 = min(int(np.ceil(det[2])), lado - px)
        y1 = min(int(np.ceil(det[3])), lado - py)
        recortada[y0:y1, x0:x1] = mascara[y0:y1, x0:x1]
        caja = [
            float((det[0] - px) / escala),
            float((det[1] - py) / escala),
            float((det[2] - px) / escala),
            float((det[3] - py) / escala),
        ]
        salida.append((int(det[5]), float(det[4]), caja, recortada))
    return salida, (px, py, escala)


def analizar(
    foto: np.ndarray,
    modelo_a: ort.InferenceSession,
    modelo_b: ort.InferenceSession,
) -> dict[str, Any]:
    """Ejecuta el pipeline en cascada (Modelo A -> Recorte -> Modelo B) y calcula severidad."""
    dets, (px, py, escala) = predecir(modelo_a, foto, LADO_FRUTO)
    frutos = [d for d in dets if d[0] == 0]
    if not frutos:
        return {"fruto": False}

    _, conf_fruto, caja, mascara = max(frutos, key=lambda d: d[1])
    margen = round(MARGEN_RECORTE * max(caja[2] - caja[0], caja[3] - caja[1]))
    alto, ancho = foto.shape[:2]
    izq, arr = max(0, int(caja[0]) - margen), max(0, int(caja[1]) - margen)
    der, aba = min(ancho, int(caja[2]) + margen), min(alto, int(caja[3]) + margen)

    recorte = foto[arr:aba, izq:der].copy()
    h, w = recorte.shape[:2]
    xs = np.clip(((np.arange(izq, der) + 0.5) * escala + px).astype(int), 0, LADO_FRUTO - 1)
    ys = np.clip(((np.arange(arr, aba) + 0.5) * escala + py).astype(int), 0, LADO_FRUTO - 1)
    dentro = mascara[np.ix_(ys, xs)].astype(np.uint8)
    radio = max(1, round(HOLGURA_FONDO * max(w, h)))
    kernel = np.ones((2 * radio + 1, 2 * radio + 1), dtype=np.uint8)
    dentro_dilatado = cv2.dilate(dentro, kernel) > 0
    recorte[~dentro_dilatado] = (128, 128, 128)

    dets_b, _ = predecir(modelo_b, recorte, LADO_LESIONES)
    forma = (LADO_LESIONES, LADO_LESIONES)
    fruto = np.zeros(forma, dtype=bool)
    por_lesion: dict[int, np.ndarray] = {k: np.zeros(forma, dtype=bool) for k in LESIONES}

    for clase, _, _, m in dets_b:
        if clase == 0:
            fruto |= m
        elif clase in por_lesion:
            por_lesion[clase] |= m

    union = np.zeros(forma, dtype=bool)
    for m in por_lesion.values():
        union |= m
    fruto |= union
    area = int(fruto.sum())
    if area == 0:
        return {"fruto": False}

    severidad = 100.0 * float(union.sum()) / area
    por_clase = {
        LESIONES[k]: round(100.0 * float(m.sum()) / area, 2)
        for k, m in por_lesion.items()
    }
    top = max(por_clase, key=lambda k: por_clase[k])
    return {
        "fruto": True,
        "clase": "Healthy" if severidad < UMBRAL_SANO else top,
        "severidad_total": round(severidad, 2),
        "por_lesion": por_clase,
        "confianza_fruto": round(conf_fruto, 3),
        "resolucion_foto_px": [ int(foto.shape[1]), int(foto.shape[0]) ],
        "lado_recorte_px": int(max(w, h)),
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    """Procesa los argumentos de línea de comandos."""
    parser = argparse.ArgumentParser(
        description="Validador de referencia ONNX para GulupaScan."
    )
    parser.add_argument(
        "--resolucion",
        choices=list(RESOLUCIONES_PX.keys()),
        default="high",
        help="Nivel de resolución de entrada (low=640, medium=1024, high=2048, original=0, ultra=4096).",
    )
    parser.add_argument(
        "fotos",
        nargs="+",
        help="Rutas a las fotografías de gulupa a evaluar.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    """Punto de entrada principal del script de validación."""
    args = parse_args(sys.argv[1:] if argv is None else argv)
    lado_maximo = RESOLUCIONES_PX[args.resolucion]

    sesion_a = ort.InferenceSession(
        str(ASSETS / "gulupa_fruto.onnx"), providers=["CPUExecutionProvider"]
    )
    sesion_b = ort.InferenceSession(
        str(ASSETS / "gulupa_lesiones.onnx"), providers=["CPUExecutionProvider"]
    )

    for ruta in args.fotos:
        foto = preparar_foto(ruta, lado_maximo=lado_maximo)
        resultado = analizar(foto, sesion_a, sesion_b)
        print(
            json.dumps(
                {"foto": ruta, "modo_resolucion": args.resolucion, **resultado},
                ensure_ascii=False,
            )
        )


if __name__ == "__main__":
    main()
