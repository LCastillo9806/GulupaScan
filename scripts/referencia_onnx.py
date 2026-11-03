"""Resultado de referencia de los modelos ONNX de GulupaScan para una foto.

Sirve para validar cualquier implementación de la app (Android, iOS): con la misma foto, la clase
debe coincidir y la severidad total debe diferir menos de 1 punto porcentual.

Uso:
    pip install onnxruntime opencv-python numpy
    python scripts/referencia_onnx.py foto1.jpg foto2.jpg ...
"""
import json
import pathlib
import sys

import cv2
import numpy as np
import onnxruntime as ort

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "app/android/app/src/main/assets"
LADO_MAXIMO_FOTO = 2048
CALIDAD_JPEG = 95
LADO_FRUTO = 480
LADO_LESIONES = 640
CONFIANZA = 0.25
UMBRAL_MASCARA = 0.5
MARGEN_RECORTE = 0.10
HOLGURA_FONDO = 0.004
UMBRAL_SANO = 0.5
LESIONES = {1: "Scab", 2: "Anthracnose", 3: "Oil_spot"}


def preparar_foto(ruta):
    """Lee la foto enderezada por EXIF, limita su lado mayor y simula la compresión JPEG de la app."""
    img = cv2.imread(str(ruta))
    if img is None:
        raise ValueError(f"No se pudo leer {ruta}")
    h, w = img.shape[:2]
    escala = LADO_MAXIMO_FOTO / max(h, w)
    if escala < 1:
        img = cv2.resize(img, (round(w * escala), round(h * escala)), interpolation=cv2.INTER_AREA)
    jpeg = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, CALIDAD_JPEG])[1]
    return cv2.imdecode(jpeg, cv2.IMREAD_COLOR)


def predecir(sesion, img, lado):
    """Letterbox, inferencia y decodificación de máscaras en el espacio de entrada del modelo."""
    h, w = img.shape[:2]
    e = min(lado / h, lado / w)
    nw, nh = round(w * e), round(h * e)
    px, py = (lado - nw) // 2, (lado - nh) // 2
    lienzo = np.full((lado, lado, 3), 114, np.uint8)
    lienzo[py:py + nh, px:px + nw] = cv2.resize(img, (nw, nh), interpolation=cv2.INTER_LINEAR)
    x = (cv2.cvtColor(lienzo, cv2.COLOR_BGR2RGB).astype(np.float32) / 255).transpose(2, 0, 1)[None]
    filas, protos = sesion.run(None, {sesion.get_inputs()[0].name: x})
    filas, protos = filas[0], protos[0]
    salida = []
    for d in filas[filas[:, 4] >= CONFIANZA]:
        m = 1 / (1 + np.exp(-np.tensordot(d[6:], protos, axes=1)))
        m = cv2.resize(m, (lado, lado), interpolation=cv2.INTER_LINEAR) > UMBRAL_MASCARA
        c = np.zeros_like(m)
        x0, y0 = max(int(d[0]), px), max(int(d[1]), py)
        x1, y1 = min(int(np.ceil(d[2])), lado - px), min(int(np.ceil(d[3])), lado - py)
        c[y0:y1, x0:x1] = m[y0:y1, x0:x1]
        caja = [(d[0] - px) / e, (d[1] - py) / e, (d[2] - px) / e, (d[3] - py) / e]
        salida.append((int(d[5]), float(d[4]), caja, c))
    return salida, (px, py, e)


def analizar(foto, modelo_a, modelo_b):
    dets, (px, py, e) = predecir(modelo_a, foto, LADO_FRUTO)
    frutos = [d for d in dets if d[0] == 0]
    if not frutos:
        return {"fruto": False}
    _, conf_fruto, c, mascara = max(frutos, key=lambda d: d[1])

    margen = round(MARGEN_RECORTE * max(c[2] - c[0], c[3] - c[1]))
    alto, ancho = foto.shape[:2]
    izq, arr = max(0, int(c[0]) - margen), max(0, int(c[1]) - margen)
    der, aba = min(ancho, int(c[2]) + margen), min(alto, int(c[3]) + margen)
    recorte = foto[arr:aba, izq:der].copy()
    h, w = recorte.shape[:2]
    xs = np.clip(((np.arange(izq, der) + .5) * e + px).astype(int), 0, LADO_FRUTO - 1)
    ys = np.clip(((np.arange(arr, aba) + .5) * e + py).astype(int), 0, LADO_FRUTO - 1)
    dentro = mascara[np.ix_(ys, xs)].astype(np.uint8)
    radio = max(1, round(HOLGURA_FONDO * max(w, h)))
    dentro = cv2.dilate(dentro, np.ones((2 * radio + 1, 2 * radio + 1), np.uint8)) > 0
    recorte[~dentro] = (128, 128, 128)

    dets_b, _ = predecir(modelo_b, recorte, LADO_LESIONES)
    forma = (LADO_LESIONES, LADO_LESIONES)
    fruto = np.zeros(forma, bool)
    por_lesion = {k: np.zeros(forma, bool) for k in LESIONES}
    for clase, _, _, m in dets_b:
        if clase == 0:
            fruto |= m
        elif clase in por_lesion:
            por_lesion[clase] |= m
    union = np.zeros(forma, bool)
    for m in por_lesion.values():
        union |= m
    fruto |= union
    area = int(fruto.sum())
    if area == 0:
        return {"fruto": False}

    severidad = 100 * union.sum() / area
    por_clase = {LESIONES[k]: round(100 * m.sum() / area, 2) for k, m in por_lesion.items()}
    top = max(por_clase, key=por_clase.get)
    return {
        "fruto": True,
        "clase": "Healthy" if severidad < UMBRAL_SANO else top,
        "severidad_total": round(float(severidad), 2),
        "por_lesion": por_clase,
        "confianza_fruto": round(conf_fruto, 3),
        "lado_recorte_px": max(w, h),
    }


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    a = ort.InferenceSession(str(ASSETS / "gulupa_fruto.onnx"), providers=["CPUExecutionProvider"])
    b = ort.InferenceSession(str(ASSETS / "gulupa_lesiones.onnx"), providers=["CPUExecutionProvider"])
    for ruta in sys.argv[1:]:
        print(json.dumps({"foto": ruta, **analizar(preparar_foto(ruta), a, b)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
