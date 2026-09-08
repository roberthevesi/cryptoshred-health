"""
Interactive HTML components for CryptoShred Health Visual Comparison App:
- Top Sticky Toolbar
- Sticky Navigation Sidebar
- Decision Audit Summary Table (Items 1-17)
- Dedicated Figure 3.3 Interactive Visualizer (Split Slider, Side-by-Side, Instant A/B Flip)
"""

from .data import AUDIT_ITEMS, ABBREVIATIONS

def render_topbar():
    return """
    <header id="top-toolbar">
        <div class="toolbar-brand">
            <span class="brand-badge">CryptoShred Health</span>
            <span>Thesis Visual Diff Inspector</span>
        </div>
        
        <div class="mode-switch-group" role="group" aria-label="Document View Mode">
            <button class="btn-mode active" id="btn-mode-unified" onclick="setDocumentMode('unified')">
                <span>Unified Diff</span>
            </button>
            <button class="btn-mode" id="btn-mode-after" onclick="setDocumentMode('after')">
                <span>Final "After" View</span>
            </button>
            <button class="btn-mode" id="btn-mode-before" onclick="setDocumentMode('before')">
                <span>Original "Before" View</span>
            </button>
        </div>
        
        <div class="filter-group">
            <button class="btn-filter active" onclick="setFilter('all', this)">All Changes (33)</button>
            <button class="btn-filter" onclick="setFilter('reverts', this)">User Reverts Only (7)</button>
            <button class="btn-filter" onclick="setFilter('calibrations', this)">Calibrations (6)</button>
        </div>
        
        <div class="nav-controls-group">
            <button class="btn-nav-step" onclick="jumpPrevChange()" title="Previous Change (Key: P or [)">
                &uarr; Prev
            </button>
            <span class="change-counter-display" id="change-counter">Diff 1 / 33</span>
            <button class="btn-nav-step" onclick="jumpNextChange()" title="Next Change (Key: N or ])">
                Next &darr;
            </button>
        </div>
    </header>
    """

def render_sidebar():
    return """
    <aside id="app-sidebar">
        <div>
            <div class="sidebar-section-title">Quick Actions</div>
            <ul class="toc-list">
                <li><a href="#decision-audit-section" class="toc-link"><strong>Decision Audit Table</strong> <span class="toc-badge">17 Items</span></a></li>
                <li><a href="#fig33-inspector" class="toc-link"><strong>Figure 3.3 Visualizer</strong> <span class="toc-badge">Interactive</span></a></li>
            </ul>
        </div>
        
        <div>
            <div class="sidebar-section-title">Dissertation Contents</div>
            <ul class="toc-list" id="sidebar-toc">
                <li><a href="#chapter-title" class="toc-link">Title & Academic Affiliation <span class="toc-badge">UPT</span></a></li>
                <li><a href="#sec-rezumat" class="toc-link">Rezumat (Romanian Abstract) <span class="toc-badge">1 diff</span></a></li>
                <li><a href="#sec-abstract" class="toc-link">Abstract (English) <span class="toc-badge">1 diff</span></a></li>
                <li><a href="#front-matter-section" class="toc-link">Front Matter (TOC, Figures, Snippets) <span class="toc-badge">2 diffs</span></a></li>
                <li><a href="#diff-change-item-9" class="toc-link">List of Abbreviations <span class="toc-badge">REVERTED</span></a></li>
                <li><a href="#chapter-1" class="toc-link">Chapter 1: Introduction <span class="toc-badge">6 diffs</span></a></li>
                <li><a href="#chapter-2" class="toc-link">Chapter 2: Theoretical Foundations <span class="toc-badge">4 diffs</span></a></li>
                <li><a href="#chapter-3" class="toc-link">Chapter 3: System Design <span class="toc-badge">5 diffs</span></a></li>
                <li><a href="#chapter-4" class="toc-link">Chapter 4: Implementation <span class="toc-badge">4 diffs</span></a></li>
                <li><a href="#chapter-5" class="toc-link">Chapter 5: Evaluation <span class="toc-badge">7 diffs</span></a></li>
                <li><a href="#chapter-6" class="toc-link">Chapter 6: Conclusions <span class="toc-badge">2 diffs</span></a></li>
                <li><a href="#bibliography-section" class="toc-link">Bibliography <span class="toc-badge">16 Citations</span></a></li>
                <li><a href="#diff-change-item-1" class="toc-link">Removed Citations <span class="toc-badge">22 REVERTED</span></a></li>
            </ul>
        </div>
        
        <div style="margin-top: auto; padding-top: 1rem; border-top: 1px solid var(--border-subtle); font-size: 0.75rem; color: #94a3b8;">
            <p><strong>CryptoShred Health</strong></p>
            <p>Candidate: Ing. Robert Hevesi</p>
            <p>Coordinator: Conf. dr. ing. A. Iovanovici</p>
            <p>Politehnica University of Timișoara</p>
        </div>
    </aside>
    """

def render_audit_table():
    rows = []
    for it in AUDIT_ITEMS:
        rows.append(f"""
        <tr data-status="{it['status'].lower()}" data-category="{it['category'].lower()}">
            <td><strong>#{it['id']}</strong></td>
            <td><strong>{it['title']}</strong><br><small style="color: #64748b;">{it['category']}</small></td>
            <td><span class="status-chip {it['badge_class']}">{it['status']}</span></td>
            <td><em>"{it['directive']}"</em></td>
            <td><code>{it['file']}</code><br><span style="font-size: 0.8rem; color: #475569;">{it['summary']}</span></td>
            <td><a href="{it['jump']}" class="btn-jump-diff" onclick="highlightChange('{it['jump'][1:]}')">Jump &rarr;</a></td>
        </tr>
        """)
        
    return f"""
    <section id="decision-audit-section">
        <div class="audit-header">
            <div>
                <h2>Decision Audit Summary Table</h2>
                <p style="font-size: 0.88rem; color: #64748b; margin-top: 0.25rem;">
                    Exhaustive itemized verification of all 17 coordinator feedback & user directives across the dissertation codebase.
                </p>
            </div>
            <div class="audit-controls">
                <input type="text" id="audit-search" class="audit-search-input" placeholder="Filter items by keyword..." oninput="filterAuditTable()">
                <div class="audit-tabs">
                    <button class="btn-audit-tab active" onclick="filterAuditStatus('all', this)">All (17)</button>
                    <button class="btn-audit-tab" onclick="filterAuditStatus('reverted', this)">Reverted (7)</button>
                    <button class="btn-audit-tab" onclick="filterAuditStatus('kept', this)">Kept (8)</button>
                    <button class="btn-audit-tab" onclick="filterAuditStatus('polished', this)">Polished (2)</button>
                </div>
            </div>
        </div>
        
        <div class="audit-table-wrap">
            <table class="audit-main-table" id="audit-table">
                <thead>
                    <tr>
                        <th style="width: 50px;">Item</th>
                        <th style="width: 220px;">Title & Category</th>
                        <th style="width: 110px;">Status</th>
                        <th style="width: 280px;">User Directive</th>
                        <th>File & Technical Change Summary</th>
                        <th style="width: 80px;">Action</th>
                    </tr>
                </thead>
                <tbody>
                    {"".join(rows)}
                </tbody>
            </table>
        </div>
    </section>
    """

def render_fig33_visualizer(images):
    return f"""
    <section id="fig33-inspector">
        <div class="fig33-header">
            <div>
                <h2>Figure 3.3 Visual Comparison Inspector</h2>
                <p style="font-size: 0.85rem; color: #94a3b8; margin-top: 0.2rem;">
                    Interactive inspection of Figure 3.3 (Erasure Pipeline Sequence Diagram): Before vs. After re-design.
                </p>
            </div>
            
            <div class="fig33-view-controls">
                <button class="btn-fig-view active" id="btn-fig-split" onclick="setFigViewMode('split')">Interactive Split Slider</button>
                <button class="btn-fig-view" id="btn-fig-side" onclick="setFigViewMode('side')">Side-by-Side</button>
                <button class="btn-fig-view" id="btn-fig-flip" onclick="setFigViewMode('flip')">Instant A/B Flip</button>
            </div>
        </div>
        
        <div class="fig33-canvas-area">
            <!-- MODE 1: SPLIT SLIDER -->
            <div class="split-slider-container" id="fig33-split-view">
                <div class="split-layer split-layer-after">
                    <img src="{images['fig33_after']}" alt="Figure 3.3 After (Final Tall Portrait)">
                    <span class="slider-label label-after">AFTER (Final Re-design)</span>
                </div>
                <div class="split-layer split-layer-before" id="split-before-layer" style="width: 50%;">
                    <img src="{images['fig33_before']}" alt="Figure 3.3 Before (v8 Overcrowded)" id="split-before-img">
                    <span class="slider-label label-before">BEFORE (v8 Overcrowded)</span>
                </div>
                <div class="split-slider-bar" id="split-slider-bar" style="left: 50%;">
                    <div class="split-handle">&harr;</div>
                </div>
            </div>
            
            <!-- MODE 2: SIDE BY SIDE -->
            <div class="side-by-side-container" id="fig33-side-view">
                <div class="fig-side-card before">
                    <h4>BEFORE (v8 with Overcrowded Elements)</h4>
                    <img src="{images['fig33_before']}" alt="Figure 3.3 Before">
                </div>
                <div class="fig-side-card after">
                    <h4>AFTER (Final Tall Portrait 7.6 &times; 11.2 in)</h4>
                    <img src="{images['fig33_after']}" alt="Figure 3.3 After">
                </div>
            </div>
            
            <!-- MODE 3: INSTANT A/B FLIP -->
            <div class="flip-container" id="fig33-flip-view">
                <div class="flip-img-wrap">
                    <img src="{images['fig33_after']}" alt="Flip Image" id="flip-active-img">
                    <div class="flip-badge-status flip-badge-after" id="flip-status-badge">VIEWING: AFTER (FINAL)</div>
                </div>
                <div class="flip-controls-bottom">
                    <button class="btn-flip-toggle" onclick="toggleABFlip()">Toggle A/B Comparison</button>
                    <span class="flip-shortcut-hint">Tip: Press [Spacebar] anywhere to toggle</span>
                </div>
            </div>
        </div>
        
        <!-- 5 Callout Cards detailing fixes -->
        <div class="fig33-callouts-grid">
            <div class="callout-card">
                <div class="callout-num">Fix 1</div>
                <h4>Box Borders & Lifeline Margins</h4>
                <p>Component headers ("Merkle Signer", "PostgreSQL + Event Bus") fit with ample padding inside rounded cards without border clipping.</p>
            </div>
            <div class="callout-card">
                <div class="callout-num">Fix 2</div>
                <h4>Complete Arrow Visibility</h4>
                <p>Full directional sequence arrows visible between lifelines without clipping or obscured arrowheads.</p>
            </div>
            <div class="callout-card">
                <div class="callout-num">Fix 3</div>
                <h4>Clean Two-Line Typography</h4>
                <p>Action titles placed cleanly above message arrows and technical descriptions below arrows with generous margins.</p>
            </div>
            <div class="callout-card">
                <div class="callout-num">Fix 4</div>
                <h4>Opaque White Lifeline Masking</h4>
                <p>All message boxes and stage badges feature solid white backing so text never touches or floats confusingly over background lines.</p>
            </div>
            <div class="callout-card">
                <div class="callout-num">Fix 5</div>
                <h4>Tall Portrait Aspect Ratio</h4>
                <p>Re-proportioned to 7.6 &times; 11.2 in portrait orientation to seamlessly fill an A4 dissertation page alongside its caption.</p>
            </div>
        </div>
    </section>
    """
