# -*- coding: utf-8 -*-
"""Generador de documentos independientes con imágenes incrustadas (Base64) en HTML, PDF y Word."""

import base64
import html
import mimetypes
import os
import pathlib
import re
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
MD_FILE = DOCS / "MANUAL_USUARIO_Y_TECNICO.md"
HTML_FILE = DOCS / "MANUAL_USUARIO_Y_TECNICO.html"
PDF_FILE = DOCS / "MANUAL_USUARIO_Y_TECNICO.pdf"
DOCX_FILE = DOCS / "MANUAL_USUARIO_Y_TECNICO.docx"

def get_base64_img(rel_path: str) -> str:
    # Si viene como "assets/foto.jpg", buscarlo en "docs/manual/assets/foto.jpg" o "docs/assets/foto.jpg"
    candidates = [
        DOCS / rel_path,
        DOCS / "manual" / rel_path,
        ROOT / rel_path,
        ROOT / "docs" / "manual" / rel_path,
    ]
    img_path = next((p for p in candidates if p.exists()), None)
    if not img_path:
        print(f"ALERTA: No se encontró la imagen: {rel_path}")
        return rel_path
    mime, _ = mimetypes.guess_type(str(img_path))
    if not mime:
        mime = "image/jpeg" if img_path.suffix.lower() in [".jpg", ".jpeg"] else "image/png"
    b64 = base64.b64encode(img_path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{b64}"

def enlaces(texto: str) -> str:
    """Convierte [texto](url) y <https://...> de Markdown en HTML; los anclajes internos quedan como texto."""
    def reemplazo(m):
        etiqueta, destino = m.group(1), m.group(2)
        if destino.startswith("http"):
            return f'<a href="{destino}" style="color:#2d6a4f;">{etiqueta}</a>'
        return etiqueta
    texto = re.sub(r'(?<!!)\[([^\]]+)\]\(([^)]+)\)', reemplazo, texto)
    return re.sub(r'<(https?://[^>]+)>', r'<a href="\1" style="color:#2d6a4f;">\1</a>', texto)


def generar_html_con_imagenes_embebidas():
    content = MD_FILE.read_text(encoding="utf-8")
    lines = content.split("\n")
    html_lines = []
    in_table = False

    for line in lines:
        stripped = line.strip()

        # Tablas markdown
        if stripped.startswith("|") and stripped.endswith("|"):
            parts = [p.strip() for p in stripped.split("|")[1:-1]]
            if all(re.match(r"^:?-+:?$", p) for p in parts):
                continue
            if not in_table:
                in_table = True
                html_lines.append('<table style="width:100%; border-collapse:collapse; margin:22px 0; font-size:14px; page-break-inside:avoid;">')
                html_lines.append('<thead><tr style="background:#1b4332; color:white;">')
                for p in parts:
                    html_lines.append(f'<th style="border:1px solid #ccc; padding:10px 12px; text-align:center;">{p}</th>')
                html_lines.append('</tr></thead><tbody>')
            else:
                html_lines.append('<tr style="border-bottom:1px solid #ddd; background:#fafafa;">')
                for p in parts:
                    img_match = re.search(r'!\[(.*?)\]\((.*?)\)', p)
                    if img_match:
                        b64_src = get_base64_img(img_match.group(2))
                        cell_content = (
                            f'<div style="text-align:center; padding:6px;">'
                            f'<img src="{b64_src}" style="max-width:240px; height:auto; border-radius:6px; box-shadow:0 3px 8px rgba(0,0,0,0.15); display:block; margin:0 auto;">'
                            f'<div style="color:#444; font-size:12px; margin-top:6px; font-weight:600;">{img_match.group(1)}</div>'
                            f'</div>'
                        )
                    else:
                        fmt_p = re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', p)
                        fmt_p = re.sub(r'`(.*?)`', r'<code style="background:#e8f5e9; padding:2px 4px; border-radius:3px;">\1</code>', fmt_p)
                        cell_content = enlaces(fmt_p)
                    html_lines.append(f'<td style="border:1px solid #ddd; padding:10px; vertical-align:middle;">{cell_content}</td>')
                html_lines.append('</tr>')
            continue
        elif in_table:
            in_table = False
            html_lines.append('</tbody></table>')

        # Headings
        if stripped.startswith("# "):
            html_lines.append(f'<h1 style="color:#1b4332; border-bottom:3px solid #2d6a4f; padding-bottom:8px; margin-top:36px; font-size:26px;">{stripped[2:]}</h1>')
        elif stripped.startswith("## "):
            html_lines.append(f'<h2 style="color:#2d6a4f; border-bottom:1.5px solid #b7e4c7; padding-bottom:6px; margin-top:30px; font-size:20px; page-break-after:avoid;">{stripped[3:]}</h2>')
        elif stripped.startswith("### "):
            html_lines.append(f'<h3 style="color:#386641; margin-top:22px; font-size:17px; page-break-after:avoid;">{stripped[4:]}</h3>')
        elif stripped.startswith("#### "):
            html_lines.append(f'<h4 style="color:#52b788; margin-top:16px; font-size:15px;">{stripped[5:]}</h4>')
        # Blockquotes
        elif stripped.startswith("> "):
            bq = stripped[2:]
            bq = re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', bq)
            bq = re.sub(r'`(.*?)`', r'<code style="background:#e8f5e9; padding:2px 4px; border-radius:3px;">\1</code>', bq)
            html_lines.append(f'<blockquote style="border-left:4px solid #2d6a4f; background:#f4f9f5; margin:14px 0; padding:10px 16px; border-radius:0 6px 6px 0; color:#1b4332;">{bq}</blockquote>')
        # Separator
        elif stripped == "---":
            html_lines.append('<hr style="border:none; border-top:1px solid #e0e0e0; margin:28px 0;">')
        # Images outside tables
        elif re.match(r'^!\[(.*?)\]\((.*?)\)$', stripped):
            m = re.match(r'^!\[(.*?)\]\((.*?)\)$', stripped)
            b64_src = get_base64_img(m.group(2))
            html_lines.append(
                f'<div style="text-align:center; margin:24px 0; page-break-inside:avoid;">'
                f'<img src="{b64_src}" style="max-width:88%; height:auto; border-radius:8px; box-shadow:0 4px 14px rgba(0,0,0,0.18); display:block; margin:0 auto;">'
                f'<div style="color:#555; font-size:13px; font-style:italic; margin-top:8px;">{m.group(1)}</div>'
                f'</div>'
            )
        # Bullet lists
        elif stripped.startswith("- "):
            item = stripped[2:]
            item = re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', item)
            item = re.sub(r'\*(.*?)\*', r'<em>\1</em>', item)
            item = re.sub(r'`(.*?)`', r'<code style="background:#eef5f0; padding:2px 4px; border-radius:3px; font-family:monospace; color:#1b4332;">\1</code>', item)
            html_lines.append(f'<li style="margin-bottom:6px; line-height:1.5;">{enlaces(item)}</li>')
        elif re.match(r'^\d+\.\s', stripped):
            item = re.sub(r'^\d+\.\s', '', stripped)
            item = re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', item)
            item = re.sub(r'\*(.*?)\*', r'<em>\1</em>', item)
            item = re.sub(r'`(.*?)`', r'<code style="background:#eef5f0; padding:2px 4px; border-radius:3px; font-family:monospace; color:#1b4332;">\1</code>', item)
            html_lines.append(f'<li style="margin-bottom:6px; line-height:1.5;">{enlaces(item)}</li>')
        # Paragraphs
        elif stripped:
            formatted = re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', stripped)
            formatted = re.sub(r'\*(.*?)\*', r'<em>\1</em>', formatted)
            formatted = re.sub(r'`(.*?)`', r'<code style="background:#eef5f0; padding:2px 5px; border-radius:3px; font-family:monospace; color:#1b4332;">\1</code>', formatted)
            html_lines.append(f'<p style="line-height:1.65; color:#222; margin:10px 0;">{enlaces(formatted)}</p>')
        else:
            html_lines.append('<br>')

    if in_table:
        html_lines.append('</tbody></table>')

    body_html = "\n".join(html_lines)
    full_html = f"""<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Manual Técnico y de Usuario — GulupaScan</title>
<style>
@page {{
    size: letter portrait;
    margin: 18mm 15mm;
}}
body {{
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: #1a1a1a;
    background: #ffffff;
    max-width: 900px;
    margin: 0 auto;
    padding: 24px;
}}
img {{
    max-width: 100%;
    height: auto;
}}
table {{
    page-break-inside: avoid;
}}
h1, h2, h3, h4 {{
    page-break-after: avoid;
}}
@media print {{
    body {{
        padding: 0;
        max-width: 100%;
    }}
}}
</style>
</head>
<body>
{body_html}
</body>
</html>"""

    HTML_FILE.write_text(full_html, encoding="utf-8")
    print(f"OK: Generado HTML con imagenes Base64 ({len(full_html)} bytes)")

def generar_pdf():
    # Busca Chrome/Edge en macOS o Windows
    candidatos = [
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    ]
    chrome = next((c for c in candidatos if os.path.exists(c)), candidatos[0])
    cmd = [
        chrome,
        "--headless",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--print-to-pdf={PDF_FILE}",
        HTML_FILE.as_uri(),
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if PDF_FILE.exists() and PDF_FILE.stat().st_size > 50000:
        print(f"OK: PDF generado ({PDF_FILE.stat().st_size} bytes)")
    else:
        print("Error al generar PDF:", res.stderr)

def generar_docx_autocontenido():
    """Crea un documento .docx real con estructura OpenXML y todas las imágenes binarias embebidas en word/media/."""
    out_zip = DOCX_FILE
    content = MD_FILE.read_text(encoding="utf-8")
    lines = content.split("\n")

    # Extraer todas las imágenes referenciadas en el markdown
    img_refs = []
    for line in lines:
        for m in re.finditer(r'!\[(.*?)\]\((.*?)\)', line):
            alt, rel = m.group(1), m.group(2)
            # Resolver ruta
            candidates = [DOCS / rel, DOCS / "manual" / rel, ROOT / rel]
            real_path = next((p for p in candidates if p.exists()), None)
            if real_path and real_path not in [x[1] for x in img_refs]:
                img_refs.append((alt, real_path))

    # Mapear imágenes a rId y nombres en word/media/image1.ext
    media_files = {}
    rids = {}
    for i, (alt, p) in enumerate(img_refs, start=1):
        ext = p.suffix.lower()
        target = f"image{i}{ext}"
        rId = f"rId{i+5}"
        media_files[target] = p.read_bytes()
        rids[str(p)] = (rId, target, alt)

    # Construir word/document.xml
    body_xml = []
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("# "):
            title = html.escape(stripped[2:])
            body_xml.append(f'<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val="1B4332"/><w:sz w:val="48"/></w:rPr><w:t>{title}</w:t></w:r></w:p>')
        elif stripped.startswith("## "):
            title = html.escape(stripped[3:])
            body_xml.append(f'<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val="2D6A4F"/><w:sz w:val="36"/></w:rPr><w:t>{title}</w:t></w:r></w:p>')
        elif stripped.startswith("### "):
            title = html.escape(stripped[4:])
            body_xml.append(f'<w:p><w:pPr><w:pStyle w:val="Heading3"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val="40916C"/><w:sz w:val="28"/></w:rPr><w:t>{title}</w:t></w:r></w:p>')
        elif re.search(r'!\[(.*?)\]\((.*?)\)', stripped):
            for m in re.finditer(r'!\[(.*?)\]\((.*?)\)', stripped):
                rel = m.group(2)
                candidates = [DOCS / rel, DOCS / "manual" / rel, ROOT / rel]
                real_p = next((p for p in candidates if p.exists()), None)
                if real_p and str(real_p) in rids:
                    rId, target, alt = rids[str(real_p)]
                    # Insertar dibujo OpenXML 5.5 x 3.5 pulgadas
                    cx, cy = 4800000, 3200000
                    drawing_xml = (
                        f'<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing>'
                        f'<wp:inline distT="0" distB="0" distL="0" distR="0">'
                        f'<wp:extent cx="{cx}" cy="{cy}"/>'
                        f'<wp:docPr id="{rId.replace("rId","")}" name="{html.escape(alt)}"/>'
                        f'<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
                        f'<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
                        f'<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
                        f'<pic:nvPicPr><pic:cNvPr id="0" name="{target}"/><pic:cNvPicPr/></pic:nvPicPr>'
                        f'<pic:blipFill><a:blip r:embed="{rId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
                        f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>'
                        f'</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
                        f'<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:i/><w:color w:val="555555"/><w:sz w:val="18"/></w:rPr><w:t>{html.escape(alt)}</w:t></w:r></w:p>'
                    )
                    body_xml.append(drawing_xml)
        elif stripped.startswith("- "):
            txt = html.escape(stripped[2:])
            body_xml.append(f'<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>• {txt}</w:t></w:r></w:p>')
        else:
            txt = html.escape(stripped)
            body_xml.append(f'<w:p><w:r><w:t>{txt}</w:t></w:r></w:p>')

    doc_xml_str = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
        'xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">\n'
        '<w:body>\n' + "\n".join(body_xml) + '\n<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>'
    )

    # word/_rels/document.xml.rels
    rels_lines = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">',
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>',
    ]
    for p_str, (rId, target, _) in rids.items():
        rels_lines.append(f'<Relationship Id="{rId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{target}"/>')
    rels_lines.append('</Relationships>')
    rels_str = "\n".join(rels_lines)

    content_types = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">',
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>',
        '<Default Extension="xml" ContentType="application/xml"/>',
        '<Default Extension="jpg" ContentType="image/jpeg"/>',
        '<Default Extension="jpeg" ContentType="image/jpeg"/>',
        '<Default Extension="png" ContentType="image/png"/>',
        '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>',
        '</Types>'
    ]

    with zipfile.ZipFile(out_zip, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('[Content_Types].xml', "\n".join(content_types))
        z.writestr('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        z.writestr('word/document.xml', doc_xml_str)
        z.writestr('word/_rels/document.xml.rels', rels_str)
        for target, data in media_files.items():
            z.writestr(f'word/media/{target}', data)

    print(f"OK: Documento Word real generado con imagenes ({out_zip.stat().st_size} bytes, {len(media_files)} imagenes embebidas)")

if __name__ == "__main__":
    generar_html_con_imagenes_embebidas()
    generar_pdf()
    generar_docx_autocontenido()
