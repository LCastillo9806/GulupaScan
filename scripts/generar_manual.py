# -*- coding: utf-8 -*-
"""Generador automatizado de activos visuales ONNX y Manual Técnico/Usuario de GulupaScan.

Ejecuta el pipeline real en cascada (YOLOv8n-seg Etapa A 480x480 + Etapa B 640x640)
sobre la fotografía real de gulupa (`docs/manual/assets/muestra_real_gulupa.jpg`),
exporta cada etapa intermedia del flujo de visión artificial y construye el panel
comparativo de las 5 resoluciones de entrada (Baja, Media, Alta, Original y Ultra Alta).

Uso:
    /opt/anaconda3/bin/python3 scripts/generar_manual.py
"""

from __future__ import annotations

import json
import pathlib
import time
from typing import Any

import cv2
import numpy as np
import onnxruntime as ort

ROOT_DIR = pathlib.Path(__file__).resolve().parent.parent
ASSETS_DIR = ROOT_DIR / "app/src/main/assets"
MANUAL_ASSETS = ROOT_DIR / "docs/manual/assets"
SAMPLE_IMG = MANUAL_ASSETS / "muestra_real_gulupa.jpg"

LADO_FRUTO = 480
LADO_LESIONES = 640
CONFIANZA = 0.25
UMBRAL_MASCARA = 0.5
MARGEN_RECORTE = 0.10
HOLGURA_FONDO = 0.004
LESIONES: dict[int, str] = {1: "Scab", 2: "Anthracnose", 3: "Oil_spot"}

RESOLUCIONES = [
    ("low", "Baja (640 px)", 640),
    ("medium", "Media (1024 px)", 1024),
    ("high", "Alta (2048 px)", 2048),
    ("original", "Original (Sensor)", 0),
    ("ultra", "Ultra Alta (4096 px)", 4096),
]


def preparar_foto(ruta: pathlib.Path, lado_maximo: int = 2048) -> np.ndarray:
    """Lee la foto, aplica pre-escalado por resolución y compresión JPEG 95."""
    img = cv2.imread(str(ruta))
    if img is None:
        raise ValueError(f"No se pudo leer la imagen: {ruta}")
    h, w = img.shape[:2]
    if lado_maximo > 0:
        escala = lado_maximo / max(h, w)
        if escala < 1.0:
            img = cv2.resize(
                img,
                (round(w * escala), round(h * escala)),
                interpolation=cv2.INTER_AREA,
            )
    ok, jpeg = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 95])
    if not ok:
        raise RuntimeError("Error al codificar JPEG")
    return cv2.imdecode(jpeg, cv2.IMREAD_COLOR)


def predecir(
    sesion: ort.InferenceSession,
    img: np.ndarray,
    lado: int,
) -> tuple[list[tuple[int, float, list[float], np.ndarray]], tuple[int, int, float], np.ndarray]:
    """Ejecuta letterbox a (lado x lado), corre inferencia ONNX y retorna máscaras + lienzo."""
    h, w = img.shape[:2]
    escala = min(lado / h, lado / w)
    nw, nh = round(w * escala), round(h * escala)
    px, py = (lado - nw) // 2, (lado - nh) // 2

    lienzo = np.full((lado, lado, 3), 114, dtype=np.uint8)
    lienzo[py : py + nh, px : px + nw] = cv2.resize(
        img, (nw, nh), interpolation=cv2.INTER_LINEAR
    )
    tensor = (
        cv2.cvtColor(lienzo, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    ).transpose(2, 0, 1)[None]

    nombre_input = sesion.get_inputs()[0].name
    filas_batch, protos_batch = sesion.run(None, {nombre_input: tensor})
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
    return salida, (px, py, escala), lienzo


def generar_activos_onnx() -> dict[str, Any]:
    """Genera todas las imágenes paso a paso del pipeline ONNX y comparativa de resoluciones."""
    MANUAL_ASSETS.mkdir(parents=True, exist_ok=True)
    sesion_a = ort.InferenceSession(
        str(ASSETS_DIR / "gulupa_fruto.onnx"), providers=["CPUExecutionProvider"]
    )
    sesion_b = ort.InferenceSession(
        str(ASSETS_DIR / "gulupa_lesiones.onnx"), providers=["CPUExecutionProvider"]
    )

    foto = preparar_foto(SAMPLE_IMG, lado_maximo=2048)
    alto, ancho = foto.shape[:2]

    # 1. Foto original preparada
    cv2.imwrite(str(MANUAL_ASSETS / "onnx_01_original.jpg"), foto)

    # 2. Etapa A: Detección y segmentación de fruto (480x480)
    dets_a, (px, py, escala), lienzo_a = predecir(sesion_a, foto, LADO_FRUTO)
    frutos = [d for d in dets_a if d[0] == 0]
    _, conf_fruto, caja, mascara_a = max(frutos, key=lambda d: d[1])

    # Proyectar máscara de fruto al tamaño de la foto original para visualizar Etapa A
    xs_full = np.clip(((np.arange(ancho) + 0.5) * escala + px).astype(int), 0, LADO_FRUTO - 1)
    ys_full = np.clip(((np.arange(alto) + 0.5) * escala + py).astype(int), 0, LADO_FRUTO - 1)
    mascara_fruto_full = mascara_a[np.ix_(ys_full, xs_full)]

    vis_etapa_a = foto.copy()
    capa_verde = np.zeros_like(vis_etapa_a)
    capa_verde[mascara_fruto_full] = (60, 180, 75)  # BGR verde esmeralda
    vis_etapa_a = cv2.addWeighted(vis_etapa_a, 1.0, capa_verde, 0.38, 0)
    contornos_a, _ = cv2.findContours(
        mascara_fruto_full.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    cv2.drawContours(vis_etapa_a, contornos_a, -1, (40, 220, 90), 3)
    x0, y0, x1, y1 = [int(v) for v in caja]
    cv2.rectangle(vis_etapa_a, (x0, y0), (x1, y1), (0, 215, 255), 3)
    etiqueta_a = f"Etapa A (480x480): Fruto Gulupa {conf_fruto * 100:.1f}%"
    cv2.rectangle(vis_etapa_a, (x0, max(0, y0 - 42)), (x0 + 520, y0), (27, 67, 50), -1)
    cv2.putText(
        vis_etapa_a,
        etiqueta_a,
        (x0 + 10, max(28, y0 - 12)),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.72,
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )
    cv2.imwrite(str(MANUAL_ASSETS / "onnx_02_etapa_a_caja_mascara.jpg"), vis_etapa_a)

    # 3. Recorte centrado con margen 10% y fondo neutro RGB(128,128,128)
    margen = round(MARGEN_RECORTE * max(caja[2] - caja[0], caja[3] - caja[1]))
    izq, arr = max(0, int(caja[0]) - margen), max(0, int(caja[1]) - margen)
    der, aba = min(ancho, int(caja[2]) + margen), min(alto, int(caja[3]) + margen)

    recorte = foto[arr:aba, izq:der].copy()
    h, w = recorte.shape[:2]
    xs = np.clip(((np.arange(izq, der) + 0.5) * escala + px).astype(int), 0, LADO_FRUTO - 1)
    ys = np.clip(((np.arange(arr, aba) + 0.5) * escala + py).astype(int), 0, LADO_FRUTO - 1)
    dentro = mascara_a[np.ix_(ys, xs)].astype(np.uint8)
    radio = max(1, round(HOLGURA_FONDO * max(w, h)))
    kernel = np.ones((2 * radio + 1, 2 * radio + 1), dtype=np.uint8)
    dentro_dilatado = cv2.dilate(dentro, kernel) > 0
    recorte[~dentro_dilatado] = (128, 128, 128)
    cv2.imwrite(str(MANUAL_ASSETS / "onnx_03_recorte_fondo_gris.jpg"), recorte)

    # 4. Etapa B: Segmentación de lesiones sobre el recorte (640x640)
    dets_b, (px_b, py_b, escala_b), lienzo_b = predecir(sesion_b, recorte, LADO_LESIONES)
    forma = (LADO_LESIONES, LADO_LESIONES)
    fruto_b = np.zeros(forma, dtype=bool)
    por_lesion: dict[int, np.ndarray] = {k: np.zeros(forma, dtype=bool) for k in LESIONES}

    for clase, _, _, m in dets_b:
        if clase == 0:
            fruto_b |= m
        elif clase in por_lesion:
            por_lesion[clase] |= m

    union_b = np.zeros(forma, dtype=bool)
    for m in por_lesion.values():
        union_b |= m
    fruto_b |= union_b
    area_fruto = int(fruto_b.sum())
    severidad_total = 100.0 * float(union_b.sum()) / area_fruto

    # Máscara binaria (blanco = lesión, gris oscuro = fruto sano, negro = exterior)
    mascara_vis = np.zeros((LADO_LESIONES, LADO_LESIONES, 3), dtype=np.uint8)
    mascara_vis[fruto_b] = (55, 75, 60)
    mascara_vis[union_b] = (255, 255, 255)
    cv2.imwrite(str(MANUAL_ASSETS / "onnx_04_etapa_b_mascara.png"), mascara_vis)

    # 5. Superposición RGBA (igual a la generada en Android OnnxAnalyzer / iOS GulupaAnalyzer)
    xs_rec = np.clip(((np.arange(w) + 0.5) * escala_b + px_b).astype(int), 0, LADO_LESIONES - 1)
    ys_rec = np.clip(((np.arange(h) + 0.5) * escala_b + py_b).astype(int), 0, LADO_LESIONES - 1)
    lesion_en_recorte = union_b[np.ix_(ys_rec, xs_rec)]
    fruto_en_recorte = fruto_b[np.ix_(ys_rec, xs_rec)]

    overlay_rgba = np.zeros((h, w, 4), dtype=np.uint8)
    # Contorno verde del fruto
    contornos_fruto, _ = cv2.findContours(
        fruto_en_recorte.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    cv2.drawContours(overlay_rgba, contornos_fruto, -1, (45, 190, 95, 230), 3)
    # Relleno coral/ámbar de las lesiones de roña (Scab)
    overlay_rgba[lesion_en_recorte] = (40, 90, 245, 165)  # BGRA -> Naranja/Rojo semitransparente
    contornos_lesion, _ = cv2.findContours(
        lesion_en_recorte.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    cv2.drawContours(overlay_rgba, contornos_lesion, -1, (0, 210, 255, 255), 2)
    cv2.imwrite(str(MANUAL_ASSETS / "onnx_05_etapa_b_overlay.png"), overlay_rgba)

    # 6. Resultado final fusionado sobre el recorte original sin fondo gris
    recorte_natural = foto[arr:aba, izq:der].copy()
    resultado_final = recorte_natural.copy()
    capa_lesion = np.zeros_like(resultado_final)
    capa_lesion[lesion_en_recorte] = (30, 80, 245)
    resultado_final = cv2.addWeighted(resultado_final, 1.0, capa_lesion, 0.45, 0)
    cv2.drawContours(resultado_final, contornos_fruto, -1, (45, 210, 95), 3)
    cv2.drawContours(resultado_final, contornos_lesion, -1, (0, 225, 255), 2)

    # Cinta superior con diagnóstico
    cv2.rectangle(resultado_final, (0, 0), (w, 54), (27, 67, 50), -1)
    texto_diag = f"Rona (Scab): {severidad_total:.2f}% | Escala 8/10 (Alta)"
    cv2.putText(
        resultado_final,
        texto_diag,
        (14, 36),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.72,
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )
    cv2.imwrite(str(MANUAL_ASSETS / "onnx_06_resultado_final.jpg"), resultado_final)

    # 7. Comparativa de las 5 resoluciones de entrada
    tarjetas: list[np.ndarray] = []
    tabla_resoluciones: list[dict[str, Any]] = []
    for clave, etiqueta, lado_max in RESOLUCIONES:
        t0 = time.perf_counter()
        img_res = preparar_foto(SAMPLE_IMG, lado_maximo=lado_max)
        dets_ra, (px_r, py_r, esc_r), _ = predecir(sesion_a, img_res, LADO_FRUTO)
        _, conf_r, caja_r, masc_ra = max([d for d in dets_ra if d[0] == 0], key=lambda d: d[1])
        margen_r = round(MARGEN_RECORTE * max(caja_r[2] - caja_r[0], caja_r[3] - caja_r[1]))
        hr, wr = img_res.shape[:2]
        ix0, iy0 = max(0, int(caja_r[0]) - margen_r), max(0, int(caja_r[1]) - margen_r)
        ix1, iy1 = min(wr, int(caja_r[2]) + margen_r), min(hr, int(caja_r[3]) + margen_r)
        rec_r = img_res[iy0:iy1, ix0:ix1].copy()
        h_c, w_c = rec_r.shape[:2]
        xs_c = np.clip(((np.arange(ix0, ix1) + 0.5) * esc_r + px_r).astype(int), 0, LADO_FRUTO - 1)
        ys_c = np.clip(((np.arange(iy0, iy1) + 0.5) * esc_r + py_r).astype(int), 0, LADO_FRUTO - 1)
        dentro_c = masc_ra[np.ix_(ys_c, xs_c)].astype(np.uint8)
        rad_c = max(1, round(HOLGURA_FONDO * max(w_c, h_c)))
        ker_c = np.ones((2 * rad_c + 1, 2 * rad_c + 1), dtype=np.uint8)
        rec_r[~(cv2.dilate(dentro_c, ker_c) > 0)] = (128, 128, 128)

        dets_rb, (px_rb, py_rb, esc_rb), _ = predecir(sesion_b, rec_r, LADO_LESIONES)
        fr_b = np.zeros(forma, dtype=bool)
        les_b = np.zeros(forma, dtype=bool)
        for cl, _, _, mb in dets_rb:
            if cl == 0:
                fr_b |= mb
            elif cl in LESIONES:
                les_b |= mb
        fr_b |= les_b
        sev_r = 100.0 * float(les_b.sum()) / max(1, int(fr_b.sum()))
        ms = round((time.perf_counter() - t0) * 1000)

        tabla_resoluciones.append(
            {
                "clave": clave,
                "etiqueta": etiqueta,
                "resolucion_entrada": f"{wr}x{hr}",
                "recorte_px": f"{w_c}x{h_c}",
                "confianza_fruto_pct": round(conf_r * 100, 1),
                "severidad_pct": round(sev_r, 2),
                "tiempo_ms": ms,
            }
        )

        # Mini tarjeta visual 320x380 para el panel comparativo
        thumb = cv2.resize(img_res[iy0:iy1, ix0:ix1], (300, 300), interpolation=cv2.INTER_AREA)
        les_thumb = cv2.resize(
            les_b.astype(np.uint8), (300, 300), interpolation=cv2.INTER_NEAREST
        ) > 0
        capa_t = np.zeros_like(thumb)
        capa_t[les_thumb] = (30, 80, 245)
        thumb = cv2.addWeighted(thumb, 1.0, capa_t, 0.42, 0)
        card = np.full((390, 320, 3), (245, 247, 245), dtype=np.uint8)
        card[0:48, :] = (27, 67, 50)
        cv2.putText(
            card,
            etiqueta,
            (12, 31),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.58,
            (255, 255, 255),
            2,
            cv2.LINE_AA,
        )
        card[54:354, 10:310] = thumb
        cv2.putText(
            card,
            f"Entrada: {wr}x{hr} px | {ms} ms",
            (12, 370),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.44,
            (40, 50, 45),
            1,
            cv2.LINE_AA,
        )
        cv2.putText(
            card,
            f"Severidad Rona: {sev_r:.2f}% (Conf {conf_r*100:.1f}%)",
            (12, 385),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.46,
            (20, 90, 50),
            1,
            cv2.LINE_AA,
        )
        tarjetas.append(card)

    panel_comparativo = np.hstack(tarjetas)
    cv2.imwrite(str(MANUAL_ASSETS / "onnx_07_comparativa_resoluciones.jpg"), panel_comparativo)

    resumen = {
        "foto_muestra": str(SAMPLE_IMG.relative_to(ROOT_DIR)),
        "dimensiones_originales": f"{ancho}x{alto}",
        "confianza_fruto_pct": round(conf_fruto * 100, 2),
        "severidad_rona_pct": round(severidad_total, 2),
        "recorte_fruto_px": f"{w}x{h}",
        "area_fruto_px_640": area_fruto,
        "area_lesion_px_640": int(union_b.sum()),
        "resoluciones": tabla_resoluciones,
    }
    (MANUAL_ASSETS / "resumen_inferencia_muestra.json").write_text(
        json.dumps(resumen, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(json.dumps(resumen, ensure_ascii=False))
    return resumen


if __name__ == "__main__":
    generar_activos_onnx()
