"""
Client-side JavaScript interactive controller for CryptoShred Health Visual Comparison App.
"""

JS_SCRIPTS = """
// State
let currentDiffIndex = 0;
let visibleDiffs = [];
let figFlipState = 'after'; // 'after' or 'before'
let figImages = {
    after: '',
    before: ''
};

document.addEventListener('DOMContentLoaded', () => {
    updateVisibleDiffsList();
    initSplitSlider();
    initKeyboardShortcuts();
    initScrollSpy();
    
    // Store image sources from DOM for flip toggle
    const afterImg = document.querySelector('.split-layer-after img');
    const beforeImg = document.querySelector('.split-layer-before img');
    if (afterImg) figImages.after = afterImg.src;
    if (beforeImg) figImages.before = beforeImg.src;
});

// ============================================================================
// DOCUMENT VIEW MODES (Unified Diff, Final After, Original Before)
// ============================================================================
function setDocumentMode(mode) {
    document.body.classList.remove('mode-unified', 'mode-after', 'mode-before');
    document.body.classList.add('mode-' + mode);
    
    document.querySelectorAll('.btn-mode').forEach(b => b.classList.remove('active'));
    const activeBtn = document.getElementById('btn-mode-' + mode);
    if (activeBtn) activeBtn.classList.add('active');
    
    updateVisibleDiffsList();
}

// ============================================================================
// FILTER TOOLBAR (All Changes, User Reverts, Calibrations)
// ============================================================================
function setFilter(filterType, btnElement) {
    document.body.classList.remove('filter-reverts', 'filter-calibrations');
    if (filterType !== 'all') {
        document.body.classList.add('filter-' + filterType);
    }
    
    document.querySelectorAll('.btn-filter').forEach(b => b.classList.remove('active'));
    if (btnElement) btnElement.classList.add('active');
    
    updateVisibleDiffsList();
}

// ============================================================================
// STEPPING NAVIGATION (Prev / Next Change)
// ============================================================================
function updateVisibleDiffsList() {
    const allDiffs = Array.from(document.querySelectorAll('.diff-item'));
    visibleDiffs = allDiffs.filter(el => {
        const style = window.getComputedStyle(el);
        return style.display !== 'none' && style.visibility !== 'hidden';
    });
    
    const counter = document.getElementById('change-counter');
    if (counter) {
        if (visibleDiffs.length === 0) {
            counter.textContent = 'No Diffs';
        } else {
            counter.textContent = `Diff ${currentDiffIndex + 1} / ${visibleDiffs.length}`;
        }
    }
}

function jumpNextChange() {
    if (visibleDiffs.length === 0) return;
    currentDiffIndex = (currentDiffIndex + 1) % visibleDiffs.length;
    scrollToDiff(currentDiffIndex);
}

function jumpPrevChange() {
    if (visibleDiffs.length === 0) return;
    currentDiffIndex = (currentDiffIndex - 1 + visibleDiffs.length) % visibleDiffs.length;
    scrollToDiff(currentDiffIndex);
}

function scrollToDiff(index) {
    if (index < 0 || index >= visibleDiffs.length) return;
    const target = visibleDiffs[index];
    
    document.querySelectorAll('.diff-item').forEach(el => el.classList.remove('active-change'));
    target.classList.add('active-change');
    
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    
    const counter = document.getElementById('change-counter');
    if (counter) {
        counter.textContent = `Diff ${index + 1} / ${visibleDiffs.length}`;
    }
}

function highlightChange(id) {
    const el = document.getElementById(id);
    if (!el) return;
    
    document.querySelectorAll('.diff-item').forEach(item => item.classList.remove('active-change'));
    el.classList.add('active-change');
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    
    const idx = visibleDiffs.indexOf(el);
    if (idx !== -1) {
        currentDiffIndex = idx;
        const counter = document.getElementById('change-counter');
        if (counter) {
            counter.textContent = `Diff ${idx + 1} / ${visibleDiffs.length}`;
        }
    }
}

// ============================================================================
// FIGURE 3.3 INTERACTIVE VISUALIZER CONTROLS
// ============================================================================
function setFigViewMode(mode) {
    const splitView = document.getElementById('fig33-split-view');
    const sideView = document.getElementById('fig33-side-view');
    const flipView = document.getElementById('fig33-flip-view');
    
    splitView.style.display = 'none';
    sideView.style.display = 'none';
    flipView.style.display = 'none';
    
    document.querySelectorAll('.btn-fig-view').forEach(b => b.classList.remove('active'));
    
    if (mode === 'split') {
        splitView.style.display = 'block';
        document.getElementById('btn-fig-split').classList.add('active');
    } else if (mode === 'side') {
        sideView.style.display = 'flex';
        document.getElementById('btn-fig-side').classList.add('active');
    } else if (mode === 'flip') {
        flipView.style.display = 'block';
        document.getElementById('btn-fig-flip').classList.add('active');
    }
}

// Interactive Split Slider (Pointer and Touch)
function initSplitSlider() {
    const container = document.getElementById('fig33-split-view');
    const sliderBar = document.getElementById('split-slider-bar');
    const beforeLayer = document.getElementById('split-before-layer');
    if (!container || !sliderBar || !beforeLayer) return;
    
    let isDragging = false;
    
    function setSliderPosition(xPos) {
        const rect = container.getBoundingClientRect();
        let offsetX = xPos - rect.left;
        if (offsetX < 0) offsetX = 0;
        if (offsetX > rect.width) offsetX = rect.width;
        
        const percent = (offsetX / rect.width) * 100;
        beforeLayer.style.width = percent + '%';
        sliderBar.style.left = percent + '%';
    }
    
    sliderBar.addEventListener('mousedown', (e) => {
        isDragging = true;
        e.preventDefault();
    });
    
    window.addEventListener('mousemove', (e) => {
        if (!isDragging) return;
        setSliderPosition(e.clientX);
    });
    
    window.addEventListener('mouseup', () => {
        isDragging = false;
    });
    
    // Touch support
    sliderBar.addEventListener('touchstart', (e) => {
        isDragging = true;
    });
    
    window.addEventListener('touchmove', (e) => {
        if (!isDragging || !e.touches[0]) return;
        setSliderPosition(e.touches[0].clientX);
    });
    
    window.addEventListener('touchend', () => {
        isDragging = false;
    });
}

// Instant A/B Flip Toggle
function toggleABFlip() {
    const activeImg = document.getElementById('flip-active-img');
    const badge = document.getElementById('flip-status-badge');
    if (!activeImg || !badge) return;
    
    if (figFlipState === 'after') {
        figFlipState = 'before';
        activeImg.src = figImages.before;
        badge.textContent = 'VIEWING: BEFORE (v8 Overcrowded)';
        badge.className = 'flip-badge-status flip-badge-before';
    } else {
        figFlipState = 'after';
        activeImg.src = figImages.after;
        badge.textContent = 'VIEWING: AFTER (Final Tall Portrait)';
        badge.className = 'flip-badge-status flip-badge-after';
    }
}

// ============================================================================
// AUDIT TABLE SEARCH & FILTERING
// ============================================================================
function filterAuditStatus(status, btnElement) {
    document.querySelectorAll('.btn-audit-tab').forEach(b => b.classList.remove('active'));
    if (btnElement) btnElement.classList.add('active');
    
    const rows = document.querySelectorAll('#audit-table tbody tr');
    rows.forEach(r => {
        const rowStatus = r.getAttribute('data-status');
        if (status === 'all' || rowStatus === status) {
            r.style.display = '';
        } else {
            r.style.display = 'none';
        }
    });
}

function filterAuditTable() {
    const query = document.getElementById('audit-search').value.toLowerCase();
    const rows = document.querySelectorAll('#audit-table tbody tr');
    
    rows.forEach(r => {
        const text = r.textContent.toLowerCase();
        if (text.includes(query)) {
            r.style.display = '';
        } else {
            r.style.display = 'none';
        }
    });
}

// ============================================================================
// SCROLL SPY FOR SIDEBAR TOC
// ============================================================================
function initScrollSpy() {
    const sections = document.querySelectorAll('.paper-sheet, #decision-audit-section, #fig33-inspector');
    const navLinks = document.querySelectorAll('.toc-link');
    
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const id = entry.target.getAttribute('id');
                navLinks.forEach(link => {
                    if (link.getAttribute('href') === '#' + id) {
                        link.classList.add('active');
                    } else {
                        link.classList.remove('active');
                    }
                });
            }
        });
    }, { rootMargin: '-20% 0px -70% 0px' });
    
    sections.forEach(s => observer.observe(s));
}

// Keyboard shortcuts: Spacebar toggles A/B, '[' and ']' or 'p' and 'n' step diffs
function initKeyboardShortcuts() {
    window.addEventListener('keydown', (e) => {
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
        
        if (e.code === 'Space') {
            const flipView = document.getElementById('fig33-flip-view');
            if (flipView && window.getComputedStyle(flipView).display !== 'none') {
                e.preventDefault();
                toggleABFlip();
            }
        } else if (e.key === '[' || e.key === 'p' || e.key === 'P') {
            jumpPrevChange();
        } else if (e.key === ']' || e.key === 'n' || e.key === 'N') {
            jumpNextChange();
        }
    });
}
"""
