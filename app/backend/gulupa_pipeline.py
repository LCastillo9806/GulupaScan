# -*- coding: utf-8 -*-
"""Pipeline de segmentación en servidor para el modelo de gulupa (`gulupa_pipeline.py`).

Cómo se activa:
    Basta con que exista el archivo de pesos `models/gulupa_seg.pt` (o la ruta definida
    en la variable de entorno `GULUPA_SEG_MODEL`). Si no existe, `api.py` utiliza el
    pipeline de respaldo PowderyVision7.

Clases soportadas:
    - `fruto` / `gulupa` / `fruit`: contorno completo del fruto (obligatoria).
    - `rona` / `scab`: sarna o roña (*Cladosporium cladosporioides*).
    - `anthracnose` / `antracnosis`: antracnosis (*Colletotrichum gloeosporioides*).
    - `oil_spot` / `bacteriosis`: mancha de aceite (*Xanthomonas axonopodis*).
    - `dano_mecanico` / `mechanical`: daño mecánico.
    - `trips` / `thrips`: daño por trips.
"""

from __future__ import annotations

import os
import time
import unicodedata
from typing import Any

import cv2
import numpy as np

MODEL_PATH: str = os.environ.get("GULUPA_SEG_MODEL", "models/gulupa_seg.pt")
CONF_MIN: float = float(os.environ.get("GULUPA_CONF", "0.25"))
FRUIT_DIAMETER_CM: float = float(os.environ.get("GULUPA_DIAMETRO_CM", "0") or 0)

FRUIT = "fruit"
HEALTHY = "healthy"

CLASS_TO_KEY: dict[str, str] = {
    "fruto": FRUIT,
    "gulupa": FRUIT,
    "fruit": FRUIT,
    "fruta": FRUIT,
    "rona": "scab",
    "scab": "scab",
    "cladosporium": "scab",
    "antracnosis": "anthracnose",
    "anthracnose": "anthracnose",
    "oil_spot": "oil_spot",
    "mancha_de_aceite": "oil_spot",
    "bacteriosis": "oil_spot",
    "dano_mecanico": "mechanical",
    "mecanico": "mechanical",
    "mechanical": "mechanical",
    "golpe": "mechanical",
    "mechanical_damage": "mechanical",
    "trips": "thrips",
    "thrips": "thrips",
}

NAMES_ES: dict[str, str] = {
    "scab": "sarna (roña)",
    "anthracnose": "antracnosis",
    "oil_spot": "mancha de aceite",
    "mechanical": "daño mecánico",
    "thrips": "trips",
}
NAMES_EN: dict[str, str] = {
    "scab": "scab",
    "anthracnose": "anthracnose",
    "oil_spot": "oil spot",
    "mechanical": "mechanical damage",
    "thrips": "thrips",
}

COLORS_RGB: dict[str, tuple[int, int, int]] = {
    "scab": (229, 57, 53),
    "anthracnose": (142, 36, 170),
    "oil_spot": (251, 140, 0),
    "mechanical": (47, 128, 237),
    "thrips": (242, 153, 74),
}
OTHER_RGB: tuple[int, int, int] = (142, 68, 173)

_model: Any = None


def available() -> bool:
    """Indica si el archivo de pesos del modelo de gulupa está disponible en disco."""
    return os.path.isfile(MODEL_PATH)


def _get_model() -> Any:
    """Carga e inicializa de forma perezosa (lazy) el modelo YOLO."""
    global _model
    if _model is None:
        from ultralytics import YOLO  # Importación diferida

        _model = YOLO(MODEL_PATH)
    return _model


def _norm(name: str) -> str:
    """Normaliza un nombre de clase a minúsculas ASCII sin tildes ni espacios."""
    s = (
        unicodedata.normalize("NFKD", str(name))
        .encode("ascii", "ignore")
        .decode("ascii")
    )
    return s.strip().lower().replace(" ", "_").replace("-", "_")


def class_key(name: str) -> str:
    """Traduce el nombre de clase del modelo a la clave estándar del catálogo."""
    n = _norm(name)
    return CLASS_TO_KEY.get(n, n)


def _bi(en: str, es: str) -> str:
    """Formatea un mensaje bilingüe en el formato 'English / Español'."""
    return f"{en} / {es}"


def analyze(bgr: np.ndarray) -> dict[str, Any]:
    """Ejecuta la segmentación sobre una imagen BGR y devuelve métricas y capas visuales."""
    height, width = bgr.shape[:2]
    t0 = time.time()
    res = _get_model()(bgr, conf=CONF_MIN, retina_masks=True, verbose=False)[0]
    seg_ms = (time.time() - t0) * 1000.0

    base: dict[str, Any] = {
        "ok": False,
        "cls_time_ms": 0.0,
        "seg_time_ms": seg_ms,
        "species": "gulupa",
        "crop_name_es": "Gulupa",
        "layers": {"original": bgr},
    }

    if res.masks is None or res.boxes is None or len(res.boxes) == 0:
        base["msg"] = _bi(
            "No gulupa was detected. Take a closer, centered photo.",
            "No se detectó una gulupa. Toma la foto más cerca y centrada.",
        )
        return base

    masks = res.masks.data.cpu().numpy()
    classes = res.boxes.cls.cpu().numpy().astype(int)
    confs = res.boxes.conf.cpu().numpy()
    names = res.names

    def full_mask(idx: int) -> np.ndarray:
        m = (masks[idx] > 0.5).astype(np.uint8)
        if m.shape != (height, width):
            m = cv2.resize(m, (width, height), interpolation=cv2.INTER_NEAREST)
        return m

    keys = [class_key(names[int(c)]) for c in classes]
    fruit_idx = [i for i, k in enumerate(keys) if k == FRUIT]
    if not fruit_idx:
        base["msg"] = _bi(
            "The fruit outline was not detected (class 'fruto').",
            "No se detectó el contorno del fruto (clase 'fruto').",
        )
        return base

    best = max(fruit_idx, key=lambda i: int(full_mask(i).sum()))
    fruit = full_mask(best)
    fruit_area = int(fruit.sum())
    if fruit_area < 500:
        base["msg"] = _bi(
            "The fruit is too small in the photo.",
            "El fruto se ve muy pequeño en la foto.",
        )
        return base

    per_key: dict[str, np.ndarray] = {}
    for i, k in enumerate(keys):
        if k == FRUIT:
            continue
        m = full_mask(i) & fruit
        per_key[k] = per_key.get(k, np.zeros_like(fruit)) | m

    any_damage = np.zeros_like(fruit)
    for m in per_key.values():
        any_damage |= m

    cm2_per_fruit = (
        float(np.pi * (FRUIT_DIAMETER_CM / 2.0) ** 2)
        if FRUIT_DIAMETER_CM > 0
        else None
    )

    def entry(key: str, area_px: int) -> dict[str, Any]:
        pct = round(100.0 * area_px / fruit_area, 2)
        cm2 = round(cm2_per_fruit * pct / 100.0, 2) if cm2_per_fruit else None
        return {"key": key, "pct": pct, "area_cm2": cm2}

    damages = sorted(
        (entry(k, int(m.sum())) for k, m in per_key.items() if m.sum() > 0),
        key=lambda d: -d["pct"],
    )
    damages.append(entry(HEALTHY, fruit_area - int(any_damage.sum())))

    ys, xs = np.where(fruit > 0)
    pad = 10
    y1, y2 = max(0, int(ys.min()) - pad), min(height, int(ys.max()) + pad + 1)
    x1, x2 = max(0, int(xs.min()) - pad), min(width, int(xs.max()) + pad + 1)
    crop = bgr[y1:y2, x1:x2].copy()
    fruit_c = fruit[y1:y2, x1:x2]
    leaf = cv2.bitwise_and(crop, crop, mask=fruit_c)
    mask_bin = (any_damage[y1:y2, x1:x2] * 255).astype(np.uint8)

    overlay = np.zeros((crop.shape[0], crop.shape[1], 4), dtype=np.uint8)
    overlays: dict[str, np.ndarray] = {}
    result = leaf.copy()
    for k, m in per_key.items():
        mc = m[y1:y2, x1:x2]
        if mc.sum() == 0:
            continue
        r, g, b = COLORS_RGB.get(k, OTHER_RGB)
        single = np.zeros_like(overlay)
        single[mc > 0] = (b, g, r, 255)
        overlays[k] = single
        overlay[mc > 0] = (b, g, r, 255)
        cnts, _ = cv2.findContours(
            (mc * 255).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
        )
        cv2.drawContours(result, cnts, -1, (b, g, r), 3)

    fc, _ = cv2.findContours(
        (fruit_c * 255).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    cv2.drawContours(result, fc, -1, (68, 158, 46), 2)

    hsv = cv2.cvtColor(leaf, cv2.COLOR_BGR2HSV)
    norm_h = cv2.normalize(hsv[:, :, 0], None, 0, 255, cv2.NORM_MINMAX).astype(
        np.uint8
    )
    hue = cv2.applyColorMap(norm_h, cv2.COLORMAP_HSV)
    hue[fruit_c == 0] = 0

    harmful = [d for d in damages if d["key"] != HEALTHY]
    top = harmful[0]["key"] if harmful else None
    total = round(sum(float(d["pct"]) for d in harmful), 2)

    return {
        **base,
        "ok": True,
        "msg": None,
        "cls_name": f"gulupa_{top}" if top else "gulupa_sana",
        "cls_conf": float(confs[best]),
        "leaf_conf": float(confs[best]),
        "is_healthy": not harmful,
        "disease": NAMES_EN.get(top, top) if top else None,
        "disease_es": NAMES_ES.get(top, top) if top else None,
        "status": "diseased" if harmful else "healthy",
        "status_es": "con daño" if harmful else "sana",
        "severidad_pct": total,
        "damages": damages,
        "layers": {
            "original": bgr,
            "crop": result,
            "leaf": leaf,
            "mask": mask_bin,
            "overlay": overlay,
            "overlays": overlays,
            "result": result,
            "hue": hue,
        },
    }
