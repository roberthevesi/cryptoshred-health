"""
Comprehensive Chapters rendering and diff injection module for CryptoShred Health Visual Comparison App.
Injects all before-after visual diffs, additions, deletions, calibrations, code snippets, and publication figures.
"""

import os
import re
import html
from .data import THESIS_DIR, CHAPTERS_DIR, ABBREVIATIONS, ACTIVE_CITATIONS, REMOVED_CITATIONS, KEY_TO_NUM

def convert_latex_inline(text):
    text = text.replace("---", "&mdash;").replace("--", "&ndash;")
    text = text.replace("~", "&nbsp;")
    text = text.replace(r"\%", "%").replace(r"\&", "&amp;").replace(r"\_", "_").replace(r"\#", "#")
    text = re.sub(r'\\textbf\{([^}]+)\}', r'<strong>\1</strong>', text)
    text = re.sub(r'\\textit\{([^}]+)\}', r'<em>\1</em>', text)
    text = re.sub(r'\\emph\{([^}]+)\}', r'<em>\1</em>', text)
    text = re.sub(r'\\texttt\{([^}]+)\}', r'<code>\1</code>', text)
    
    def replace_cite(m):
        keys = [k.strip() for k in m.group(1).split(",")]
        links = []
        for k in keys:
            if k in KEY_TO_NUM:
                num = KEY_TO_NUM[k]
                links.append(f'<a href="#bib-item-{num}" class="cite-ref" title="Citation [{num}]: {k}">[{num}]</a>')
            else:
                links.append(f'<span class="cite-ref cite-removed" title="Citation key reverted: {k}">[{k}]</span>')
        return "".join(links)
        
    text = re.sub(r'\\cite\{([^}]+)\}', replace_cite, text)
    text = re.sub(r'\\ref\{([^}]+)\}', r'<a href="#\1" class="sec-ref">\1</a>', text)
    text = re.sub(r'\\eqref\{([^}]+)\}', r'(<a href="#\1" class="eq-ref">\1</a>)', text)
    return text

def parse_latex_table(tex_table):
    caption_m = re.search(r'\\caption\{([^}]+)\}', tex_table)
    caption = caption_m.group(1) if caption_m else ""
    tabular_m = re.search(r'\\begin\{tabular\}\{[^}]+\}(.*?)\\end\{tabular\}', tex_table, re.DOTALL)
    if not tabular_m:
        return f'<div class="table-fallback">{html.escape(tex_table)}</div>'
    
    body = tabular_m.group(1)
    lines = [l.strip() for l in body.split(r'\\') if l.strip()]
    
    html_rows = []
    is_header = True
    for line in lines:
        if any(line.startswith(r'\\' + r) for r in ['toprule', 'midrule', 'bottomrule']):
            line = re.sub(r'\\(top|mid|bottom)rule', '', line).strip()
            if not line: continue
        if line.startswith(r'\hline'):
            line = line.replace(r'\hline', '').strip()
            if not line: continue
        
        cells = [c.strip() for c in line.split('&')]
        tag = "th" if is_header else "td"
        formatted_cells = [f'<{tag}>{convert_latex_inline(c)}</{tag}>' for c in cells]
        html_rows.append(f'<tr>{"".join(formatted_cells)}</tr>')
        if is_header:
            is_header = False
            
    return f"""
    <div class="academic-table-container">
        <table class="academic-table">
            <thead>{html_rows[0] if html_rows else ""}</thead>
            <tbody>{"".join(html_rows[1:]) if len(html_rows) > 1 else ""}</tbody>
        </table>
        {f'<div class="table-caption"><strong>Table:</strong> {convert_latex_inline(caption)}</div>' if caption else ""}
    </div>
    """

def parse_latex_listing(tex_listing):
    caption_m = re.search(r'caption=\{([^}]+)\}', tex_listing)
    caption = caption_m.group(1) if caption_m else ""
    code_m = re.search(r'\\begin\{lstlisting\}(?:\[.*?\])?\n?(.*?)\\end\{lstlisting\}', tex_listing, re.DOTALL)
    code = code_m.group(1) if code_m else ""
    lines = code.strip().split("\n")
    numbered_lines = []
    for i, line in enumerate(lines, 1):
        escaped = html.escape(line)
        numbered_lines.append(f'<div class="code-line"><span class="line-num">{i:2d}</span><span class="line-code">{escaped}</span></div>')
    return f"""
    <div class="academic-code-container">
        <div class="code-header">
            <div class="code-dots"><span class="dot dot-red"></span><span class="dot dot-yellow"></span><span class="dot dot-green"></span></div>
            <div class="code-caption">{convert_latex_inline(caption) if caption else "Code Snippet"}</div>
            <div class="code-lang">Java / JPA</div>
        </div>
        <pre class="code-body"><code>{"".join(numbered_lines)}</code></pre>
    </div>
    """

def render_chapter_content(tex_content, images, chapter_num=1):
    output = []
    clean_lines = []
    for line in tex_content.splitlines():
        if line.strip().startswith("%"): continue
        clean_lines.append(line)
    text = "\n".join(clean_lines)
    text = re.sub(r'(\\end\{(?:table|figure|lstlisting|equation|enumerate|itemize)\})', r'\1\n\n', text)
    text = re.sub(r'(\\begin\{(?:table|figure|lstlisting|equation|enumerate|itemize)\})', r'\n\n\1', text)
    paragraphs = text.split("\n\n")
    
    for p in paragraphs:
        p = p.strip()
        if not p: continue
        
        m_chap = re.match(r'\\chapter\*?\{([^}]+)\}', p)
        if m_chap:
            chap_title = m_chap.group(1)
            output.append(f'<h1 class="chap-title" id="chapter-{chapter_num}"><span class="chap-num">Chapter {chapter_num}</span> {convert_latex_inline(chap_title)}</h1>')
            continue
            
        m_sec = re.match(r'\\section\*?\{([^}]+)\}', p)
        if m_sec:
            sec_title = m_sec.group(1)
            sec_slug = re.sub(r'[^a-zA-Z0-9]+', '-', sec_title).strip('-').lower()
            output.append(f'<h2 class="sec-title" id="sec-{sec_slug}">{convert_latex_inline(sec_title)}</h2>')
            continue
            
        m_subsec = re.match(r'\\subsection\*?\{([^}]+)\}', p)
        if m_subsec:
            subsec_title = m_subsec.group(1)
            subsec_slug = re.sub(r'[^a-zA-Z0-9]+', '-', subsec_title).strip('-').lower()
            output.append(f'<h3 class="subsec-title" id="subsec-{subsec_slug}">{convert_latex_inline(subsec_title)}</h3>')
            continue
            
        m_subsubsec = re.match(r'\\subsubsection\*?\{([^}]+)\}', p)
        if m_subsubsec:
            subsubsec_title = m_subsubsec.group(1)
            output.append(f'<h4 class="subsubsec-title">{convert_latex_inline(subsubsec_title)}</h4>')
            continue
            
        if p.startswith(r'\paragraph{'):
            p = re.sub(r'\\paragraph\{([^}]+)\}', r'<h5 class="para-header"><strong>\1</strong></h5>', p)
            
        if r'\begin{table}' in p:
            output.append(parse_latex_table(p))
            continue
            
        if r'\begin{lstlisting}' in p:
            output.append(parse_latex_listing(p))
            continue
            
        if r'\begin{figure}' in p:
            caption_m = re.search(r'\\caption\{([^}]+)\}', p)
            caption = caption_m.group(1) if caption_m else ""
            label_m = re.search(r'\\label\{([^}]+)\}', p)
            label = label_m.group(1) if label_m else ""
            
            img_tag = ""
            if "fig_system_architecture" in p:
                img_tag = f'<img src="{images["fig_sys_arch"]}" alt="System Architecture" class="academic-fig-img">'
            elif "fig_erasure_sequence" in p:
                img_tag = f'<div class="figure-3-3-inline-container diff-item" id="diff-change-item-5" data-item="5" data-type="polished"><img src="{images["fig33_after"]}" alt="Figure 3.3 Erasure Pipeline" class="academic-fig-img fig33-inline-img"><div class="fig-badge-banner"><span class="diff-badge badge-polished">POLISHED: Figure 3.3 (Item 5)</span><span>Tall portrait format (7.6 &times; 11.2 in), uncrowded lifelines, zero text overlapping</span> <a href="#fig33-inspector" class="btn-inspect-link">Open Full Figure 3.3 Interactive Visualizer &rarr;</a></div></div>'
            elif "fig_vault_raft" in p:
                img_tag = f'<img src="{images["fig_vault_raft"]}" alt="Vault Raft Failover" class="academic-fig-img">'
            elif "ui_merkle_verifier" in p:
                img_tag = f'<img src="{images["ui_merkle_verifier"]}" alt="Merkle Verifier UI" class="academic-fig-img">'
            elif "ui_patient_census" in p:
                img_tag = f'<img src="{images["ui_patient_census"]}" alt="Patient Census UI" class="academic-fig-img">'
            elif "ui_patient_chart" in p:
                img_tag = f'<img src="{images["ui_patient_chart"]}" alt="Patient Chart UI" class="academic-fig-img">'
            elif "ui_grafana_dashboard" in p:
                img_tag = f'<img src="{images["ui_grafana_dashboard"]}" alt="Grafana Dashboard" class="academic-fig-img">'
            elif "fig_deletion_scaling" in p:
                img_tag = f'<img src="{images["fig_deletion_scaling"]}" alt="Deletion Scaling Plot" class="academic-fig-img">'
            elif "fig_merkle_scaling" in p:
                img_tag = f'<img src="{images["fig_merkle_scaling"]}" alt="Merkle Scaling Plot" class="academic-fig-img">'
            elif "fig_macro_concurrency_throughput" in p:
                img_tag = f'<div class="fig-wrap diff-item" id="diff-change-item-7" data-item="7" data-type="addition"><img src="{images["fig_macro_throughput"]}" alt="Macro Concurrency Throughput" class="academic-fig-img"><div class="fig-badge-banner" style="margin-top:0.4rem;"><span class="diff-badge badge-kept">CALIBRATED (Item 7)</span><span style="font-size:0.8rem;color:#475569;">Legend entries rendered in strict numerical order (Scenario 1, 2, 3, 4)</span></div></div>'
            elif "fig_macro_latency_percentiles" in p:
                img_tag = f'<img src="{images["fig_macro_percentiles"]}" alt="Macro Latency Percentiles" class="academic-fig-img">'
            else:
                img_tag = f'<div class="fig-placeholder">[Figure {label}]</div>'
                
            output.append(f"""
            <figure class="academic-figure" id="{label}">
                <div class="figure-frame">{img_tag}</div>
                <figcaption class="academic-caption">
                    <span class="caption-label">Figure {chapter_num}.{label[-1] if label and label[-1].isdigit() else "X"}:</span>
                    {convert_latex_inline(caption)}
                </figcaption>
            </figure>
            """)
            continue
            
        if r'\begin{equation}' in p:
            eq_m = re.search(r'\\begin\{equation\}(.*?)\\end\{equation\}', p, re.DOTALL)
            if eq_m:
                eq_body = eq_m.group(1).strip()
                output.append(f'<div class="katex-display">\\[ {eq_body} \\]</div>')
                continue
                
        if r'\begin{enumerate}' in p or r'\begin{itemize}' in p:
            is_enum = r'\begin{enumerate}' in p
            items = re.findall(r'\\item\s+(.*?)(?=(?:\\item|\\end\{itemize\}|\\end\{enumerate\}|$))', p, re.DOTALL)
            list_tag = "ol" if is_enum else "ul"
            list_items = [f'<li>{convert_latex_inline(it.strip())}</li>' for it in items if it.strip()]
            output.append(f'<{list_tag} class="academic-list">{"".join(list_items)}</{list_tag}>')
            continue
            
        output.append(f'<p class="academic-p">{convert_latex_inline(p)}</p>')
        
    return "\n".join(output)

def render_title_page(images):
    return f"""
    <div class="paper-sheet" id="chapter-title">
        <div class="title-page-container">
            <div class="title-header-top">
                <div class="upt-title-block">
                    <h2>UNIVERSITATEA POLITEHNICA TIMIȘOARA</h2>
                    <p>FACULTATEA DE AUTOMATICĂ ȘI CALCULATOARE</p>
                    <p>DEPARTAMENTUL CALCULATOARE ȘI TEHNOLOGIA INFORMAȚIEI</p>
                    <p style="margin-top: 0.4rem; font-weight: 600; color: #1e293b;">PROGRAMUL DE STUDII: SOFTWARE ENGINEERING</p>
                </div>
                <div>
                    <img src="{images['logo']}" alt="UPT Logo" class="title-logo-img">
                </div>
            </div>
            
            <div class="title-main-block">
                <div class="dissertation-type-tag">LUCRARE DE DISERTAȚIE / MASTER'S DISSERTATION</div>
                <h1 class="thesis-main-title">
                    Beyond Physical Deletion: Enforcing Verifiable Cryptographic Erasure Across Distributed Storage Layers in Zero-Plaintext Electronic Health Record Architectures
                </h1>
            </div>
            
            <div class="title-meta-block">
                <div class="meta-col">
                    <h4>Absolvent (Candidate):</h4>
                    <p>Ing. Robert HEVESI</p>
                </div>
                <div class="meta-col" style="text-align: right;">
                    <h4>Conducător științific (Coordinator):</h4>
                    <p>Conf. dr. ing. Alexandru IOVANOVICI</p>
                </div>
            </div>
            
            <div class="title-session-footer">
                Timișoara &mdash; Sesiunea: Septembrie 2026
            </div>
        </div>
    </div>
    """

def render_abstract_ro():
    p1 = "Platformele moderne de dosare electronice de sănătate (Electronic Health Record &mdash; EHR) replică date clinice sensibile în baze de date distribuite, fluxuri de mesaje, cache-uri volatile și arhive de backup imutabile de tip Write-Once-Read-Many (WORM). Această persistență pe multiple niveluri generează o tensiune arhitecturală fundamentală între dreptul la ștergere garantat de Articolul 17 din Regulamentul General privind Protecția Datelor (GDPR) și obligațiile legale de retenție medicală (precum NHS Code of Practice, HIPAA sau Legea nr. 95/2006). Deoarece modificarea blocurilor fizice în medii de stocare imutabile și jurnale de tip append-only este imposibilă din punct de vedere tehnic, operațiunile SQL clasice de ștergere omit backup-urile și jurnalele de tranzacții, lăsând date clinice reziduale expuse."
    p2 = "Această teză prezintă <em>CryptoShred Health</em>, o arhitectură EHR enterprise zero-plaintext care decuplează persistența fizică a datelor de accesibilitatea lor criptografică. Sistemul implementează o ierarhie de criptare de tip envelope: chei efemere de criptare a datelor (Data Encryption Keys &mdash; DEK) bazate pe AES-256-GCM sunt asociate contextual înregistrărilor prin Authenticated Associated Data (AAD) și încapsulate sub chei dedicate de criptare a cheilor (Key Encryption Keys &mdash; KEK), gestionate într-un cluster HashiCorp Vault Raft cu 3 noduri. Distrugerea unei KEK determină irecuperabilitatea computațională a tuturor textelor cifrate asociate, reducând efortul de recuperare la <ins class='diff-ins diff-item' id='diff-change-workfactor-ro' data-item='19' data-type='addition'><span class='diff-badge badge-kept'>CALIBRAT</span>un factor mediu de lucru de $2^{255}$ operații simetrice</ins> pe un spațiu de chei de $2^{256}$ biți."
    p3 = "Arhitectura introduce trei contribuții majore: (1) un mecanism de indexare oarbă deterministică bazat pe HMAC-SHA256 pentru căutări exacte în timp $\\mathcal{O}(1)$ fără decriptarea identificatorilor pe serverul de baze de date; (2) un pipeline de atestare a ștergerii cu dublă semnare (RSA-2048 și post-cuantică ML-DSA-65) organizat într-un arbore Merkle binar, permițând verificarea probelor de incluziune în $2.48\\text{~ms}$ pentru $100{,}000$ de frunze; și (3) un serviciu de reconciliere a tombstone-urilor care scanează chitanțele imutabile WORM la pornire, eliminând Paradoxul Restaurării Snapshot-urilor prin purjarea automată a cheilor restaurate accidental ($1.69\\text{~ms}$ per cheie)."
    p4 = "Evaluarea experimentală demonstrează că ștergerea criptografică end-to-end se finalizează în <mark class='diff-calib diff-item' id='diff-change-speedup-ro' data-item='10' data-type='calibration'><span class='diff-badge badge-calib'>CALIBRAT</span>$5.63\\text{--}7.65\\text{~ms}$, realizând o accelerare de $4\\text{--}5\\times$</mark> față de cascadele tranzacționale PostgreSQL ($25.19\\text{--}39.12\\text{~ms}$), în timp ce microbenchmark-urile computaționale izolate confirmă invarianța algoritmică în timp constant ($59.50\\,\\mu\\text{s}$). Sub teste de sarcină k6 cu 500 de utilizatori concurenți, platforma susține $96{,}820.5\\text{~cereri/s}$ pe citiri din cache și $13{,}420.7\\text{~ștergeri/s}$. Validarea adversarială pe $2{,}377{,}883$ de cereri care vizează entități șterse confirmă o rată strictă de scurgere a datelor în clar de $0.000\\%$."
    
    return f"""
    <div class="paper-sheet" id="sec-rezumat">
        <h2 class="sec-title" style="text-align: center; border: none; font-size: 1.6rem;">REZUMAT</h2>
        <h3 class="subsec-title" style="text-align: center; font-family: var(--font-serif); font-size: 1.1rem; color: #475569; margin-bottom: 2rem;">
            Dincolo de Ștergerea Fizică: Asigurarea Ștergerii Criptografice Verificabile pe Nivelurile de Stocare Distribuite în Arhitecturi Zero-Plaintext pentru Dosare Electronice de Sănătate
        </h3>
        
        <p class="academic-p">{p1}</p>
        <p class="academic-p">{p2}</p>
        <p class="academic-p">{p3}</p>
        <p class="academic-p">{p4}</p>
        
        <div class="diff-del diff-item" id="diff-change-item-10" data-item="10" data-type="revert" style="margin-top: 2rem;">
            <span class="diff-badge badge-revert">REVERTED: Cuvinte-cheie Eliminat (Item 10)</span>
            <p class="academic-p" style="margin-bottom: 0;">
                <strong>Cuvinte-cheie:</strong> ștergere criptografică, dosare electronice de sănătate, GDPR Articolul 17, arbore Merkle binar, criptografie post-cuantică, ML-DSA-65, reconciliere tombstone.
            </p>
        </div>
    </div>
    """

def render_abstract_en():
    p1 = "Modern Electronic Health Record (EHR) platforms replicate sensitive clinical data across distributed databases, message streams, in-memory caches, and immutable Write-Once-Read-Many (WORM) backup archives. This multi-tier persistence creates a fundamental tension between enforceable data subject privacy under Article 17 of the General Data Protection Regulation (GDPR) and mandatory statutory health preservation obligations (such as the UK NHS Code of Practice, HIPAA, or national health laws). Because modifying physical storage blocks across immutable media and append-only journals is technically impossible, conventional SQL relational deletions update pointers while leaving residual clinical records exposed across logs and disaster recovery snapshots."
    p2 = "This dissertation presents <em>CryptoShred Health</em>, an enterprise zero-plaintext EHR platform that decouples physical persistence from cryptographic accessibility. The architecture implements a two-tier envelope encryption hierarchy: ephemeral AES-256-GCM Data Encryption Keys (DEKs) bind to clinical record identifiers via Authenticated Associated Data (AAD) and are wrapped under dedicated per-patient and per-encounter Key Encryption Keys (KEKs) maintained within a high-availability 3-node HashiCorp Vault Raft cluster. Destroying a KEK renders all associated ciphertexts computationally irrecoverable, reducing any recovery attempt to an <ins class='diff-ins diff-item' id='diff-change-workfactor-en' data-item='19' data-type='addition'><span class='diff-badge badge-kept'>CALIBRATED</span>average work factor of $2^{255}$ symmetric evaluations</ins> over a $2^{256}$-bit key space."
    p3 = "The system delivers three core technical contributions: (1) deterministic HMAC-SHA256 blind indexing that enables constant-time ($\\mathcal{O}(1)$) exact-match queries without decrypting identifiers on the database tier; (2) an erasure verification pipeline combining classical RSA-2048 and post-quantum ML-DSA-65 signatures within an append-only binary Merkle tree, enabling third-party inclusion proof verification in $2.48\\text{~ms}$ across $100{,}000$ historical leaves; and (3) an automated tombstone reconciler that harvests immutable WORM deletion receipts at startup, eliminating the KMS Snapshot Restoration Paradox by purging resurrected encryption keys in $1.69\\text{~ms}$ per key."
    p4 = "Empirical evaluation demonstrates that end-to-end cryptographic erasure executes in <mark class='diff-calib diff-item' id='diff-change-speedup-en' data-item='11' data-type='calibration'><span class='diff-badge badge-calib'>CALIBRATED</span>$5.63\\text{--}7.65\\text{~ms}$, delivering a $4\\text{--}5\\times$ speedup</mark> over transactional PostgreSQL cascades ($25.19\\text{--}39.12\\text{~ms}$), while isolated CPU microbenchmarks confirm constant-time algorithmic invariance ($59.50\\,\\mu\\text{s}$). Under distributed k6 load testing with 500 concurrent virtual users, the platform sustains $96{,}820.5\\text{~requests/s}$ on cached clinical reads and $13{,}420.7\\text{~erasures/s}$. Adversarial validation across $2{,}377{,}883$ queries targeting shredded entities confirms a strict $0.000\\%$ plaintext leakage rate."
    
    return f"""
    <div class="paper-sheet" id="sec-abstract">
        <h2 class="sec-title" style="text-align: center; border: none; font-size: 1.6rem;">ABSTRACT</h2>
        <h3 class="subsec-title" style="text-align: center; font-family: var(--font-serif); font-size: 1.1rem; color: #475569; margin-bottom: 2rem;">
            Beyond Physical Deletion: Enforcing Verifiable Cryptographic Erasure Across Distributed Storage Layers in Zero-Plaintext Electronic Health Record Architectures
        </h3>
        
        <p class="academic-p">{p1}</p>
        <p class="academic-p">{p2}</p>
        <p class="academic-p">{p3}</p>
        <p class="academic-p">{p4}</p>
        
        <div class="diff-del diff-item" id="diff-change-item-11" data-item="11" data-type="revert" style="margin-top: 2rem;">
            <span class="diff-badge badge-revert">REVERTED: Keywords Removed (Item 11)</span>
            <p class="academic-p" style="margin-bottom: 0;">
                <strong>Keywords:</strong> cryptographic erasure, electronic health records, GDPR Article 17, binary Merkle tree, post-quantum cryptography, ML-DSA-65, tombstone reconciliation.
            </p>
        </div>
    </div>
    """

def render_front_matter():
    abbr_rows = []
    for abbr, desc in ABBREVIATIONS:
        abbr_rows.append(f"<tr><td><strong>{abbr}</strong></td><td>{desc}</td></tr>")
        
    return f"""
    <div class="paper-sheet" id="front-matter-section">
        <h2 class="sec-title">Front Matter</h2>
        
        <div style="margin: 1.5rem 0; display: flex; flex-direction: column; gap: 0.6rem;">
            <div style="display: flex; justify-content: space-between; border-bottom: 1px dotted #cbd5e1; padding-bottom: 0.25rem;">
                <strong>Table of Contents</strong> <span>p. i</span>
            </div>
            <div style="display: flex; justify-content: space-between; border-bottom: 1px dotted #cbd5e1; padding-bottom: 0.25rem;">
                <strong>List of Figures</strong> <span>p. iv</span>
            </div>
            <div style="display: flex; justify-content: space-between; border-bottom: 1px dotted #cbd5e1; padding-bottom: 0.25rem;">
                <strong>List of Tables</strong> <span>p. v</span>
            </div>
            <div style="display: flex; justify-content: space-between; border-bottom: 1px dotted #cbd5e1; padding-bottom: 0.25rem; align-items: center;">
                <div>
                    <del class="diff-del diff-item" id="diff-change-item-2" data-item="2" data-type="revert">
                        <span class="diff-badge badge-revert">REVERTED (Item 2)</span>List of Listings
                    </del>
                    <ins class="diff-ins diff-item" id="diff-change-item-2-ins" data-item="2" data-type="addition">
                        <span class="diff-badge badge-kept">RESTORED (Item 2)</span>List of Snippets
                    </ins>
                </div>
                <span>p. vi</span>
            </div>
        </div>
        
        <!-- Reverted Abbreviations Table -->
        <div class="diff-del diff-item abbreviations-diff-card" id="diff-change-item-9" data-item="9" data-type="revert">
            <span class="diff-badge badge-revert">REVERTED: List of Abbreviations Completely Removed (Item 9)</span>
            <div class="diff-header-note">
                <strong>User Directive:</strong> <em>"9 - please remove this"</em>. The 55-entry formal List of Abbreviations and its LaTeX inclusion (<code>\\include{{chapters/abbreviations}}</code>) was removed from the front matter. All acronyms remain defined upon first occurrence in the chapters.
            </div>
            <div class="table-scroll">
                <table class="academic-table abbr-table">
                    <thead>
                        <tr>
                            <th style="width: 140px;">Abbreviation</th>
                            <th>Definition</th>
                        </tr>
                    </thead>
                    <tbody>
                        {"".join(abbr_rows)}
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    """

def render_chapter_1(images):
    path = os.path.join(CHAPTERS_DIR, "4.Introduction.tex")
    with open(path) as f:
        content = f.read()
        
    html_content = render_chapter_content(content, images, chapter_num=1)
    
    # 1. NIST SP 800-88 in intro paragraph
    target_nist = "This stalls active hospital writes while still leaving cold backup archives completely untouched."
    rep_nist = "This stalls active hospital writes while still leaving cold backup archives completely untouched <del class='diff-del diff-item' id='diff-change-item-15' data-item='15' data-type='revert'><span class='diff-badge badge-revert'>REVERTED: Added NIST Citation (Item 15)</span>, failing the sanitization mandates of NIST SP~800-88 Rev.~1~\cite{nist_sp_800_88_r1}</del>."
    html_content = html_content.replace(convert_latex_inline(target_nist), convert_latex_inline(rep_nist))
    
    # 2. Flaws 1, 2, 3 CWE/NIST references
    target_f1 = "Hardcoded and Exposed Keys:</strong> Hardcoding static keys in configuration files or generating them directly inside application memory leaves raw key material vulnerable to memory dumps and process inspection."
    rep_f1 = "Hardcoded and Exposed Keys <del class='diff-del diff-item' id='diff-change-item-15-f1' data-item='15' data-type='revert'><span class='diff-badge badge-revert'>REVERTED (Item 15)</span>(CWE-798)</del>:</strong> Hardcoding static keys in configuration files or generating them directly inside application memory <del class='diff-del diff-item' id='diff-change-item-15-f1-b' data-item='15' data-type='revert'>violates CWE-798</del> leaves raw key material vulnerable to memory dumps and process inspection."
    html_content = html_content.replace(target_f1, rep_f1)
    
    target_f2 = "violates GDPR compliance without triggering alerts."
    rep_f2 = "violates GDPR compliance without triggering alerts <del class='diff-del diff-item' id='diff-change-item-15-f2' data-item='15' data-type='revert'><span class='diff-badge badge-revert'>REVERTED (Item 15)</span>, violating media sanitization principles (NIST SP 800-88)</del>."
    html_content = html_content.replace(target_f2, rep_f2)
    
    target_f3 = "Keys Lingering in JVM Memory:</strong> In managed runtimes like Java, standard byte arrays holding sensitive keys remain in heap memory until collected, risking exposure if the operating system pages idle memory to swap space."
    rep_f3 = "Keys Lingering in JVM Memory <del class='diff-del diff-item' id='diff-change-item-15-f3' data-item='15' data-type='revert'><span class='diff-badge badge-revert'>REVERTED (Item 15)</span>(CWE-312)</del>:</strong> In managed runtimes like Java, standard byte arrays holding sensitive keys remain in heap memory until collected, risking exposure if the operating system pages idle memory to swap space <del class='diff-del diff-item' id='diff-change-item-15-f3-b' data-item='15' data-type='revert'>(CWE-312)</del>."
    html_content = html_content.replace(target_f3, rep_f3)
    
    # 3. Hypothesis H1 Speedup (Item 12)
    target_h1 = "achieving a $4\\text{&ndash;}5\\times$ latency speedup over relational foreign-key cascade deletions ($25\\text{&ndash;}39\\text{&nbsp;ms}$)"
    rep_h1 = "<mark class='diff-calib diff-item' id='diff-change-item-12' data-item='12' data-type='calibration'><span class='diff-badge badge-calib'>CALIBRATED (Item 12)</span>achieving a $4\\text{&ndash;}5\\times$ latency speedup over relational foreign-key cascade deletions ($25\\text{&ndash;}39\\text{&nbsp;ms}$)</mark>"
    html_content = html_content.replace(target_h1, rep_h1)
    
    # 4. Hypothesis H3 Linear Scaling (Item 13)
    target_h3 = "achieves deterministic linear scaling ($\\mathcal{O}(k)$) with sub-$5\\text{&nbsp;ms}$ per-key execution latency ($<50\\text{&nbsp;ms}$ for typical operational cohorts $k \\le 30$)"
    rep_h3 = "<mark class='diff-calib diff-item' id='diff-change-item-13' data-item='13' data-type='calibration'><span class='diff-badge badge-calib'>CALIBRATED (Item 13)</span>achieves deterministic linear scaling ($\\mathcal{O}(k)$) with sub-$5\\text{&nbsp;ms}$ per-key execution latency ($<50\\text{&nbsp;ms}$ for typical operational cohorts $k \\le 30$)</mark>"
    html_content = html_content.replace(target_h3, rep_h3)
    
    # 5. Inlined O(N) (Item 14)
    target_o = "induces linear execution overhead $\\mathcal{O}(N)$"
    rep_o = "<mark class='diff-calib diff-item' id='diff-change-item-14' data-item='14' data-type='calibration'><span class='diff-badge badge-calib'>CALIBRATED (Item 14)</span>induces linear execution overhead $\\mathcal{O}(N)$</mark>"
    html_content = html_content.replace(convert_latex_inline(target_o), convert_latex_inline(rep_o))
    
    return f'<div class="paper-sheet" id="chapter-1-section">{html_content}</div>'

def render_chapter_2(images):
    path = os.path.join(CHAPTERS_DIR, "5.TheoreticalFoundations.tex")
    with open(path) as f:
        content = f.read()
    html_content = render_chapter_content(content, images, chapter_num=2)
    
    # 1. Single-Use DEK Cryptographic Rationale (Item 16)
    target_dek = "A key architectural advantage of this design is that each $K_{\\text{DEK}}$ is strictly single-use"
    if target_dek in html_content:
        html_content = html_content.replace(target_dek, f"<ins class='diff-ins diff-item' id='diff-change-item-16' data-item='16' data-type='addition'><span class='diff-badge badge-kept'>ADDED (Item 16)</span>{target_dek}")
        html_content = html_content.replace("irrelevant by construction.", "irrelevant by construction.</ins>")
    
    # 2. Brute-Force Work Factor (Item 19)
    target_wf = "reducing any recovery attempt to an average work factor of $2^{255}$ symmetric evaluations over a $2^{256}$-bit key space"
    rep_wf = "<mark class='diff-calib diff-item' id='diff-change-item-19' data-item='19' data-type='calibration'><span class='diff-badge badge-calib'>CALIBRATED</span>reducing any recovery attempt to an average work factor of $2^{255}$ symmetric evaluations over a $2^{256}$-bit key space</mark>"
    html_content = html_content.replace(convert_latex_inline(target_wf), convert_latex_inline(rep_wf))

    # 3. Reverted IND-CCA2 Definition (Item 17)
    revert_ind_cca2 = """
    <div class="diff-del diff-item" id="diff-change-item-17" data-item="17" data-type="revert">
        <span class="diff-badge badge-revert">REVERTED: IND-CCA2 Proposition &amp; Equation Removed (Item 17)</span>
        <p class="academic-p">
            Formally, AES-GCM is an Authenticated Encryption with Associated Data (AEAD) scheme. Under the standard pseudorandom permutation (PRP) assumption for the underlying AES block cipher, an AEAD scheme providing ciphertext indistinguishability under chosen-plaintext attack ($\text{IND-CPA}$) alongside ciphertext integrity ($\text{INT-CTXT}$) provably achieves indistinguishability under adaptive chosen-ciphertext attack:
            <div class="katex-display">\\[ \\text{IND-CPA} \\land \\text{INT-CTXT} \\implies \\text{IND-CCA2} \\]</div>
            Equation guarantees that an active adversary with oracle access cannot forge valid ciphertexts, manipulate encrypted payloads, or alter associated metadata without triggering immediate verification failure.
        </p>
    </div>
    """
    insert_marker = "Binding the patient or visit UUID inside $\\text{AAD}$ prevents ciphertext splicing attacks."
    if insert_marker in html_content:
        html_content = html_content.replace(insert_marker, revert_ind_cca2 + "\n" + insert_marker)
        
    return f'<div class="paper-sheet" id="chapter-2-section">{html_content}</div>'

def render_chapter_3(images):
    path = os.path.join(CHAPTERS_DIR, "6.SystemDesign.tex")
    with open(path) as f:
        content = f.read()
    html_content = render_chapter_content(content, images, chapter_num=3)
    
    # 1. Demographic vs Encounters O(1) vs O(m) distinction (Item 21)
    target_shred_scale = "To achieve total cryptographic eradication of a patient's entire medical chart containing $m$ historical visits, the system must execute $m$ independent key destruction operations in Vault Transit. This complete chart purge exhibits deterministic linear scaling $\\mathcal{O}(m)$."
    rep_shred_scale = f"<ins class='diff-ins diff-item' id='diff-change-item-21' data-item='21' data-type='addition'><span class='diff-badge badge-kept'>CALIBRATED (Item 21)</span>{target_shred_scale}</ins>"
    html_content = html_content.replace(target_shred_scale, rep_shred_scale)
    
    # 2. Revert Figure 3.4 (ER Model) (Item 3)
    revert_fig34 = """
    <div class="diff-del diff-item figure-revert-card" id="diff-change-item-3" data-item="3" data-type="revert">
        <span class="diff-badge badge-revert">REVERTED: Figure 3.4 &amp; Reference Paragraph Removed (Item 3)</span>
        <p class="academic-p">Figure 3.4 illustrates the entity-relationship schema that maintains this cryptographic boundary across demographic identities, clinical encounters, and diagnostic attachments.</p>
        <div class="figure-deleted-notice">
            <div class="del-icon">&#10060;</div>
            <div class="del-text">
                <strong>Figure 3.4 (Entity-Relationship Data Model) Removed</strong><br>
                Per explicit user directive: <em>"do not create an entity relationship figure"</em>. Figure 3.4 and its referring schema paragraph were removed from Section 3.2.
            </div>
        </div>
    </div>
    """
    marker_fig34 = '<h3 class="subsec-title" id="subsec-cryptographic-context-binding-via-authenticated-associated-data">'
    if marker_fig34 in html_content:
        html_content = html_content.replace(marker_fig34, revert_fig34 + "\n" + marker_fig34)
        
    # 3. Revert Figure 3.5 (Merkle Tree Path) (Item 6)
    revert_fig35 = """
    <div class="diff-del diff-item figure-revert-card" id="diff-change-item-6" data-item="6" data-type="revert">
        <span class="diff-badge badge-revert">REVERTED: Figure 3.5 &amp; Reference Sentence Removed (Item 6)</span>
        <p class="academic-p">Figure 3.5 illustrates the binary Merkle tree topology and directional audit path reconstruction.</p>
        <div class="figure-deleted-notice">
            <div class="del-icon">&#10060;</div>
            <div class="del-text">
                <strong>Figure 3.5 (Binary Merkle Tree Inclusion Proof Diagram) Removed</strong><br>
                Per explicit user directive: <em>"Please revert 6"</em>. Figure 3.5 and its referring sentence were removed from Section 3.3.4.
            </div>
        </div>
    </div>
    """
    marker_fig35 = "Across a clinical registry containing $1{,}000{,}000$ records"
    if marker_fig35 in html_content:
        html_content = html_content.replace(marker_fig35, revert_fig35 + "\n" + marker_fig35)
        
    # 4. Raft Failover Bound Calibration (Item 4)
    target_failover = "$T_{\\text{failover}} < 2.5\\text{&nbsp;s}$"
    rep_failover = f"<ins class='diff-ins diff-item' id='diff-change-item-4' data-item='4' data-type='addition'><span class='diff-badge badge-kept'>CALIBRATED (Item 4)</span>{target_failover}</ins>"
    html_content = html_content.replace(target_failover, rep_failover)
    
    # 5. Snapshot Restoration Paradox & WORM Deletion Receipts Ledger Solution (Item 25)
    target_worm_ledger = "CryptoShred Health resolves the paradox through the automated <code>MerkleTombstoneReconciliationService</code> backed by an external, immutable Write-Once-Read-Many (WORM) deletion receipt ledger."
    if target_worm_ledger in html_content:
        html_content = html_content.replace(target_worm_ledger, f"<ins class='diff-ins diff-item' id='diff-change-item-25' data-item='25' data-type='addition'><span class='diff-badge badge-kept'>ARCHITECTURE FIX (Item 25)</span>{target_worm_ledger}</ins>")
        
    return f'<div class="paper-sheet" id="chapter-3-section">{html_content}</div>'

def render_chapter_4(images):
    path = os.path.join(CHAPTERS_DIR, "7.Implementation.tex")
    with open(path) as f:
        content = f.read()
    html_content = render_chapter_content(content, images, chapter_num=4)
    
    # 1. Scoped zeroization & memory hygiene (Item 26)
    target_fill = "Executing <code>Arrays.fill(dek, (byte) 0)</code> and <code>Arrays.fill(decryptedBytes, (byte) 0)</code> immediately"
    if target_fill in html_content:
        html_content = html_content.replace(target_fill, f"<ins class='diff-ins diff-item' id='diff-change-item-26' data-item='26' data-type='addition'><span class='diff-badge badge-kept'>SECURITY HARDENING (Item 26)</span>{target_fill}</ins>")
        
    # 2. Key Rotation min_decryption_version (Item 27)
    target_rotation = "<code>min_decryption_version</code>"
    if target_rotation in html_content:
        html_content = html_content.replace(target_rotation, f"<ins class='diff-ins diff-item' id='diff-change-item-27' data-item='27' data-type='addition'><span class='diff-badge badge-kept'>KEY ROTATION (Item 27)</span><code>min_decryption_version</code></ins>", 1)
        
    # 3. Post-Quantum ML-DSA-65 & OpenSSL PSS (Item 28)
    target_mldsa = "ML-DSA-65"
    if target_mldsa in html_content:
        html_content = html_content.replace(target_mldsa, f"<ins class='diff-ins diff-item' id='diff-change-item-28' data-item='28' data-type='addition'><span class='diff-badge badge-kept'>POST-QUANTUM (Item 28)</span>ML-DSA-65</ins>", 1)
        
    # 4. WORM Deletion Receipts Startup Scanner (Item 39/40)
    target_worm_scanner = "external immutable WORM deletion receipts (<code>backups/deletion-receipt_*.json</code>) read via non-blocking directory streams (<code>Files.newDirectoryStream</code>)"
    if target_worm_scanner in html_content:
        html_content = html_content.replace(target_worm_scanner, f"<ins class='diff-ins diff-item' id='diff-change-item-39' data-item='39' data-type='addition'><span class='diff-badge badge-kept'>WORM SCANNER</span>{target_worm_scanner}</ins>")
        
    # 5. FHIR REDACTED Tag (Item 29/41)
    target_fhir = "Using the standard <code>REDACTED</code> vocabulary token guarantees strict schema validation across standard HL7 FHIR R4 validator suites"
    if target_fhir in html_content:
        html_content = html_content.replace(target_fhir, f"<ins class='diff-ins diff-item' id='diff-change-item-29' data-item='29' data-type='addition'><span class='diff-badge badge-kept'>FHIR ALIGNMENT</span>{target_fhir}</ins>")
        
    return f'<div class="paper-sheet" id="chapter-4-section">{html_content}</div>'

def render_chapter_5(images):
    path = os.path.join(CHAPTERS_DIR, "8.Evaluation.tex")
    with open(path) as f:
        content = f.read()
    html_content = render_chapter_content(content, images, chapter_num=5)
    
    # Tooling & Reproducibility Banner (Item 8)
    item8_banner = """
    <div class="diff-ins diff-item" id="diff-change-item-8" data-item="8" data-type="addition" style="margin: 1.5rem 0; padding: 1rem; border-left: 4px solid #10b981; background: #f0fdf4; border-radius: 4px;">
        <span class="diff-badge badge-kept">TOOLING &amp; REPRODUCIBILITY (Item 8)</span>
        <p class="academic-p" style="margin: 0.5rem 0 0 0; font-size: 0.95rem;">
            <strong>Master Figure Generator:</strong> All benchmark plots and architecture diagrams are strictly reproducible via <code>thesis/figures/generate_figures.py</code> supporting CLI flags <code>--all</code>, <code>--fig32</code>, <code>--fig33</code>, and <code>--fig53</code>.
        </p>
    </div>
    """
    marker_testbed = '<h3 class="subsec-title" id="subsec-experimental-testbed-and-process-isolation">Experimental Testbed and Process Isolation</h3>'
    if marker_testbed in html_content:
        html_content = html_content.replace(marker_testbed, marker_testbed + "\n" + item8_banner)
    
    # 1. Total requests sum fix 2,377,883 (Item 34)
    if "$2{,}377{,}883$" in html_content:
        html_content = html_content.replace("$2{,}377{,}883$", "<ins class='diff-ins diff-item' id='diff-change-item-34' data-item='34' data-type='addition'><span class='diff-badge badge-kept'>CORRECTED SUM (Item 34)</span>$2{,}377{,}883$</ins>", 1)
        
    # 2. 25-key test 42.3 ms (Item 35)
    target_423 = "$42.3\\text{&nbsp;ms}$"
    if target_423 in html_content:
        html_content = html_content.replace(target_423, f"<ins class='diff-ins diff-item' id='diff-change-item-35' data-item='35' data-type='addition'><span class='diff-badge badge-kept'>EMPIRICAL RESULT (Item 35)</span>{target_423}</ins>", 1)
        
    # 3. Table 5.1 microbenchmark & JIT warmup explanation (Item 31)
    target_warmup = "5 warmup iterations ($1.0\\text{&nbsp;s}$ each) followed by 5 measurement iterations ($1.0\\text{&nbsp;s}$ each)."
    rep_warmup = f"<mark class='diff-calib diff-item' id='diff-change-item-31' data-item='31' data-type='calibration'><span class='diff-badge badge-calib'>JIT WARMUP (Item 31)</span>{target_warmup}</mark>"
    html_content = html_content.replace(target_warmup, rep_warmup)
    
    # 4. Table 5.2 units and connection pooling (Item 32)
    target_pool = "This $10.68\\text{&nbsp;ms}$ latency in JMH reflects isolated, unpooled HTTP executions where each benchmark invocation performs an independent remote procedure call without connection reuse"
    rep_pool = f"<mark class='diff-calib diff-item' id='diff-change-item-32' data-item='32' data-type='calibration'><span class='diff-badge badge-calib'>CONNECTION POOLING (Item 32)</span>{target_pool}</mark>"
    html_content = html_content.replace(target_pool, rep_pool)
    
    # 5. Table 5.3 heavy tail explanation (Item 33)
    target_heavy = "The elevated sample standard deviations reported in Table"
    if target_heavy in html_content:
        html_content = html_content.replace(target_heavy, f"<mark class='diff-calib diff-item' id='diff-change-item-33' data-item='33' data-type='calibration'><span class='diff-badge badge-calib'>HEAVY TAIL NOTE (Item 33)</span>{target_heavy}")
        html_content = html_content.replace("B-Tree disk page lookups under heavy concurrency.", "B-Tree disk page lookups under heavy concurrency.</mark>")
        
    # 6. Section 5.4.3 RBAC Test Breakdown (Item 37)
    target_rbac = "29 dedicated HTTP security assertions"
    if target_rbac in html_content:
        html_content = html_content.replace(target_rbac, f"<ins class='diff-ins diff-item' id='diff-change-item-37' data-item='37' data-type='addition'><span class='diff-badge badge-kept'>RBAC AUDIT (Item 37)</span>{target_rbac}</ins>")
        
    return f'<div class="paper-sheet" id="chapter-5-section">{html_content}</div>'

def render_chapter_6(images):
    path = os.path.join(CHAPTERS_DIR, "9.Conclusions.tex")
    with open(path) as f:
        content = f.read()
    html_content = render_chapter_content(content, images, chapter_num=6)
    
    # Honest speedup in Table 6.1 (Item 38)
    if "4&ndash;5&times;" in html_content:
        html_content = html_content.replace("4&ndash;5&times;", "<mark class='diff-calib diff-item' id='diff-change-item-38' data-item='38' data-type='calibration'><span class='diff-badge badge-calib'>HONEST SPEEDUP (Item 38)</span>4&ndash;5&times;</mark>", 1)
        
    return f'<div class="paper-sheet" id="chapter-6-section">{html_content}</div>'

def render_bibliography():
    active_entries = []
    for c in ACTIVE_CITATIONS:
        active_entries.append(f"""
        <div class="bib-entry" id="bib-item-{c['num']}">
            <div class="bib-num">[{c['num']}]</div>
            <div class="bib-details">
                <span class="bib-authors">{c['authors']},</span>
                <span class="bib-title">"{c['title']}",</span>
                <span class="bib-journal">{c['journal']},</span>
                <span class="bib-year">{c['year']}.</span>
                <span style="font-size: 0.75rem; color: #94a3b8; font-family: var(--font-mono); margin-left: 0.5rem;">[Key: {c['key']}]</span>
            </div>
        </div>
        """)
        
    removed_rows = []
    for r in REMOVED_CITATIONS:
        removed_rows.append(f"""
        <tr>
            <td><code>{r['key']}</code></td>
            <td><strong>{r['title']}</strong></td>
            <td>{r['author']}</td>
            <td>{r['year']}</td>
            <td><span class="status-chip status-reverted">{r['reason']}</span></td>
        </tr>
        """)
        
    return f"""
    <div class="paper-sheet" id="bibliography-section">
        <h1 class="chap-title">References (Bibliography)</h1>
        <p class="academic-p" style="color: #64748b; font-size: 0.92rem; margin-bottom: 2rem;">
            References formatted according to IEEE standards in order of citation appearance. All 16 citation keys map 100% to in-text citations.
        </p>
        
        <div class="active-bib-list">
            {"".join(active_entries)}
        </div>
        
        <!-- Reverted 22 Citations Box (Item 1) -->
        <div class="diff-del diff-item bib-revert-container" id="diff-change-item-1" data-item="1" data-type="revert">
            <span class="diff-badge badge-revert">REVERTED: 22 Added Citations Removed (Item 1)</span>
            <h3>Reverted Bibliography Entries (Expanded Citations Rolled Back to Original 16)</h3>
            <p class="bib-revert-note">
                <strong>User Directive:</strong> <em>"do not increase the number of citations"</em>. The following 22 citations were removed from <code>thesis/bibliography.bib</code>, successfully restoring the bibliography to the original 16 peer-reviewed and standards citations:
            </p>
            
            <div class="table-scroll">
                <table class="academic-table abbr-table">
                    <thead>
                        <tr>
                            <th style="width: 140px;">BibTeX Key</th>
                            <th>Reference Title</th>
                            <th>Author(s)</th>
                            <th style="width: 70px;">Year</th>
                            <th style="width: 130px;">Action Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        {"".join(removed_rows)}
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    """
