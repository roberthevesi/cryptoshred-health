"""
Master Assembler for CryptoShred Health Visual Comparison HTML Application.
"""

import os
import sys
import base64
import shutil

from .data import (
    WORKSPACE_DIR, THESIS_DIR, OUT_PRIMARY, OUT_ARTIFACT,
    IMAGE_PATHS, AUDIT_ITEMS
)
from .css import CSS_STYLES
from .components import (
    render_topbar, render_sidebar, render_audit_table, render_fig33_visualizer
)
from .chapters import (
    render_title_page, render_abstract_ro, render_abstract_en,
    render_front_matter, render_chapter_1, render_chapter_2,
    render_chapter_3, render_chapter_4, render_chapter_5,
    render_chapter_6, render_bibliography
)
from .js import JS_SCRIPTS

def load_b64_images():
    print("Loading and encoding images to Base64...")
    encoded = {}
    for name, path in IMAGE_PATHS.items():
        if os.path.exists(path):
            with open(path, "rb") as f:
                b64_data = base64.b64encode(f.read()).decode("ascii")
                mime = "image/png"
                if path.endswith(".jpg") or path.endswith(".jpeg"):
                    mime = "image/jpeg"
                encoded[name] = f"data:{mime};base64,{b64_data}"
                print(f"  [OK] Encoded {name} ({os.path.getsize(path):,} bytes)")
        else:
            print(f"  [WARN] Missing image: {path}, using placeholder")
            svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="600" height="350" viewBox="0 0 600 350"><rect width="100%" height="100%" fill="#f1f5f9"/><text x="50%" y="50%" fill="#64748b" font-family="sans-serif" font-size="16" text-anchor="middle">Image: {name}</text></svg>'
            encoded[name] = "data:image/svg+xml;base64," + base64.b64encode(svg.encode("utf-8")).decode("ascii")
    return encoded

def build_full_application():
    print("=" * 70)
    print("Building CryptoShred Health Standalone Visual Comparison HTML Application")
    print("=" * 70)
    
    images = load_b64_images()
    
    print("Rendering components...")
    topbar_html = render_topbar()
    sidebar_html = render_sidebar()
    audit_table_html = render_audit_table()
    fig33_visualizer_html = render_fig33_visualizer(images)
    
    print("Rendering thesis chapters & diffs...")
    title_html = render_title_page(images)
    abstract_ro_html = render_abstract_ro()
    abstract_en_html = render_abstract_en()
    front_matter_html = render_front_matter()
    chap1_html = render_chapter_1(images)
    chap2_html = render_chapter_2(images)
    chap3_html = render_chapter_3(images)
    chap4_html = render_chapter_4(images)
    chap5_html = render_chapter_5(images)
    chap6_html = render_chapter_6(images)
    bib_html = render_bibliography()
    
    html_document = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CryptoShred Health &mdash; Full Compiled Dissertation (Visual Comparison &amp; Audit)</title>
    
    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&family=Merriweather:ital,wght@0,300;0,400;0,700;0,900;1,300;1,400;1,700&display=swap" rel="stylesheet">
    
    <!-- KaTeX for formula rendering -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js" onload="if(window.renderMathInElement)renderMathInElement(document.body);"></script>
    
    <style>
        {CSS_STYLES}
    </style>
</head>
<body class="mode-unified">

    {topbar_html}
    
    <div id="app-layout">
        {sidebar_html}
        
        <main id="app-content">
            {audit_table_html}
            {fig33_visualizer_html}
            {title_html}
            {abstract_ro_html}
            {abstract_en_html}
            {front_matter_html}
            {chap1_html}
            {chap2_html}
            {chap3_html}
            {chap4_html}
            {chap5_html}
            {chap6_html}
            {bib_html}
        </main>
    </div>
    
    <script>
        {JS_SCRIPTS}
    </script>
</body>
</html>
"""
    
    print(f"Writing primary output: {OUT_PRIMARY}...")
    with open(OUT_PRIMARY, "w", encoding="utf-8") as f:
        f.write(html_document)
    sz_primary = os.path.getsize(OUT_PRIMARY)
    print(f"  [OK] Primary file written successfully: {sz_primary:,} bytes ({sz_primary / (1024*1024):.2f} MB)")
    
    print(f"Writing artifact output: {OUT_ARTIFACT}...")
    os.makedirs(os.path.dirname(OUT_ARTIFACT), exist_ok=True)
    with open(OUT_ARTIFACT, "w", encoding="utf-8") as f:
        f.write(html_document)
    sz_artifact = os.path.getsize(OUT_ARTIFACT)
    print(f"  [OK] Artifact file written successfully: {sz_artifact:,} bytes ({sz_artifact / (1024*1024):.2f} MB)")
    
    print("=" * 70)
    print("VERIFICATION COMPLETE: Application generated successfully in both destinations.")
    print("=" * 70)

if __name__ == "__main__":
    build_full_application()
