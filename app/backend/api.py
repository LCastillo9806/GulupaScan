# -*- coding: utf-8 -*-
"""API REST JSON para la aplicación móvil GulupaScan (Android / iOS).

Expone dos endpoints sobre el servidor Flask:
    - `GET  /api/health`  -> `{"ok": true, "api_version": 1, "engine": ...}`
    - `POST /api/analyze` -> recibe `multipart/form-data` con el campo `"image"` y
                             devuelve el diagnóstico fitosanitario junto con las
                             capas segmentadas codificadas en Base64.
"""

from __future__ import annotations

import base64
import os
import shutil
import threading
import uuid
from typing import Any

import cv2
import numpy as np
from flask import Flask, Response, jsonify, request

import gulupa_pipeline

try:
    import segment_mildew
    from app import app
    from pipeline import run_full_pipeline

    HAS_PV7 = True
except Exception as _exc:  # noqa: BLE001
    if not gulupa_pipeline.available():
        raise
    print(f"[api] PowderyVision7 no disponible ({_exc}); usando motor de gulupa.")
    app = Flask(__name__)
    HAS_PV7 = False

API_VERSION = 1
MAX_SIDE_UPLOAD = 1600
MAX_SIDE_RETURN = 900
JPEG_QUALITY = 85

_capture = threading.local()

if HAS_PV7:
    _original_panel_fn = segment_mildew._panel_to_array

    def _panel_hook(
        rgb: np.ndarray,
        lr_u: np.ndarray,
        ent_u: np.ndarray,
        delta_e: np.ndarray,
        seg: np.ndarray,
        vis: np.ndarray,
        porcentaje: float,
    ) -> Any:
        """Captura en memoria las capas intermedias generadas durante la segmentación."""
        _capture.layers = {
            "leaf_rgb": np.asarray(rgb).copy(),
            "mask": np.asarray(seg).copy(),
            "result_rgb": np.asarray(vis).copy(),
        }
        return _original_panel_fn(rgb, lr_u, ent_u, delta_e, seg, vis, porcentaje)

    segment_mildew._panel_to_array = _panel_hook


def engine() -> str:
    """Devuelve el nombre del motor de inferencia activo en el servidor."""
    return "gulupa" if gulupa_pipeline.available() else "powderyvision7"


def _resize_max(img: np.ndarray, max_side: int) -> np.ndarray:
    """Redimensiona una imagen si su lado mayor supera `max_side` píxeles."""
    h, w = img.shape[:2]
    if max(h, w) <= max_side:
        return img
    scale = max_side / float(max(h, w))
    interp = cv2.INTER_NEAREST if img.ndim == 2 else cv2.INTER_AREA
    return cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=interp)


def _b64(img_bgr_or_gray: np.ndarray | None, fmt: str = ".jpg") -> str | None:
    """Codifica un arreglo de imagen OpenCV en una cadena Base64."""
    if img_bgr_or_gray is None:
        return None
    img = _resize_max(img_bgr_or_gray, MAX_SIDE_RETURN)
    params = [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY] if fmt == ".jpg" else []
    ok, buf = cv2.imencode(fmt, img, params)
    return base64.b64encode(buf.tobytes()).decode("ascii") if ok else None


def _read_static(web_path: str | None) -> np.ndarray | None:
    """Lee una imagen generada en una ruta relativa tipo `/static/results/...`."""
    if not web_path:
        return None
    return cv2.imread(web_path.lstrip("/"), cv2.IMREAD_COLOR)


def _overlay_rgba(
    mask: np.ndarray, color_bgr: tuple[int, int, int] = (57, 57, 229)
) -> np.ndarray:
    """Genera una máscara PNG RGBA con fondo transparente para superposición."""
    h, w = mask.shape[:2]
    out = np.zeros((h, w, 4), dtype=np.uint8)
    on = mask > 0
    out[on, 0], out[on, 1], out[on, 2] = color_bgr
    out[on, 3] = 255
    return out


def _hue_channel(leaf_rgb: np.ndarray) -> np.ndarray:
    """Genera un mapa de calor falso color a partir del canal Hue (HSV)."""
    hsv = cv2.cvtColor(leaf_rgb, cv2.COLOR_RGB2HSV)
    norm_h = cv2.normalize(hsv[:, :, 0], None, 0, 255, cv2.NORM_MINMAX).astype(
        np.uint8
    )
    return cv2.applyColorMap(norm_h, cv2.COLORMAP_HSV)


def _split_bilingual(msg: str | None) -> tuple[str | None, str | None]:
    """Separa un mensaje bilingüe `'English / Español'` en una tupla `(en, es)`."""
    if not msg:
        return None, None
    parts = msg.split(" / ", 1)
    return (parts[0], parts[1]) if len(parts) == 2 else (msg, msg)


def _damages(res: dict[str, Any]) -> list[dict[str, Any]]:
    """Construye la lista estándar de daños y porcentajes de severidad."""
    if res.get("is_healthy"):
        return [{"key": "healthy", "pct": 100.0}]
    sev = float(res.get("severidad_pct") or 0.0)
    sev = max(0.0, min(100.0, sev))
    return [
        {"key": "powdery_mildew", "pct": round(sev, 2)},
        {"key": "healthy", "pct": round(100.0 - sev, 2)},
    ]


@app.route("/api/health", methods=["GET"])
def api_health() -> Response:
    """Endpoint de verificación de salud y estado del motor de inferencia."""
    return jsonify(
        {
            "ok": True,
            "api_version": API_VERSION,
            "engine": engine(),
            "gulupa_model": (
                gulupa_pipeline.MODEL_PATH if gulupa_pipeline.available() else None
            ),
        }
    )


def _analyze_gulupa(bgr: np.ndarray) -> Response:
    """Procesa una imagen BGR con el pipeline específico de gulupa."""
    res = gulupa_pipeline.analyze(bgr)
    layers = res.pop("layers", {}) or {}
    images = {
        "original": _b64(layers.get("original")),
        "crop": _b64(layers.get("crop")),
        "leaf": _b64(layers.get("leaf")),
        "mask": _b64(layers.get("mask"), ".png"),
        "overlay": _b64(layers.get("overlay"), ".png"),
        "result": _b64(layers.get("result")),
        "hue": _b64(layers.get("hue")),
        "panel": None,
        "overlays": {
            k: _b64(v, ".png") for k, v in (layers.get("overlays") or {}).items()
        },
    }
    msg_en, msg_es = _split_bilingual(res.get("msg"))
    res.update(
        {
            "api_version": API_VERSION,
            "engine": "gulupa",
            "msg_en": msg_en,
            "msg_es": msg_es,
            "images": images,
        }
    )
    if not res.get("ok"):
        res["damages"] = []
    return jsonify(res)


@app.route("/api/analyze", methods=["POST"])
def api_analyze() -> tuple[Response, int] | Response:
    """Endpoint principal para analizar una fotografía enviada desde la app móvil."""
    file = request.files.get("image")
    if not file or file.filename == "":
        return (
            jsonify(
                {
                    "ok": False,
                    "api_version": API_VERSION,
                    "msg": "No image was received. / No se recibió ninguna imagen.",
                }
            ),
            400,
        )

    bgr = cv2.imdecode(np.frombuffer(file.read(), np.uint8), cv2.IMREAD_COLOR)
    if bgr is None:
        return (
            jsonify(
                {
                    "ok": False,
                    "api_version": API_VERSION,
                    "msg": "Could not read the image. / No se pudo leer la imagen.",
                }
            ),
            400,
        )

    bgr = _resize_max(bgr, MAX_SIDE_UPLOAD)

    if gulupa_pipeline.available():
        try:
            return _analyze_gulupa(bgr)
        except Exception as exc:  # noqa: BLE001
            return (
                jsonify(
                    {
                        "ok": False,
                        "api_version": API_VERSION,
                        "msg": f"Gulupa model error: {exc} / Error en el modelo de gulupa: {exc}",
                    }
                ),
                500,
            )

    prefix = "api_" + uuid.uuid4().hex[:8]
    save_root = os.path.join("static", "results", prefix)
    _capture.layers = None

    try:
        res = run_full_pipeline(bgr, save_root=save_root, prefix=prefix)
        layers = getattr(_capture, "layers", None)

        images: dict[str, Any] = {
            "original": _b64(bgr),
            "crop": _b64(_read_static(res.get("crop_img_path"))),
            "panel": _b64(_read_static(res.get("panel_img_path"))),
            "leaf": None,
            "mask": None,
            "overlay": None,
            "result": None,
            "hue": None,
        }
        if layers is not None and res.get("seg_ok"):
            leaf_bgr = cv2.cvtColor(layers["leaf_rgb"], cv2.COLOR_RGB2BGR)
            images["leaf"] = _b64(leaf_bgr)
            images["mask"] = _b64(layers["mask"], ".png")
            images["overlay"] = _b64(_overlay_rgba(layers["mask"]), ".png")
            images["result"] = _b64(
                cv2.cvtColor(layers["result_rgb"], cv2.COLOR_RGB2BGR)
            )
            images["hue"] = _b64(_hue_channel(layers["leaf_rgb"]))

        msg_en, msg_es = _split_bilingual(res.get("msg"))
        payload = {
            "ok": bool(res.get("ok")),
            "api_version": API_VERSION,
            "engine": "powderyvision7",
            "msg": res.get("msg"),
            "msg_en": msg_en,
            "msg_es": msg_es,
            "cls_name": res.get("cls_name"),
            "cls_conf": float(res.get("cls_conf") or 0.0),
            "cls_time_ms": float(res.get("cls_time_ms") or 0.0),
            "seg_time_ms": float(res.get("seg_time_ms") or 0.0),
            "species": res.get("species"),
            "crop_name_es": res.get("crop_name_es"),
            "status": res.get("status"),
            "status_es": res.get("status_es"),
            "disease": res.get("disease"),
            "disease_es": res.get("disease_es"),
            "is_healthy": bool(res.get("is_healthy")),
            "leaf_conf": float(res.get("leaf_conf") or 0.0),
            "severidad_pct": float(res.get("severidad_pct") or 0.0),
            "damages": _damages(res) if res.get("ok") else [],
            "images": images,
        }
        return jsonify(payload)
    except Exception as exc:  # noqa: BLE001
        return (
            jsonify(
                {
                    "ok": False,
                    "api_version": API_VERSION,
                    "msg": f"Model pipeline error: {exc} / Error en el pipeline de modelos: {exc}",
                }
            ),
            500,
        )
    finally:
        _capture.layers = None
        shutil.rmtree(save_root, ignore_errors=True)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=True)
