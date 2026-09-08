"""
CSS styles for the CryptoShred Health Visual Comparison Application.
"""

CSS_STYLES = """
:root {
  --font-serif: 'Merriweather', 'Georgia', 'Cambria', serif;
  --font-sans: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  --font-mono: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, Consolas, monospace;
  
  --bg-page: #f8fafc;
  --bg-paper: #ffffff;
  --text-main: #1e293b;
  --text-muted: #64748b;
  --border-subtle: #e2e8f0;
  --border-strong: #cbd5e1;
  
  --primary: #2563eb;
  --primary-hover: #1d4ed8;
  --primary-bg: #eff6ff;
  
  --del-bg: #fee2e2;
  --del-text: #991b1b;
  --del-border: #ef4444;
  --del-badge: #dc2626;
  
  --ins-bg: #dcfce7;
  --ins-text: #166534;
  --ins-border: #22c55e;
  --ins-badge: #16a34a;
  
  --calib-bg: #fef3c7;
  --calib-text: #92400e;
  --calib-border: #f59e0b;
  --calib-badge: #d97706;
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html {
  scroll-behavior: smooth;
  font-size: 16px;
}

body {
  background-color: var(--bg-page);
  color: var(--text-main);
  font-family: var(--font-sans);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

/* ==========================================================================
   TOP STICKY TOOLBAR
   ========================================================================== */
#top-toolbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border-subtle);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 0.6rem 1.5rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.toolbar-brand {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-weight: 700;
  font-size: 1.05rem;
  color: #0f172a;
}

.toolbar-brand .brand-badge {
  background: #1e293b;
  color: #f8fafc;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-family: var(--font-mono);
  text-transform: uppercase;
}

.mode-switch-group {
  display: flex;
  background: #f1f5f9;
  padding: 0.25rem;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  gap: 0.25rem;
}

.btn-mode {
  border: none;
  background: transparent;
  padding: 0.35rem 0.85rem;
  border-radius: 6px;
  font-size: 0.82rem;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.btn-mode:hover {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.6);
}

.btn-mode.active {
  background: #ffffff;
  color: var(--primary);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.btn-filter {
  border: 1px solid var(--border-subtle);
  background: #ffffff;
  padding: 0.3rem 0.65rem;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-filter:hover {
  border-color: #94a3b8;
  color: #1e293b;
}

.btn-filter.active {
  background: #1e293b;
  color: #ffffff;
  border-color: #1e293b;
}

.nav-controls-group {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.btn-nav-step {
  border: 1px solid var(--border-subtle);
  background: #ffffff;
  padding: 0.3rem 0.6rem;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 600;
  color: #334155;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.btn-nav-step:hover {
  background: #f8fafc;
  border-color: #94a3b8;
}

.change-counter-display {
  font-size: 0.78rem;
  font-weight: 600;
  font-family: var(--font-mono);
  color: #64748b;
  min-width: 90px;
  text-align: center;
}

/* ==========================================================================
   MAIN APP LAYOUT (SIDEBAR + CONTENT)
   ========================================================================== */
#app-layout {
  display: flex;
  max-width: 1600px;
  margin: 0 auto;
  min-height: calc(100vh - 60px);
}

#app-sidebar {
  width: 320px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-subtle);
  background: #ffffff;
  position: sticky;
  top: 55px;
  height: calc(100vh - 55px);
  overflow-y: auto;
  padding: 1.25rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

#app-content {
  flex: 1;
  padding: 2rem 3rem;
  max-width: 1200px;
}

.sidebar-section-title {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #94a3b8;
  margin-bottom: 0.5rem;
}

.toc-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.toc-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.4rem 0.6rem;
  border-radius: 6px;
  font-size: 0.82rem;
  color: #475569;
  text-decoration: none;
  transition: all 0.15s ease;
  line-height: 1.3;
}

.toc-link:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.toc-link.active {
  background: var(--primary-bg);
  color: var(--primary);
  font-weight: 600;
}

.toc-badge {
  font-size: 0.7rem;
  font-weight: 700;
  font-family: var(--font-mono);
  padding: 0.1rem 0.4rem;
  border-radius: 9999px;
  background: #f1f5f9;
  color: #64748b;
}

.toc-link.active .toc-badge {
  background: #dbeafe;
  color: #1d4ed8;
}

/* ==========================================================================
   ACADEMIC DISSERTATION PAPER CONTAINER
   ========================================================================== */
.paper-sheet {
  background: var(--bg-paper);
  border: 1px solid var(--border-subtle);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border-radius: 8px;
  padding: 3.5rem 4rem;
  margin-bottom: 3rem;
  position: relative;
}

.page-break-divider {
  height: 24px;
  background: repeating-linear-gradient(45deg, #f1f5f9, #f1f5f9 10px, #e2e8f0 10px, #e2e8f0 20px);
  margin: 3rem -4rem;
  border-top: 1px dashed var(--border-strong);
  border-bottom: 1px dashed var(--border-strong);
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-break-divider::after {
  content: "PAGE BREAK";
  background: #ffffff;
  padding: 0.15rem 0.75rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: #94a3b8;
  border-radius: 4px;
  border: 1px solid var(--border-subtle);
}

/* Title Page */
.title-page-container {
  min-height: 850px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  text-align: center;
}

.title-header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 2px solid #0f172a;
  padding-bottom: 1.25rem;
  text-align: left;
}

.upt-title-block h2 {
  font-size: 1.15rem;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: 0.02em;
}

.upt-title-block p {
  font-size: 0.85rem;
  color: #475569;
}

.title-logo-img {
  height: 65px;
  object-fit: contain;
}

.title-main-block {
  margin: 4rem 0;
}

.dissertation-type-tag {
  font-size: 0.9rem;
  font-weight: 700;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: #64748b;
  margin-bottom: 1.25rem;
}

.thesis-main-title {
  font-family: var(--font-serif);
  font-size: 2.1rem;
  line-height: 1.35;
  color: #0f172a;
  font-weight: 900;
  max-width: 900px;
  margin: 0 auto;
}

.title-meta-block {
  display: flex;
  justify-content: space-between;
  text-align: left;
  border-top: 1px solid var(--border-subtle);
  padding-top: 2rem;
  margin-top: auto;
}

.meta-col h4 {
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
  margin-bottom: 0.25rem;
}

.meta-col p {
  font-size: 1.05rem;
  font-weight: 700;
  color: #0f172a;
}

.title-session-footer {
  margin-top: 2.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: #475569;
}

/* Headings */
.chap-title {
  font-size: 1.85rem;
  font-weight: 800;
  color: #0f172a;
  margin: 2.5rem 0 1.25rem 0;
  border-bottom: 2px solid #0f172a;
  padding-bottom: 0.6rem;
  line-height: 1.3;
}

.chap-title .chap-num {
  color: var(--primary);
  display: block;
  font-size: 1rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 0.25rem;
}

.sec-title {
  font-size: 1.35rem;
  font-weight: 700;
  color: #0f172a;
  margin: 2rem 0 0.85rem 0;
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 0.4rem;
}

.subsec-title {
  font-size: 1.12rem;
  font-weight: 600;
  color: #1e293b;
  margin: 1.5rem 0 0.65rem 0;
}

.subsubsec-title {
  font-size: 1rem;
  font-weight: 600;
  color: #334155;
  margin: 1.2rem 0 0.5rem 0;
}

.para-header {
  font-size: 0.95rem;
  font-weight: 700;
  color: #0f172a;
  margin-top: 1rem;
  margin-bottom: 0.25rem;
}

/* Body prose */
.academic-p {
  font-family: var(--font-serif);
  font-size: 1.02rem;
  line-height: 1.75;
  color: #1e293b;
  margin-bottom: 1.15rem;
  text-align: justify;
}

/* Citations & Links */
.cite-ref {
  color: #0284c7;
  text-decoration: none;
  font-weight: 600;
  font-family: var(--font-sans);
  padding: 0 0.1rem;
  border-bottom: 1px dotted #0284c7;
  transition: all 0.15s ease;
}

.cite-ref:hover {
  background: #e0f2fe;
}

.cite-removed {
  color: var(--del-text);
  background: var(--del-bg);
  border-bottom: 1px dashed var(--del-border);
  padding: 0 0.25rem;
  border-radius: 3px;
  text-decoration: line-through;
}

.sec-ref, .eq-ref {
  color: #4338ca;
  text-decoration: none;
  font-weight: 600;
  border-bottom: 1px dotted #4338ca;
}

/* Figures */
.academic-figure {
  margin: 2.5rem auto;
  max-width: 95%;
  text-align: center;
}

.figure-frame {
  background: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  display: inline-block;
  max-width: 100%;
}

.academic-fig-img {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 0 auto;
  border-radius: 4px;
}

.academic-caption {
  margin-top: 0.75rem;
  font-size: 0.88rem;
  color: #475569;
  line-height: 1.45;
  text-align: center;
  max-width: 850px;
  margin-left: auto;
  margin-right: auto;
}

.academic-caption .caption-label {
  font-weight: 700;
  color: #0f172a;
  margin-right: 0.25rem;
}

/* Tables */
.academic-table-container {
  margin: 2rem auto;
  max-width: 100%;
  overflow-x: auto;
}

.academic-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
  text-align: left;
  border-top: 2px solid #0f172a;
  border-bottom: 2px solid #0f172a;
}

.academic-table th {
  font-weight: 700;
  color: #0f172a;
  padding: 0.65rem 0.85rem;
  border-bottom: 1px solid #0f172a;
  background: #f8fafc;
}

.academic-table td {
  padding: 0.55rem 0.85rem;
  border-bottom: 1px solid var(--border-subtle);
  color: #334155;
}

.academic-table tr:hover td {
  background: #f8fafc;
}

.table-caption {
  font-size: 0.85rem;
  color: #475569;
  margin-top: 0.5rem;
  text-align: left;
}

/* Code Listings */
.academic-code-container {
  margin: 2rem 0;
  border: 1px solid #1e293b;
  border-radius: 8px;
  background: #0f172a;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  background: #1e293b;
  border-bottom: 1px solid #334155;
}

.code-dots {
  display: flex;
  gap: 0.35rem;
}

.code-dots .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot-red { background: #ef4444; }
.dot-yellow { background: #f59e0b; }
.dot-green { background: #10b981; }

.code-caption {
  font-size: 0.8rem;
  font-weight: 600;
  color: #e2e8f0;
  font-family: var(--font-sans);
}

.code-lang {
  font-size: 0.72rem;
  font-family: var(--font-mono);
  color: #94a3b8;
  text-transform: uppercase;
}

.code-body {
  padding: 0.85rem 0;
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 0.82rem;
  color: #f8fafc;
  line-height: 1.5;
}

.code-line {
  display: flex;
  padding: 0 1rem;
}

.code-line:hover {
  background: rgba(255, 255, 255, 0.05);
}

.line-num {
  width: 2.2rem;
  flex-shrink: 0;
  color: #64748b;
  user-select: none;
  text-align: right;
  padding-right: 0.85rem;
}

.line-code {
  flex: 1;
  white-space: pre;
}

/* Lists */
.academic-list {
  margin: 1rem 0 1.25rem 2rem;
  font-family: var(--font-serif);
  font-size: 1.02rem;
  line-height: 1.7;
}

.academic-list li {
  margin-bottom: 0.4rem;
}

/* KaTeX Display */
.katex-display {
  margin: 1.5rem 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.5rem 0;
}

/* ==========================================================================
   DIFF HIGHLIGHTING ENGINE
   ========================================================================== */
.diff-item {
  position: relative;
  transition: all 0.2s ease;
}

.diff-badge {
  display: inline-block;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  font-weight: 700;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-right: 0.4rem;
  vertical-align: middle;
}

.badge-revert {
  background: var(--del-badge);
  color: #ffffff;
}

.badge-kept {
  background: var(--ins-badge);
  color: #ffffff;
}

.badge-polished {
  background: #3b82f6;
  color: #ffffff;
}

.badge-calib {
  background: var(--calib-badge);
  color: #ffffff;
}

/* In-line diff styles (Unified Mode default) */
.diff-del {
  background: var(--del-bg);
  color: var(--del-text);
  border-left: 4px solid var(--del-border);
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  text-decoration: line-through;
  text-decoration-color: var(--del-border);
}

.diff-ins {
  background: var(--ins-bg);
  color: var(--ins-text);
  border-left: 4px solid var(--ins-border);
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  text-decoration: none;
}

.diff-calib {
  background: var(--calib-bg);
  color: var(--calib-text);
  border-left: 4px solid var(--calib-border);
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  text-decoration: none;
}

/* Block diff containers */
div.diff-del, div.diff-ins, div.diff-calib {
  display: block;
  padding: 1rem 1.25rem;
  margin: 1.5rem 0;
  border-radius: 6px;
  text-decoration: none;
}

div.diff-del p, div.diff-ins p, div.diff-calib p {
  margin-bottom: 0.5rem;
}

/* Active Highlight pulse when navigated to */
.diff-item.active-change {
  outline: 3px solid #3b82f6;
  outline-offset: 4px;
  box-shadow: 0 0 15px rgba(59, 130, 246, 0.4);
}

/* VIEW MODE: FINAL AFTER */
body.mode-after .diff-del {
  display: none !important;
}

body.mode-after .diff-ins, body.mode-after .diff-calib {
  background: transparent !important;
  border-left: none !important;
  color: inherit !important;
  padding: 0 !important;
}

body.mode-after .diff-badge {
  display: none !important;
}

body.mode-after .figure-deleted-notice {
  display: none !important;
}

body.mode-after .abbreviations-diff-card {
  display: none !important;
}

/* VIEW MODE: ORIGINAL BEFORE */
body.mode-before .diff-ins {
  display: none !important;
}

body.mode-before .diff-del {
  background: transparent !important;
  border-left: none !important;
  color: inherit !important;
  text-decoration: none !important;
  padding: 0 !important;
}

body.mode-before .diff-calib {
  background: transparent !important;
  border-left: none !important;
  color: inherit !important;
  padding: 0 !important;
}

body.mode-before .diff-badge {
  display: none !important;
}

/* CATEGORY FILTERS */
body.filter-reverts .diff-ins, body.filter-reverts .diff-calib {
  opacity: 0.35;
}

body.filter-calibrations .diff-del, body.filter-calibrations .diff-ins {
  opacity: 0.35;
}

/* Reverted Figure Notices */
.figure-revert-card {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-left: 5px solid #ef4444;
  padding: 1.25rem;
  border-radius: 6px;
  margin: 2rem 0;
}

.figure-deleted-notice {
  display: flex;
  align-items: center;
  gap: 1rem;
  background: #ffffff;
  padding: 1rem;
  border-radius: 6px;
  border: 1px dashed #ef4444;
  margin-top: 0.75rem;
}

.figure-deleted-notice .del-icon {
  font-size: 1.8rem;
}

.figure-deleted-notice .del-text {
  font-size: 0.88rem;
  color: #991b1b;
  line-height: 1.4;
}

/* Abbreviations Revert Card */
.abbreviations-diff-card {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-left: 5px solid #ef4444;
  padding: 1.5rem;
  border-radius: 8px;
  margin: 2rem 0;
}

.diff-header-note {
  font-size: 0.9rem;
  color: #7f1d1d;
  margin-bottom: 1rem;
  line-height: 1.5;
}

.table-scroll {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #fca5a5;
  border-radius: 6px;
  background: #ffffff;
}

.abbr-table th {
  background: #fee2e2;
  color: #991b1b;
  position: sticky;
  top: 0;
}

/* ==========================================================================
   FIGURE 3.3 DEDICATED VISUALIZER
   ========================================================================== */
#fig33-inspector {
  background: #0f172a;
  color: #f8fafc;
  border-radius: 12px;
  padding: 2rem;
  margin-bottom: 3.5rem;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.3);
}

.fig33-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #334155;
  padding-bottom: 1rem;
  margin-bottom: 1.5rem;
}

.fig33-header h2 {
  font-size: 1.4rem;
  font-weight: 800;
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.fig33-view-controls {
  display: flex;
  background: #1e293b;
  padding: 0.25rem;
  border-radius: 8px;
  gap: 0.25rem;
}

.btn-fig-view {
  border: none;
  background: transparent;
  color: #94a3b8;
  padding: 0.35rem 0.85rem;
  border-radius: 6px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-fig-view:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.05);
}

.btn-fig-view.active {
  background: var(--primary);
  color: #ffffff;
}

/* Comparison Canvas Containers */
.fig33-canvas-area {
  position: relative;
  min-height: 600px;
  display: flex;
  justify-content: center;
  background: #1e293b;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #334155;
}

/* Mode 1: Split Slider */
.split-slider-container {
  position: relative;
  width: 100%;
  max-width: 800px;
  overflow: hidden;
  user-select: none;
  margin: 0 auto;
}

.split-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.split-layer img {
  display: block;
  width: 100%;
  height: auto;
}

.split-layer-before {
  z-index: 2;
  overflow: hidden;
}

.split-slider-bar {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 4px;
  background: #ffffff;
  z-index: 10;
  cursor: ew-resize;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.5);
}

.split-handle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 38px;
  height: 38px;
  background: var(--primary);
  border: 3px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 1rem;
}

.slider-label {
  position: absolute;
  top: 1rem;
  padding: 0.25rem 0.65rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 700;
  font-family: var(--font-mono);
  z-index: 5;
  pointer-events: none;
}

.label-before {
  left: 1rem;
  background: rgba(220, 38, 38, 0.85);
  color: #ffffff;
}

.label-after {
  right: 1rem;
  background: rgba(22, 163, 74, 0.85);
  color: #ffffff;
}

/* Mode 2: Side-by-Side */
.side-by-side-container {
  display: none;
  width: 100%;
  gap: 1.5rem;
  padding: 1rem;
}

.fig-side-card {
  flex: 1;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 1rem;
  text-align: center;
}

.fig-side-card h4 {
  font-size: 0.9rem;
  font-weight: 700;
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #334155;
}

.fig-side-card.before h4 { color: #f87171; }
.fig-side-card.after h4 { color: #4ade80; }

.fig-side-card img {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
}

/* Mode 3: Instant A/B Flip */
.flip-container {
  display: none;
  position: relative;
  width: 100%;
  max-width: 750px;
  text-align: center;
  margin: 0 auto;
  padding: 1rem;
}

.flip-img-wrap {
  position: relative;
  border-radius: 6px;
  overflow: hidden;
  border: 2px solid #334155;
}

.flip-img-wrap img {
  width: 100%;
  height: auto;
  display: block;
}

.flip-badge-status {
  position: absolute;
  top: 1.25rem;
  left: 1.25rem;
  padding: 0.4rem 0.85rem;
  border-radius: 6px;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.05em;
  font-family: var(--font-mono);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
}

.flip-badge-after {
  background: #16a34a;
  color: #ffffff;
}

.flip-badge-before {
  background: #dc2626;
  color: #ffffff;
}

.flip-controls-bottom {
  margin-top: 1rem;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
}

.btn-flip-toggle {
  background: var(--primary);
  color: #ffffff;
  border: none;
  padding: 0.6rem 1.5rem;
  border-radius: 8px;
  font-weight: 700;
  font-size: 0.9rem;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4);
  transition: all 0.15s ease;
}

.btn-flip-toggle:hover {
  background: var(--primary-hover);
  transform: translateY(-1px);
}

.flip-shortcut-hint {
  font-size: 0.78rem;
  color: #94a3b8;
}

/* Callout Cards */
.fig33-callouts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
  margin-top: 1.5rem;
}

.callout-card {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 1rem;
}

.callout-card .callout-num {
  font-size: 0.75rem;
  font-weight: 800;
  color: var(--primary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.25rem;
}

.callout-card h4 {
  font-size: 0.92rem;
  color: #f8fafc;
  margin-bottom: 0.35rem;
}

.callout-card p {
  font-size: 0.8rem;
  color: #94a3b8;
  line-height: 1.4;
}

/* Inline Figure 3.3 elements */
.figure-3-3-inline-container {
  text-align: center;
}

.fig33-inline-img {
  max-width: 680px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08);
}

.fig-badge-banner {
  margin-top: 0.75rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  font-size: 0.85rem;
  color: #475569;
}

.btn-inspect-link {
  color: var(--primary);
  font-weight: 600;
  text-decoration: none;
}

.btn-inspect-link:hover {
  text-decoration: underline;
}

/* ==========================================================================
   DECISION AUDIT SUMMARY TABLE
   ========================================================================== */
#decision-audit-section {
  background: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  padding: 2rem;
  margin-bottom: 3.5rem;
}

.audit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  border-bottom: 2px solid var(--border-subtle);
  padding-bottom: 1.25rem;
  margin-bottom: 1.5rem;
}

.audit-header h2 {
  font-size: 1.4rem;
  font-weight: 800;
  color: #0f172a;
}

.audit-controls {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.audit-search-input {
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 0.4rem 0.85rem;
  font-size: 0.82rem;
  outline: none;
  min-width: 200px;
}

.audit-search-input:focus {
  border-color: var(--primary);
}

.audit-tabs {
  display: flex;
  background: #f1f5f9;
  padding: 0.25rem;
  border-radius: 8px;
  gap: 0.25rem;
}

.btn-audit-tab {
  border: none;
  background: transparent;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
}

.btn-audit-tab.active {
  background: #ffffff;
  color: #0f172a;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.audit-table-wrap {
  overflow-x: auto;
}

.audit-main-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  text-align: left;
}

.audit-main-table th {
  background: #f8fafc;
  padding: 0.75rem 0.85rem;
  font-weight: 700;
  color: #0f172a;
  border-bottom: 2px solid var(--border-subtle);
}

.audit-main-table td {
  padding: 0.75rem 0.85rem;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: middle;
}

.audit-main-table tr:hover td {
  background: #f8fafc;
}

.status-chip {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 800;
  font-family: var(--font-mono);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.status-reverted {
  background: #fee2e2;
  color: #b91c1c;
}

.status-kept {
  background: #dcfce7;
  color: #15803d;
}

.status-polished {
  background: #dbeafe;
  color: #1d4ed8;
}

.btn-jump-diff {
  border: 1px solid var(--border-subtle);
  background: #ffffff;
  color: var(--primary);
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.55rem;
  border-radius: 4px;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-jump-diff:hover {
  background: var(--primary-bg);
  border-color: var(--primary);
}

/* ==========================================================================
   BIBLIOGRAPHY SECTION DIFF
   ========================================================================== */
.bib-entry {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  font-size: 0.92rem;
  line-height: 1.5;
  color: #1e293b;
}

.bib-num {
  font-weight: 700;
  color: #0284c7;
  font-family: var(--font-mono);
  flex-shrink: 0;
  width: 2.2rem;
}

.bib-details {
  flex: 1;
}

.bib-title {
  font-weight: 600;
  color: #0f172a;
}

.bib-journal {
  font-style: italic;
  color: #475569;
}

.bib-revert-container {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-left: 5px solid #ef4444;
  padding: 1.5rem;
  border-radius: 8px;
  margin: 2.5rem 0;
}

.bib-revert-container h3 {
  font-size: 1.1rem;
  color: #991b1b;
  margin-bottom: 0.5rem;
}

.bib-revert-note {
  font-size: 0.88rem;
  color: #7f1d1d;
  margin-bottom: 1.25rem;
}
"""
