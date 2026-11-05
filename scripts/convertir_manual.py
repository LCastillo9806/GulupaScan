# -*- coding: utf-8 -*-
"""Convierte docs/MANUAL_USUARIO_Y_TECNICO.md a HTML enriquecido listo para imprimir a PDF o abrir en Word."""

import html
import os
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
MD_FILE = DOCS / "MANUAL_USUARIO_Y_TECNICO.md"
HTML_FILE = DOCS / "MANUAL_USUARIO_Y_TECNICO.html"

def md_to_html():
    content = MD_FILE.read_text(encoding="utf-8")

    # Reemplazo de links a imágenes relativas para que carguen en el HTML
    # ![alt](assets/...) -> <img src="assets/..." alt="alt" style="max-width:100%; border-radius:8px; box-shadow:0 4px 12px rgba(0,0,0,0.15); margin:12px 0;">

    html_lines = []
    in_table = False
    table_rows = []

    lines = content.split("\n")
    for line in lines:
        stripped = line.strip()

        # Tablas markdown
        if stripped.startswith("|") and stripped.endswith("|"):
            parts = [p.strip() for p in stripped.split("|")[1:-1]]
            if all(re.match(r"^:?-+:?$", p) for p in parts):
                continue # separador de header
            if not in_table:
                in_table = True
                html_lines.append('<table style="width:100%; border-collapse:collapse; margin:20px 0; font-size:14px;">')
                html_lines.append('<thead><tr style="background:#274332; color:white;">')
                for p in parts:
                    html_lines.append(f'<th style="border:1px solid #ccc; padding:10px;">{p}</th>')
                html_lines.append('</tr></thead><tbody>')
            else:
                html_lines.append('<tr style="border-bottom:1px solid #eee;">')
                for p in parts:
                    # Parse images inside tables
                    img_match = re.search(r'!\[(.*?)\]\((.*?)\)', p)
                    if img_match:
                        cell_content = f'<div style="text-align:center;"><img src="{img_match.group(2)}" style="max-width:280px; border-radius:6px; box-shadow:0 2px 8px rgba(0,0,0,0.12);"><br><small style="color:#555;">{img_match.group(1)}</small></div>'
                    else:
                        cell_content = p
                    html_lines.append(f'<td style="border:1px solid #ddd; padding:10px;">{cell_content}</td>')
                html_lines.append('</tr>')
            continue
        elif in_table:
            in_table = False
            html_lines.append('</tbody></table>')

        # Headings
        if stripped.startswith("# "):
            html_lines.append(f'<h1 style="color:#1b4332; border-bottom:3px solid #2d6a4f; padding-bottom:8px; margin-top:32px;">{stripped[2:]}</h1>')
        elif stripped.startswith("## "):
            html_lines.append(f'<h2 style="color:#2d6a4f; border-bottom:1px solid #d8f3dc; padding-bottom:6px; margin-top:28px;">{stripped[3:]}</h2>')
        elif stripped.startswith("### "):
            html_lines.append(f'<h3 style="color:#40916c; margin-top:20px;">{stripped[4:]}</h3>')
        elif stripped.startswith("#### "):
            html_lines.append(f'<h4 style="color:#52b788; margin-top:16px;">{stripped[5:]}</h4>')
        # Blockquotes
        elif stripped.startswith("> "):
            html_lines.append(f'<blockquote style="border-left:4px solid #2d6a4f; background:#f4f9f5; margin:16px 0; padding:12px 16px; border-radius:0 8px 8px 0; color:#2d6a4f;">{stripped[2:]}</blockquote>')
        # Separator
        elif stripped == "---":
            html_lines.append('<hr style="border:none; border-top:1px solid #e0e0e0; margin:32px 0;">')
        # Images outside tables
        elif re.match(r'^!\[(.*?)\]\((.*?)\)$', stripped):
            m = re.match(r'^!\[(.*?)\]\((.*?)\)$', stripped)
            html_lines.append(f'<div style="text-align:center; margin:24px 0;"><img src="{m.group(2)}" style="max-width:92%; border-radius:8px; box-shadow:0 4px 16px rgba(0,0,0,0.15);"><br><span style="color:#666; font-size:13px; font-style:italic;">{m.group(1)}</span></div>')
        # Bullet lists
        elif stripped.startswith("- "):
            html_lines.append(f'<li style="margin-bottom:6px;">{stripped[2:]}</li>')
        elif re.match(r'^\d+\.\s', stripped):
            clean_item = re.sub(r'^\d+\.\s', '', stripped)
            html_lines.append(f'<li style="margin-bottom:6px;">{clean_item}</li>')
        # Paragraphs
        elif stripped:
            formatted = re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', stripped)
            formatted = re.sub(r'\*(.*?)\*', r'<em>\1</em>', formatted)
            formatted = re.sub(r'`(.*?)`', r'<code style="background:#eef5f0; padding:2px 6px; border-radius:4px; font-family:monospace; color:#1b4332;">\1</code>', formatted)
            html_lines.append(f'<p style="line-height:1.65; color:#222; margin:10px 0;">{formatted}</p>')
        else:
            html_lines.append('<br>')

    if in_table:
        html_lines.append('</tbody></table>')

    body_content = "\n".join(html_lines)
    doc = f"""<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Manual Técnico y de Usuario - GulupaScan</title>
<style>
@page {{
    size: letter portrait;
    margin: 20mm;
}}
body {{
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: #1a1a1a;
    background: #ffffff;
    max-width: 960px;
    margin: 0 auto;
    padding: 30px;
}}
img {{
    max-width: 100%;
    height: auto;
}}
table {{
    page-break-inside: avoid;
}}
h1, h2, h3 {{
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
{body_content}
</body>
</html>"""

    HTML_FILE.write_text(doc, encoding="utf-8")
    print(f"OK: Generado {HTML_FILE}")

if __name__ == "__main__":
    md_to_html()
