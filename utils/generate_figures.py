#!/usr/bin/env python3
"""
generate_figures.py — Master Publication Figure Generator & Calibrator for CryptoShred Health Dissertation.

Produces clean, professional, publication-quality vector PDFs in thesis/figures/:
- Fig 3.2: Calibrated Vault Raft Consensus Auto-Failover (< 2.5s failover, 1.84s re-election, 2.15s reroute)
- Fig 3.3: Coordinated 5-Stage Cryptographic Erasure & Verification Pipeline Sequence Diagram
- Fig 3.4: Relational Entity-Relationship Model (Plaintext Metadata vs Encrypted Payloads vs Blind Indexes)
- Fig 3.5: Directional Binary Merkle Tree Audit Trail & Inclusion Proof Path (log2 N Traversal)
- Fig 5.3: Macro System Throughput Across Concurrency Tiers (Strictly Numerical Legend Order: Scenarios 1, 2, 3, 4)
"""

import os
import sys
import argparse
import zlib
import re
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.patches as patches

try:
    import pymupdf
except ImportError:
    import fitz as pymupdf

UTILS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(UTILS_DIR, ".."))
FIGURES_DIR = os.path.join(PROJECT_ROOT, "thesis", "figures")

# Global publication matplotlib style
plt.rcParams.update({
    'font.family': 'sans-serif',
    'font.sans-serif': ['DejaVu Sans', 'Helvetica', 'Arial'],
    'mathtext.fontset': 'cm',
})


def calibrate_figure_3_2():
    """
    Calibrates Figure 3.2 (thesis/figures/fig_vault_raft.pdf):
    Updates/patches references to < 2.5s failover:
    - Banner title: (< 2.5s)
    - HAProxy box: Auto-Failover (< 2.5s)
    - Box 2: Secures 2/3 quorum, re-elects in 1.84s
    - Box 4: health in 2.15s, failover < 2.5s
    """
    pdf_path = os.path.join(FIGURES_DIR, "fig_vault_raft.pdf")
    if not os.path.exists(pdf_path):
        print(f"[WARN] {pdf_path} not found for calibration.")
        return

    doc = pymupdf.open(pdf_path)
    page = doc[0]
    contents_xref = page.get_contents()[0]
    stream_bytes = doc.xref_stream(contents_xref)

    # 1. Banner title
    old_banner = b"(orkflow \\(< 500ms\\))"
    new_banner = b"(orkflow \\(< 2.5s\\))"
    if old_banner in stream_bytes:
        stream_bytes = stream_bytes.replace(old_banner, new_banner)

    # 2. HAProxy auto-failover bullet
    old_ha = b"(-F) 91.5178571429 (ailover \\(<500ms\\))"
    new_ha = b"(-F) 91.5178571429 (ailover \\(< 2.5s\\))"
    if old_ha in stream_bytes:
        stream_bytes = stream_bytes.replace(old_ha, new_ha)

    # 3. Box 2 (Raft Election)
    old_b2_l2 = b"[ (Secur) 21.3068181818 (es 2/3) ] TJ"
    new_b2_l2 = b"[ (Secur) 21.3068181818 (es 2/3 quorum,) ] TJ"
    old_b2_l3 = b"[ (quorum votes.) ] TJ"
    new_b2_l3 = b"[ (re-elects in 1.84s.) ] TJ"
    if old_b2_l2 in stream_bytes:
        stream_bytes = stream_bytes.replace(old_b2_l2, new_b2_l2)
    if old_b2_l3 in stream_bytes:
        stream_bytes = stream_bytes.replace(old_b2_l3, new_b2_l3)

    # 4. Box 4 (Traffic Reroute)
    old_b4_l2 = b"[ (health shif) 16.571969697 (t,) ] TJ"
    new_b4_l2 = b"[ (health in 2.15s,) ] TJ"
    old_b4_l3 = b"[ (r) 21.3068181818 (er) 21.3068181818 (outes <500ms.) ] TJ"
    new_b4_l3 = b"[ (failover < 2.5s.) ] TJ"
    if old_b4_l2 in stream_bytes:
        stream_bytes = stream_bytes.replace(old_b4_l2, new_b4_l2)
    if old_b4_l3 in stream_bytes:
        stream_bytes = stream_bytes.replace(old_b4_l3, new_b4_l3)

    doc.update_stream(contents_xref, stream_bytes)
    output_pdf = pdf_path
    temp_output = output_pdf + ".tmp"
    doc.save(temp_output)
    doc.close()
    os.replace(temp_output, output_pdf)
    print(f"  [OK] Calibrated Figure 3.2 saved to: {output_pdf}")


def generate_figure_3_3():
    """
    Figure 3.3 (thesis/figures/fig_erasure_sequence.pdf):
    5-Stage Cryptographic Erasure & Verification Pipeline Sequence Diagram.

    TALL PORTRAIT FORMAT:
    Fits cleanly on a single thesis page without clipping, crowding, or line collisions.

    EASILY EDITABLE:
    All coordinates, text labels, and offsets are defined in the tables below:
    - ACTORS: Lifeline x-positions, box labels, and palette colors.
    - STAGES: Horizontal stage separator heights (y) and banner titles.
    - ACTIVATIONS: Execution blocks on lifelines (x, y_top, y_bot).
    - MESSAGES: Sequence arrows, titles (above arrow), descriptions (below arrow).

    To adjust any position or text:
    1. Tweak the corresponding coordinate or string in the configuration dictionaries.
    2. Run: python3 thesis/figures/generate_figures.py --fig33
    """
    # --------------------------------------------------------------------------
    # 1. Canvas Dimensions & Matplotlib Setup (Wider Aspect Ratio ~= 1.18)
    # --------------------------------------------------------------------------
    fig, ax = plt.subplots(figsize=(9.2, 11.0), dpi=300)
    xlim_max = 128
    ylim_max = 155
    ax.set_xlim(0, xlim_max)
    ax.set_ylim(0, ylim_max)
    ax.axis('off')

    # Pure white background
    fig.patch.set_facecolor('white')
    ax.set_facecolor('white')

    # Publication Color Palette
    C_CLIENT = '#0284c7'   # Sky 600
    C_API    = '#059669'   # Emerald 600
    C_VAULT  = '#d97706'   # Amber 600
    C_STORE  = '#dc2626'   # Red 600
    C_MERKLE = '#4f46e5'   # Indigo 600
    C_TEXT   = '#0f172a'   # Slate 900
    C_SUB    = '#475569'   # Slate 600
    C_LINE   = '#cbd5e1'   # Slate 300

    # --------------------------------------------------------------------------
    # 2. Main Title
    # --------------------------------------------------------------------------
    ax.text(xlim_max / 2.0, 152.5, '5-Stage Cryptographic Erasure & Verification Pipeline', 
            ha='center', va='top', fontsize=12.5, fontweight='bold', color=C_TEXT)
    ax.text(xlim_max / 2.0, 149.8, 'End-to-End Sequence: Policy Evaluation, KMS Key Shredding, Relational Tombstone, & PQC Audit Proof',
            ha='center', va='top', fontsize=7.6, style='italic', color=C_SUB)

    # --------------------------------------------------------------------------
    # 3. Actors Configuration (Lifelines at x = 14, 39, 64, 89, 114)
    # --------------------------------------------------------------------------
    top_y = 144.5
    bot_y = 6.5
    box_w, box_h = 19.5, 4.8

    ACTORS = [
        {'id': 'client',  'name': 'Client / DPO',    'sub': 'Data Subject / Auditor', 'x': 14.0, 'color': C_CLIENT, 'bg': '#f0f9ff'},
        {'id': 'api',     'name': 'Backend API',     'sub': 'Spring Boot Service',    'x': 39.0, 'color': C_API,    'bg': '#ecfdf5'},
        {'id': 'vault',   'name': 'Vault KMS',       'sub': 'Transit Engine (Raft)',  'x': 64.0, 'color': C_VAULT,  'bg': '#fffbeb'},
        {'id': 'storage', 'name': 'Storage & Kafka', 'sub': 'PostgreSQL + Event Bus', 'x': 89.0, 'color': C_STORE,  'bg': '#fef2f2'},
        {'id': 'merkle',  'name': 'Merkle Signer',   'sub': 'ML-DSA-65 & RSA-PSS',    'x': 114.0,'color': C_MERKLE, 'bg': '#eef2ff'},
    ]

    for a in ACTORS:
        x = a['x']
        # Continuous subtle lifeline
        ax.plot([x, x], [top_y - box_h, bot_y + 3.0], color=C_LINE, linestyle='--', linewidth=1.0, zorder=1)
        
        # Top Actor Header Box
        box = patches.FancyBboxPatch((x - box_w/2, top_y - box_h), box_w, box_h,
                                    boxstyle='round,pad=0.2,rounding_size=0.4',
                                    fc=a['bg'], ec=a['color'], linewidth=1.3, zorder=3)
        ax.add_patch(box)
        ax.text(x, top_y - 1.6, a['name'], ha='center', va='center', fontsize=7.8, fontweight='bold', color=C_TEXT, zorder=4)
        ax.text(x, top_y - 3.4, a['sub'], ha='center', va='center', fontsize=6.2, style='italic', color=a['color'], zorder=4)

        # Bottom Actor Anchor Box
        bot_box = patches.FancyBboxPatch((x - box_w/2, bot_y), box_w, 2.8,
                                        boxstyle='round,pad=0.1,rounding_size=0.3',
                                        fc=a['bg'], ec=a['color'], linewidth=1.0, zorder=3)
        ax.add_patch(bot_box)
        ax.text(x, bot_y + 1.4, a['name'], ha='center', va='center', fontsize=6.8, fontweight='bold', color=C_TEXT, zorder=4)

    # --------------------------------------------------------------------------
    # 4. Stage Dividers (Full-width clean divider with centered stage pill)
    # --------------------------------------------------------------------------
    STAGES = [
        {'y': 135.0, 'num': 'STAGE 1', 'title': 'Statutory Retention Check & Policy Authorization', 'color': C_CLIENT},
        {'y': 112.0, 'num': 'STAGE 2', 'title': 'Cryptographic Key Shredding (Constant-Time O(1) KMS Zeroization)', 'color': C_VAULT},
        {'y': 83.0,  'num': 'STAGE 3 & 4', 'title': 'Relational DB Tombstone & Asynchronous Kafka Event Log', 'color': C_STORE},
        {'y': 59.0,  'num': 'STAGE 5', 'title': 'Binary Merkle Tree Accumulation & Post-Quantum Dual-Signature Minting', 'color': C_MERKLE},
        {'y': 27.0,  'num': 'DELIVERY', 'title': 'Verifiable Deletion Receipt Delivery & Independent Client Verification', 'color': C_CLIENT},
    ]

    for s in STAGES:
        y = s['y']
        ax.plot([3, xlim_max - 3], [y, y], color=s['color'], linestyle='-', linewidth=0.8, alpha=0.3, zorder=2)
        label_text = f"  {s['num']}: {s['title']}  "
        ax.text(xlim_max / 2.0, y, label_text, ha='center', va='center', fontsize=6.6, fontweight='bold', color=s['color'],
                zorder=3, bbox=dict(boxstyle='round,pad=0.25,rounding_size=0.3', fc='white', ec=s['color'], lw=0.8))

    # --------------------------------------------------------------------------
    # 5. Localized Activation Bars
    # --------------------------------------------------------------------------
    ACTIVATIONS = [
        {'x': 14.0, 'y_top': 130.0, 'y_bot': 126.0, 'fc': '#bae6fd', 'ec': C_CLIENT},
        {'x': 39.0, 'y_top': 130.0, 'y_bot': 117.0, 'fc': '#a7f3d0', 'ec': C_API},
        {'x': 39.0, 'y_top': 107.0, 'y_bot': 88.0,  'fc': '#a7f3d0', 'ec': C_API},
        {'x': 64.0, 'y_top': 107.0, 'y_bot': 92.0,  'fc': '#fde68a', 'ec': C_VAULT},
        {'x': 39.0, 'y_top': 77.0,  'y_bot': 64.0,  'fc': '#a7f3d0', 'ec': C_API},
        {'x': 89.0, 'y_top': 77.0,  'y_bot': 68.0,  'fc': '#fecaca', 'ec': C_STORE},
        {'x': 39.0, 'y_top': 54.0,  'y_bot': 31.0,  'fc': '#a7f3d0', 'ec': C_API},
        {'x': 114.0,'y_top': 54.0,  'y_bot': 36.0,  'fc': '#c7d2fe', 'ec': C_MERKLE},
        {'x': 14.0, 'y_top': 21.0,  'y_bot': 13.0,  'fc': '#bae6fd', 'ec': C_CLIENT},
    ]

    w_act = 1.3
    for act in ACTIVATIONS:
        bar = patches.Rectangle((act['x'] - w_act/2, act['y_bot']), w_act, act['y_top'] - act['y_bot'],
                                fc=act['fc'], ec=act['ec'], linewidth=0.8, zorder=2)
        ax.add_patch(bar)

    # --------------------------------------------------------------------------
    # 6. Messages Configuration Table (Easily editable coordinates and labels)
    # --------------------------------------------------------------------------
    MESSAGES = [
        # --- STAGE 1: Authorization ---
        {
            'num': 1, 'type': 'msg', 'from_x': 14.0, 'to_x': 39.0, 'y': 128.0,
            'title': 'DELETE /patients/{id}/shred',
            'desc': 'Reason: COURT_ORDER (override)',
            'color': C_CLIENT, 'is_reply': False,
        },
        {
            'num': 2, 'type': 'self', 'x': 39.0, 'y': 119.5, 'to_left': False,
            'title': 'Retention Policy Check',
            'desc': 'Verify active encounters & holds',
            'color': C_API,
        },
        # --- STAGE 2: KMS Erasure ---
        {
            'num': 3, 'type': 'msg', 'from_x': 39.0, 'to_x': 64.0, 'y': 105.0,
            'title': 'DELETE /transit/keys/{id}',
            'desc': 'Zeroize master KEK in KMS',
            'color': C_VAULT, 'is_reply': False,
        },
        {
            'num': 4, 'type': 'self', 'x': 64.0, 'y': 98.0, 'to_left': False,
            'title': 'Zeroize Master KEK in RAM',
            'desc': '3-node Raft purge (5.6–7.6 ms)',
            'color': C_VAULT,
        },
        {
            'num': 5, 'type': 'msg', 'from_x': 64.0, 'to_x': 39.0, 'y': 90.0,
            'title': '204 No Content',
            'desc': 'Master KEK destroyed in Vault',
            'color': C_VAULT, 'is_reply': True,
        },
        # --- STAGE 3 & 4: Relational DB & Kafka ---
        {
            'num': 6, 'type': 'msg', 'from_x': 39.0, 'to_x': 89.0, 'y': 75.5,
            'title': 'UPDATE patients SET blobs = NULL',
            'desc': 'Set shredded = true; clear blind indexes',
            'color': C_STORE, 'is_reply': False,
        },
        {
            'num': 7, 'type': 'msg', 'from_x': 89.0, 'to_x': 39.0, 'y': 66.5,
            'title': 'Kafka Event: PATIENT_SHREDDED',
            'desc': 'Evict L2 Redis; commit to audit topic',
            'color': C_STORE, 'is_reply': True,
        },
        # --- STAGE 5: Merkle Tree & PQC Signatures ---
        {
            'num': 8, 'type': 'msg', 'from_x': 39.0, 'to_x': 114.0, 'y': 51.5,
            'title': 'appendLeaf(H_leaf)',
            'desc': 'H_leaf = SHA-256(EntityID || Timestamp || Status)',
            'color': C_MERKLE, 'is_reply': False,
        },
        {
            'num': 9, 'type': 'self', 'x': 114.0, 'y': 43.0, 'to_left': True,
            'title': 'Dual-Sign Merkle Root',
            'desc': 'RSA-2048 + ML-DSA-65 (PQC)',
            'color': C_MERKLE,
        },
        {
            'num': 10, 'type': 'msg', 'from_x': 114.0, 'to_x': 39.0, 'y': 34.5,
            'title': 'Signed Deletion Certificate',
            'desc': 'Proof Bundle: {Root, Leaf, Path, Signatures}',
            'color': C_MERKLE, 'is_reply': True,
        },
        # --- DELIVERY ---
        {
            'num': 11, 'type': 'msg', 'from_x': 39.0, 'to_x': 14.0, 'y': 18.5,
            'title': '200 OK: Deletion Receipt',
            'desc': 'Offline proof verification < 1 ms',
            'color': C_CLIENT, 'is_reply': True,
        },
    ]

    # Draw all messages
    w_bar = 0.7
    bbox_white = dict(boxstyle='round,pad=0.25', fc='white', ec='none', alpha=1.0)

    for m in MESSAGES:
        if m['type'] == 'msg':
            x1, x2, y = m['from_x'], m['to_x'], m['y']
            start_x = x1 + (w_bar if x2 > x1 else -w_bar)
            end_x = x2 + (-w_bar if x2 > x1 else w_bar)
            ls = '--' if m.get('is_reply', False) else '-'
            arrow = patches.FancyArrowPatch((start_x, y), (end_x, y),
                                           arrowstyle='->,head_width=3.2,head_length=4.2',
                                           linestyle=ls, color=m['color'], linewidth=1.3, zorder=3)
            ax.add_patch(arrow)
            mid_x = (x1 + x2) / 2.0
            
            # Title sits cleanly above arrow; description sits cleanly below arrow
            ax.text(mid_x, y + 0.9, f"{m['num']}. {m['title']}", ha='center', va='bottom',
                    fontsize=7.0, fontweight='bold', color=m['color'], zorder=5, bbox=bbox_white)
            if m.get('desc'):
                ax.text(mid_x, y - 0.9, m['desc'], ha='center', va='top',
                        fontsize=6.0, color=C_SUB, zorder=5, bbox=bbox_white)

        elif m['type'] == 'self':
            x, y = m['x'], m['y']
            to_left = m.get('to_left', False)
            y_top = y + 1.8
            y_bot = y - 1.8
            x_base = x - w_bar if to_left else x + w_bar
            angle = 180 if to_left else 0
            path = patches.FancyArrowPatch((x_base, y_top), (x_base, y_bot),
                                          connectionstyle=f'arc,angleA={angle},angleB={angle},armA=14,armB=14,rad=5',
                                          arrowstyle='->,head_width=3.0,head_length=4.0',
                                          color=m['color'], linewidth=1.3, zorder=3)
            ax.add_patch(path)
            
            if to_left:
                tx = x - w_bar - 5.5
                ax.text(tx, y + 0.8, f"{m['num']}. {m['title']}", ha='right', va='bottom',
                        fontsize=6.9, fontweight='bold', color=m['color'], zorder=5, bbox=bbox_white)
                if m.get('desc'):
                    ax.text(tx, y - 0.8, m['desc'], ha='right', va='top',
                            fontsize=6.0, color=C_SUB, zorder=5, bbox=bbox_white)
            else:
                tx = x + w_bar + 5.5
                ax.text(tx, y + 0.8, f"{m['num']}. {m['title']}", ha='left', va='bottom',
                    fontsize=6.9, fontweight='bold', color=m['color'], zorder=5, bbox=bbox_white)
                if m.get('desc'):
                    ax.text(tx, y - 0.8, m['desc'], ha='left', va='top',
                            fontsize=6.0, color=C_SUB, zorder=5, bbox=bbox_white)

    # --------------------------------------------------------------------------
    # 7. Render & Save Publication Outputs
    # --------------------------------------------------------------------------
    out_pdf = os.path.join(FIGURES_DIR, 'fig_erasure_sequence.pdf')
    out_png = os.path.join(FIGURES_DIR, 'fig_erasure_sequence.png')
    plt.savefig(out_pdf, format='pdf', bbox_inches='tight')
    plt.savefig(out_png, format='png', bbox_inches='tight', dpi=300)
    plt.close()
    print(f"  [OK] Generated Figure 3.3 saved to: {out_pdf}")



def generate_figure_3_4():
    """
    Figure 3.4 (thesis/figures/fig_er_model.pdf):
    Entity-Relationship Model:
    Patient (1..*), PatientVisit (1..*), PatientAttachment, EncryptionKey, MerkleNode
    Distinguishes Plaintext Metadata vs Encrypted Payloads vs Salted Blind Indexes.
    """
    fig, ax = plt.subplots(figsize=(14.0, 10.0), dpi=300)
    ax.set_xlim(0, 140)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(70, 98.2, 'CryptoShred Health Relational Entity-Relationship Model',
            ha='center', va='top', fontsize=14, fontweight='bold', color='#0f172a')
    ax.text(70, 96.0, 'Separation of Plaintext Metadata, Salted HMAC Blind Indexes, and AES-256-GCM Encrypted Payloads',
            ha='center', va='top', fontsize=9.2, style='italic', color='#475569')

    def draw_entity_card(x, y, w, h, title, table_name, header_color, sections):
        card = patches.FancyBboxPatch((x, y - h), w, h, boxstyle='round,pad=0.2,rounding_size=0.8',
                                      fc='white', ec=header_color, linewidth=1.5, zorder=2)
        ax.add_patch(card)
        
        hh = 4.0
        header = patches.FancyBboxPatch((x, y - hh), w, hh, boxstyle='round,pad=0.2,rounding_size=0.8',
                                        fc=header_color, ec=header_color, linewidth=1.5, zorder=3)
        ax.add_patch(header)
        flat_rect = patches.Rectangle((x, y - hh), w, hh/2, fc=header_color, ec=header_color, zorder=3)
        ax.add_patch(flat_rect)
        
        ax.text(x + w/2, y - 1.5, title, ha='center', va='center', fontsize=9.2, fontweight='bold', color='white', zorder=4)
        ax.text(x + w/2, y - 3.0, table_name, ha='center', va='center', fontsize=6.8, style='italic', color='#f1f5f9', zorder=4)
        
        curr_y = y - hh - 0.6
        for sec in sections:
            stitle = sec.get('title', '')
            s_bg = sec.get('bg', '#f1f5f9')
            s_fg = sec.get('fg', '#334155')
            s_bar = patches.Rectangle((x + 0.6, curr_y - 1.7), w - 1.2, 1.7, fc=s_bg, ec='none', zorder=3)
            ax.add_patch(s_bar)
            ax.text(x + 1.2, curr_y - 0.85, stitle, ha='left', va='center', fontsize=6.2, fontweight='bold', color=s_fg, zorder=4)
            curr_y -= 2.2
            
            for f in sec.get('fields', []):
                badge = f.get('badge', '')
                bbg = f.get('bbg', '#e2e8f0')
                bfg = f.get('bfg', '#1e293b')
                name = f.get('name', '')
                dtype = f.get('dtype', '')
                note = f.get('note', '')
                
                bx = x + 1.2
                if badge:
                    bw = len(badge) * 0.9 + 1.1
                    bp = patches.FancyBboxPatch((bx, curr_y - 1.2), bw, 1.5, boxstyle='round,pad=0.1,rounding_size=0.3',
                                                fc=bbg, ec='none', zorder=3)
                    ax.add_patch(bp)
                    ax.text(bx + bw/2, curr_y - 0.45, badge, ha='center', va='center', fontsize=5.5, fontweight='bold', color=bfg, zorder=4)
                    tx = bx + bw + 0.8
                else:
                    tx = bx
                    
                ax.text(tx, curr_y - 0.45, name, ha='left', va='center', fontsize=6.5, fontweight='semibold', color='#0f172a', zorder=4)
                ax.text(x + w - 1.8, curr_y - 0.45, dtype, ha='right', va='center', fontsize=6.0, style='italic', color='#64748b', zorder=4)
                if note:
                    curr_y -= 1.3
                    ax.text(tx + 0.6, curr_y - 0.35, note, ha='left', va='center', fontsize=5.5, style='italic', color='#64748b', zorder=4)
                curr_y -= 1.7
            curr_y -= 0.4

    # 1. Patient Table (x=5, y=92, w=39, h=43)
    pat_sections = [
        {
            'title': 'PLAINTEXT METADATA & RELATIONAL KEYS', 'bg': '#e0f2fe', 'fg': '#0369a1',
            'fields': [
                {'badge': 'PK', 'bbg': '#0284c7', 'bfg': 'white', 'name': 'id', 'dtype': 'UUID'},
                {'badge': 'UK', 'bbg': '#38bdf8', 'bfg': '#0c4a6e', 'name': 'patient_id', 'dtype': 'VARCHAR(64)', 'note': 'Business ID (PAT-YYYY-...)'},
                {'badge': 'FK', 'bbg': '#64748b', 'bfg': 'white', 'name': 'encryption_key_id', 'dtype': 'UUID', 'note': 'References encryption_keys (KEK_pat)'},
                {'badge': 'SYS', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'is_active', 'dtype': 'BOOLEAN'},
            ]
        },
        {
            'title': 'SALTED HMAC-SHA256 BLIND INDEXES', 'bg': '#fef3c7', 'fg': '#92400e',
            'fields': [
                {'badge': 'IDX', 'bbg': '#f59e0b', 'bfg': 'white', 'name': 'blind_index_nhs', 'dtype': 'VARCHAR(64)', 'note': 'B-Tree indexed | Null on shred'},
                {'badge': 'IDX', 'bbg': '#f59e0b', 'bfg': 'white', 'name': 'blind_index_mrn', 'dtype': 'VARCHAR(64)', 'note': 'B-Tree indexed | Null on shred'},
                {'badge': 'IDX', 'bbg': '#f59e0b', 'bfg': 'white', 'name': 'blind_index_last_name', 'dtype': 'VARCHAR(64)', 'note': 'B-Tree indexed | Null on shred'},
            ]
        },
        {
            'title': 'ZERO-PLAINTEXT ENCRYPTED PAYLOAD (AES-GCM)', 'bg': '#fee2e2', 'fg': '#b91c1c',
            'fields': [
                {'badge': 'ENC', 'bbg': '#dc2626', 'bfg': 'white', 'name': 'encrypted_data_blob', 'dtype': 'TEXT', 'note': 'firstName, lastName, dob, nhs, address, contact'},
            ]
        },
        {
            'title': 'STATUTORY RETENTION & AUDIT PROOF', 'bg': '#ede9fe', 'fg': '#6d28d9',
            'fields': [
                {'badge': 'DEL', 'bbg': '#ef4444', 'bfg': 'white', 'name': 'shredded', 'dtype': 'BOOLEAN', 'note': 'true = KEK destroyed & blob nullified'},
                {'badge': 'PRF', 'bbg': '#7c3aed', 'bfg': 'white', 'name': 'deletion_proof_json', 'dtype': 'TEXT', 'note': 'Signed erasure certificate C_erasure'},
                {'badge': 'TS', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'created_at, updated_at', 'dtype': 'TIMESTAMP'},
            ]
        }
    ]
    draw_entity_card(5, 92, 39, 43, 'Patient', 'patients (Demographic Master)', '#059669', pat_sections)

    # 2. PatientVisit Table (x=50, y=92, w=40, h=43)
    pv_sections = [
        {
            'title': 'PLAINTEXT METADATA & RELATIONAL KEYS', 'bg': '#e0f2fe', 'fg': '#0369a1',
            'fields': [
                {'badge': 'PK', 'bbg': '#0284c7', 'bfg': 'white', 'name': 'id', 'dtype': 'UUID'},
                {'badge': 'FK', 'bbg': '#059669', 'bfg': 'white', 'name': 'patient_id', 'dtype': 'UUID', 'note': 'References patients (1 Patient -> N Visits)'},
                {'badge': 'FK', 'bbg': '#64748b', 'bfg': 'white', 'name': 'encryption_key_id', 'dtype': 'UUID', 'note': 'References encryption_keys (KEK_visit)'},
                {'badge': 'SYS', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'owner_id', 'dtype': 'UUID', 'note': 'Attending clinician tenant ID'},
            ]
        },
        {
            'title': 'ZERO-PLAINTEXT ENCRYPTED CLINICAL VISIT', 'bg': '#fee2e2', 'fg': '#b91c1c',
            'fields': [
                {'badge': 'ENC', 'bbg': '#dc2626', 'bfg': 'white', 'name': 'encrypted_data_blob', 'dtype': 'TEXT', 'note': 'chiefComplaint, diagnosis, vitals, soapNotes'},
            ]
        },
        {
            'title': 'STATUTORY RETENTION & AUDIT PROOF', 'bg': '#ede9fe', 'fg': '#6d28d9',
            'fields': [
                {'badge': 'DEL', 'bbg': '#ef4444', 'bfg': 'white', 'name': 'shredded', 'dtype': 'BOOLEAN', 'note': 'Encounter-level crypto-shredding'},
                {'badge': 'PRF', 'bbg': '#7c3aed', 'bfg': 'white', 'name': 'deletion_proof_json', 'dtype': 'TEXT', 'note': 'Encounter erasure certificate C_erasure'},
                {'badge': 'TS', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'created_at, updated_at', 'dtype': 'TIMESTAMP'},
            ]
        }
    ]
    draw_entity_card(50, 92, 40, 43, 'PatientVisit', 'patient_visits (Clinical Encounters)', '#0284c7', pv_sections)

    # 3. PatientAttachment Table (x=96, y=92, w=39, h=43)
    pa_sections = [
        {
            'title': 'METADATA & BINARY REFERENCE', 'bg': '#e0f2fe', 'fg': '#0369a1',
            'fields': [
                {'badge': 'PK', 'bbg': '#0284c7', 'bfg': 'white', 'name': 'id', 'dtype': 'UUID'},
                {'badge': 'FK', 'bbg': '#0284c7', 'bfg': 'white', 'name': 'patient_visit_id', 'dtype': 'UUID', 'note': 'References patient_visits (1 Visit -> N Attach)'},
                {'badge': 'META', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'file_name', 'dtype': 'VARCHAR(255)', 'note': 'Technical UUID file identifier'},
                {'badge': 'META', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'content_type', 'dtype': 'VARCHAR(100)', 'note': 'MIME (application/pdf, image/dicom)'},
                {'badge': 'META', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'file_size', 'dtype': 'BIGINT', 'note': 'Payload byte size in storage'},
                {'badge': 'IV', 'bbg': '#fde68a', 'bfg': '#78350f', 'name': 'iv', 'dtype': 'VARCHAR(64)', 'note': '96-bit AES-GCM Initialization Vector'},
            ]
        },
        {
            'title': 'ENCRYPTED BINARY BLOB (AES-256-GCM)', 'bg': '#fee2e2', 'fg': '#b91c1c',
            'fields': [
                {'badge': 'ENC', 'bbg': '#dc2626', 'bfg': 'white', 'name': 'encrypted_data_blob', 'dtype': 'TEXT', 'note': 'Base64 ciphertext of clinical scan / PDF'},
                {'badge': 'DEL', 'bbg': '#ef4444', 'bfg': 'white', 'name': 'shredded', 'dtype': 'BOOLEAN', 'note': 'Cascade-shredded with parent visit KEK'},
                {'badge': 'TS', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'created_at', 'dtype': 'TIMESTAMP'},
            ]
        }
    ]
    draw_entity_card(96, 92, 39, 43, 'PatientAttachment', 'patient_attachments (Diagnostic Scans)', '#b91c1c', pa_sections)

    # Connections in Top Row
    ax.annotate('', xy=(50, 78), xytext=(44, 78),
                arrowprops=dict(arrowstyle='->,head_width=4,head_length=5', color='#0284c7', linewidth=2.0))
    ax.text(47, 80.0, '1 .. *', fontsize=8.0, fontweight='bold', color='#0284c7', ha='center')

    ax.annotate('', xy=(96, 78), xytext=(90, 78),
                arrowprops=dict(arrowstyle='->,head_width=4,head_length=5', color='#b91c1c', linewidth=2.0))
    ax.text(93, 80.0, '1 .. *', fontsize=8.0, fontweight='bold', color='#b91c1c', ha='center')

    # Bottom Row
    ek_sections = [
        {
            'title': 'KEY METADATA & ENVELOPE ENCRYPTION WRAPPING', 'bg': '#fef3c7', 'fg': '#b45309',
            'fields': [
                {'badge': 'PK', 'bbg': '#0284c7', 'bfg': 'white', 'name': 'id', 'dtype': 'UUID'},
                {'badge': 'UK', 'bbg': '#38bdf8', 'bfg': '#0c4a6e', 'name': 'key_id', 'dtype': 'VARCHAR(128)', 'note': 'Logical unique identifier (UUID)'},
                {'badge': 'KMS', 'bbg': '#d97706', 'bfg': 'white', 'name': 'vault_key_name', 'dtype': 'VARCHAR(255)', 'note': 'transit/keys/patient_{uuid} or patient_{uuid}_visit_{uuid}'},
                {'badge': 'WRAP', 'bbg': '#f59e0b', 'bfg': 'white', 'name': 'wrapped_dek', 'dtype': 'TEXT', 'note': '256-bit AES DEK wrapped inside Vault Transit under master KEK'},
                {'badge': 'IV', 'bbg': '#fde68a', 'bfg': '#78350f', 'name': 'iv', 'dtype': 'VARCHAR(64)', 'note': '96-bit AES-GCM Initialization Vector'},
            ]
        },
        {
            'title': 'LIFECYCLE & ATOMIC REVOCATION AUDIT', 'bg': '#fee2e2', 'fg': '#b91c1c',
            'fields': [
                {'badge': 'DEL', 'bbg': '#ef4444', 'bfg': 'white', 'name': 'invalidated', 'dtype': 'BOOLEAN', 'note': 'true = Vault KEK permanently destroyed'},
                {'badge': 'TS', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'invalidated_at, rotated_at, created_at', 'dtype': 'TIMESTAMP'},
            ]
        }
    ]
    draw_entity_card(5, 42, 62, 33, 'EncryptionKey', 'encryption_keys (Vault Transit Envelopes)', '#d97706', ek_sections)

    mn_sections = [
        {
            'title': 'MERKLE AUDIT TRAIL & TAMPER-EVIDENT ACCUMULATOR', 'bg': '#ede9fe', 'fg': '#6d28d9',
            'fields': [
                {'badge': 'PK', 'bbg': '#4f46e5', 'bfg': 'white', 'name': 'id', 'dtype': 'UUID'},
                {'badge': 'UK', 'bbg': '#818cf8', 'bfg': 'white', 'name': 'leaf_index', 'dtype': 'INTEGER', 'note': 'Strict sequential leaf index: 0 .. N-1 in Binary Merkle Tree'},
                {'badge': 'DAG', 'bbg': '#6d28d9', 'bfg': 'white', 'name': 'leaf_hash', 'dtype': 'VARCHAR(64)', 'note': 'H_leaf = SHA-256(EntityID || Timestamp || S_ret || R_ovr || SHREDDED)'},
                {'badge': 'TS', 'bbg': '#e2e8f0', 'bfg': '#334155', 'name': 'created_at', 'dtype': 'TIMESTAMP', 'note': 'Immutable consensus insertion timestamp'},
            ]
        },
        {
            'title': 'MATHEMATICAL PROOF VERIFICATION INTERACTION', 'bg': '#e0e7ff', 'fg': '#3730a3',
            'fields': [
                {'badge': 'PROOF', 'bbg': '#4338ca', 'bfg': 'white', 'name': 'Inclusion Path', 'dtype': 'log2(N) Hashes', 'note': 'Directional sibling hashes {S_1, S_2, ...} reconstructing Root'},
                {'badge': 'PQC', 'bbg': '#312e81', 'bfg': 'white', 'name': 'Dual Signatures', 'dtype': 'RSA + ML-DSA-65', 'note': 'FIPS 204 post-quantum signature guarantees long-term non-repudiation'},
            ]
        }
    ]
    draw_entity_card(73, 42, 62, 33, 'MerkleNode', 'merkle_nodes (Persistent Audit DAG)', '#4f46e5', mn_sections)

    # Connections between rows
    ax.annotate('', xy=(18, 42), xytext=(18, 49),
                arrowprops=dict(arrowstyle='->,head_width=4,head_length=5', color='#d97706', linewidth=1.8))
    ax.text(19.2, 45.5, '1 .. 1 (KEK_pat)', fontsize=7.2, fontweight='bold', color='#d97706')

    ax.annotate('', xy=(56, 42), xytext=(56, 49),
                arrowprops=dict(arrowstyle='->,head_width=4,head_length=5', color='#d97706', linewidth=1.8))
    ax.text(57.2, 45.5, '1 .. 1 (KEK_vis)', fontsize=7.2, fontweight='bold', color='#d97706')

    ax.annotate('', xy=(85, 42), xytext=(85, 49),
                arrowprops=dict(arrowstyle='->,head_width=4,head_length=5', color='#4f46e5', linewidth=1.8, linestyle='--'))
    ax.text(86.2, 45.5, 'H_leaf Audit Link', fontsize=7.2, fontweight='bold', color='#4f46e5')

    # Bottom Legend
    legend_box = patches.FancyBboxPatch((5, 1.5), 130, 4.5, boxstyle='round,pad=0.2,rounding_size=0.5',
                                       fc='#f8fafc', ec='#94a3b8', linewidth=1.0, zorder=1)
    ax.add_patch(legend_box)
    ax.text(7, 3.75, 'SECURITY TAXONOMY:', fontsize=7.5, fontweight='bold', color='#0f172a')

    badges = [
        {'b': 'PK/FK', 'bg': '#0284c7', 'fg': 'white', 'desc': 'Plaintext Relational Keys & Metadata', 'x': 27},
        {'b': 'IDX', 'bg': '#f59e0b', 'fg': 'white', 'desc': 'Salted HMAC Blind Indexes (O(1) Exact Search)', 'x': 59},
        {'b': 'ENC', 'bg': '#dc2626', 'fg': 'white', 'desc': 'Zero-Plaintext Encrypted Payload (AES-256-GCM)', 'x': 92},
        {'b': 'PRF', 'bg': '#7c3aed', 'fg': 'white', 'desc': 'Merkle Tree Proof (Dual RSA + ML-DSA-65)', 'x': 124},
    ]

    for b in badges:
        bx = b['x']
        bp = patches.FancyBboxPatch((bx - 1.5, 2.9), 3.4, 1.7, boxstyle='round,pad=0.1,rounding_size=0.3',
                                    fc=b['bg'], ec='none', zorder=2)
        ax.add_patch(bp)
        ax.text(bx + 0.2, 3.75, b['b'], ha='center', va='center', fontsize=5.8, fontweight='bold', color=b['fg'], zorder=3)
        ax.text(bx + 2.4, 3.75, b['desc'], ha='left', va='center', fontsize=6.3, color='#334155', zorder=3)

    out_path = os.path.join(FIGURES_DIR, 'fig_er_model.pdf')
    plt.savefig(out_path, format='pdf', bbox_inches='tight')
    plt.close()
    print(f"  [OK] Generated Figure 3.4 saved to: {out_path}")


def generate_figure_3_5():
    """
    Figure 3.5 (thesis/figures/fig_merkle_path.pdf):
    Binary Merkle Tree showing leaf hashing (H0..H7), pairwise internal node hashing,
    and the inclusion proof path (audit trail) leading to the Root Hash.
    """
    fig, ax = plt.subplots(figsize=(13.5, 9.8), dpi=300)
    ax.set_xlim(0, 135)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(67.5, 97.8, 'Binary Merkle Tree Audit Trail & Inclusion Proof Path',
            ha='center', va='top', fontsize=13.5, fontweight='bold', color='#0f172a')
    ax.text(67.5, 95.2, 'Logarithmic Directional Sibling Accumulation for Verifiable GDPR Article 17 Erasure',
            ha='center', va='top', fontsize=9.2, style='italic', color='#475569')

    leaf_xs = [8, 20, 32, 44, 58, 70, 82, 94]
    l1_xs = [(leaf_xs[0]+leaf_xs[1])/2, (leaf_xs[2]+leaf_xs[3])/2, (leaf_xs[4]+leaf_xs[5])/2, (leaf_xs[6]+leaf_xs[7])/2]
    l2_xs = [(l1_xs[0]+l1_xs[1])/2, (l1_xs[2]+l1_xs[3])/2]
    root_x = (l2_xs[0]+l2_xs[1])/2

    node_w = 9.4
    node_h = 4.2

    def draw_tree_node(x, y, label, sub='', status='neutral'):
        if status == 'target':
            fc = '#d1fae5'; ec = '#059669'; tc = '#065f46'; lw = 2.0
        elif status == 'proof':
            fc = '#fef3c7'; ec = '#d97706'; tc = '#92400e'; lw = 2.0
        elif status == 'path':
            fc = '#e0f2fe'; ec = '#0284c7'; tc = '#0369a1'; lw = 2.0
        elif status == 'root':
            fc = '#ede9fe'; ec = '#7c3aed'; tc = '#5b21b6'; lw = 2.2
        else:
            fc = '#f8fafc'; ec = '#94a3b8'; tc = '#475569'; lw = 1.0
            
        box = patches.FancyBboxPatch((x - node_w/2, y - node_h/2), node_w, node_h,
                                    boxstyle='round,pad=0.2,rounding_size=0.6',
                                    fc=fc, ec=ec, linewidth=lw, zorder=3)
        ax.add_patch(box)
        
        if sub:
            ax.text(x, y + 0.65, label, ha='center', va='center', fontsize=8.2, fontweight='bold', color=tc, zorder=4)
            ax.text(x, y - 0.95, sub, ha='center', va='center', fontsize=6.2, style='italic', color=tc, zorder=4)
        else:
            ax.text(x, y, label, ha='center', va='center', fontsize=8.5, fontweight='bold', color=tc, zorder=4)

    def draw_edge(x1, y1, x2, y2, is_proof=False, is_path=False):
        if is_path:
            c = '#0284c7'; lw = 2.4; ls = '-'; zo = 2
        elif is_proof:
            c = '#d97706'; lw = 2.2; ls = '--'; zo = 2
        else:
            c = '#cbd5e1'; lw = 1.0; ls = '-'; zo = 1
        ax.plot([x1, x2], [y1, y2], color=c, linewidth=lw, linestyle=ls, zorder=zo)

    for i in range(8):
        p_idx = i // 2
        is_path = (i == 2)
        is_proof = (i == 3)
        draw_edge(leaf_xs[i], 23 + node_h/2, l1_xs[p_idx], 39 - node_h/2, is_proof=is_proof, is_path=is_path)

    for i in range(4):
        p_idx = i // 2
        is_path = (i == 1)
        is_proof = (i == 0)
        draw_edge(l1_xs[i], 39 + node_h/2, l2_xs[p_idx], 55 - node_h/2, is_proof=is_proof, is_path=is_path)

    draw_edge(l2_xs[0], 55 + node_h/2, root_x, 71 - node_h/2, is_path=True)
    draw_edge(l2_xs[1], 55 + node_h/2, root_x, 71 - node_h/2, is_proof=True)

    # Leaves
    draw_tree_node(leaf_xs[0], 23, 'H0', 'Leaf 0', 'neutral')
    draw_tree_node(leaf_xs[1], 23, 'H1', 'Leaf 1', 'neutral')
    draw_tree_node(leaf_xs[2], 23, 'H2', 'Target Leaf', 'target')
    draw_tree_node(leaf_xs[3], 23, 'H3', 'Proof #1 (R)', 'proof')
    draw_tree_node(leaf_xs[4], 23, 'H4', 'Leaf 4', 'neutral')
    draw_tree_node(leaf_xs[5], 23, 'H5', 'Leaf 5', 'neutral')
    draw_tree_node(leaf_xs[6], 23, 'H6', 'Leaf 6', 'neutral')
    draw_tree_node(leaf_xs[7], 23, 'H7', 'Leaf 7', 'neutral')

    # Level 1 Nodes
    draw_tree_node(l1_xs[0], 39, 'H01', 'Proof #2 (L)', 'proof')
    draw_tree_node(l1_xs[1], 39, 'H23', 'Hash(H2, H3)', 'path')
    draw_tree_node(l1_xs[2], 39, 'H45', 'Hash(H4, H5)', 'neutral')
    draw_tree_node(l1_xs[3], 39, 'H67', 'Hash(H6, H7)', 'neutral')

    # Level 2 Nodes
    draw_tree_node(l2_xs[0], 55, 'H0123', 'Hash(H01, H23)', 'path')
    draw_tree_node(l2_xs[1], 55, 'H4567', 'Proof #3 (R)', 'proof')

    # Level 3 Root Node
    draw_tree_node(root_x, 71, 'H_root', 'Root Commitment', 'root')

    # Leaf 2 Metadata Input Box
    meta_box = patches.FancyBboxPatch((12, 7.5), 44, 7.5, boxstyle='round,pad=0.2,rounding_size=0.6',
                                     fc='#ecfdf5', ec='#059669', linewidth=1.5, zorder=3)
    ax.add_patch(meta_box)
    ax.annotate('', xy=(leaf_xs[2], 23 - node_h/2), xytext=(34, 15.0),
                arrowprops=dict(arrowstyle='->,head_width=4,head_length=5', color='#059669', linewidth=1.8))
    ax.text(34, 13.0, 'Target Deletion Metadata Digest (Leaf i = 2)', ha='center', va='center', fontsize=7.8, fontweight='bold', color='#065f46')
    ax.text(34, 10.0, 'H2 = SHA-256( EntityID || Timestamp || S_retention || R_override || SHREDDED )',
            ha='center', va='center', fontsize=6.8, fontweight='semibold', color='#047857')

    # Dual Signature Seal Box
    seal_box = patches.FancyBboxPatch((root_x - 22, 78.5), 44, 7.5, boxstyle='round,pad=0.2,rounding_size=0.6',
                                     fc='#f5f3ff', ec='#7c3aed', linewidth=1.5, zorder=3)
    ax.add_patch(seal_box)
    ax.plot([root_x, root_x], [78.5, 71 + node_h/2], color='#7c3aed', linewidth=2.0, linestyle=':', zorder=2)
    ax.text(root_x, 83.8, 'Dual-Signed Deletion Certificate Commitment', ha='center', va='center', fontsize=8.2, fontweight='bold', color='#5b21b6')
    ax.text(root_x, 81.5, r'$\sigma_{\mathrm{hybrid}} = (\sigma_{\mathrm{RSA-2048}},\; \sigma_{\mathrm{ML-DSA-65}})$ evaluated over $m_{\mathrm{canonical}}$',
            ha='center', va='center', fontsize=7.5, color='#6d28d9')
    ax.text(root_x, 79.5, 'Canonical payload binds {EntityID, Timestamp, H2, H_root}',
            ha='center', va='center', fontsize=6.3, style='italic', color='#7c3aed')

    # Right Verification Box
    info_box = patches.FancyBboxPatch((104, 7.5), 27, 80, boxstyle='round,pad=0.2,rounding_size=0.6',
                                     fc='#f8fafc', ec='#64748b', linewidth=1.2, zorder=2)
    ax.add_patch(info_box)
    ax.text(117.5, 84.5, 'Verification Protocol', ha='center', va='center', fontsize=9.2, fontweight='bold', color='#0f172a')
    ax.text(117.5, 82.5, 'Directional Inclusion Path', ha='center', va='center', fontsize=7.5, style='italic', color='#475569')

    steps_text = [
        ('1. Target Leaf Input:', '#059669', True),
        ('Index: i = 2', '#0f172a', False),
        ('Leaf Hash: H2 (computed)', '#047857', False),
        ('', '', False),
        ('2. Sibling Path Elements P:', '#d97706', True),
        ('P = { S1: H3 (Right),', '#b45309', False),
        ('      S2: H01 (Left),', '#b45309', False),
        ('      S3: H4567 (Right) }', '#b45309', False),
        ('|P| = log2(8) = 3 hashes', '#64748b', False),
        ('Path payload: 3 x 32 = 96 B', '#64748b', False),
        ('', '', False),
        ('3. Directional Traversal:', '#0284c7', True),
        ('Level 1: (Right Sibling S1)', '#64748b', False),
        ('H23 = SHA-256(H2 || H3)', '#0369a1', False),
        ('Level 2: (Left Sibling S2)', '#64748b', False),
        ('H0123 = SHA-256(H01 || H23)', '#0369a1', False),
        ('Level 3: (Right Sibling S3)', '#64748b', False),
        ('H* = SHA-256(H0123 || H4567)', '#0369a1', False),
        ('', '', False),
        ('4. Root Equivalence:', '#7c3aed', True),
        ('H* == H_root  ->  VALID', '#5b21b6', True),
        ('Zero patient data revealed', '#047857', False),
        ('', '', False),
        ('5. Dual Signature Check:', '#7c3aed', True),
        ('Verify Vault RSA-2048', '#5b21b6', False),
        ('Verify FIPS 204 ML-DSA-65', '#5b21b6', False),
        ('Total execution < 1 ms', '#047857', True),
    ]

    sy = 79.5
    for line, col, is_bold in steps_text:
        if line:
            fw = 'bold' if is_bold else 'normal'
            ax.text(106, sy, line, ha='left', va='center', fontsize=6.8, fontweight=fw, color=col)
        sy -= 2.65

    # Bottom Legend
    leg_box = patches.FancyBboxPatch((8, 1.2), 92, 4.8, boxstyle='round,pad=0.2,rounding_size=0.5',
                                    fc='#f8fafc', ec='#94a3b8', linewidth=1.0, zorder=1)
    ax.add_patch(leg_box)
    ax.text(10, 3.6, 'MERKLE TREE VISUAL LEGEND:', fontsize=7.2, fontweight='bold', color='#0f172a')

    leg_items = [
        ('Target Leaf (H2)', '#d1fae5', '#059669', 34),
        ('Proof Sibling P', '#fef3c7', '#d97706', 50),
        ('Reconstruction Path', '#e0f2fe', '#0284c7', 68),
        ('Root Commitment', '#ede9fe', '#7c3aed', 87),
    ]

    for label, lfc, lec, lx in leg_items:
        lp = patches.FancyBboxPatch((lx, 2.2), 3.4, 2.6, boxstyle='round,pad=0.1,rounding_size=0.3',
                                   fc=lfc, ec=lec, linewidth=1.5, zorder=2)
        ax.add_patch(lp)
        ax.text(lx + 4.2, 3.6, label, ha='left', va='center', fontsize=6.5, color='#1e293b')

    out_path = os.path.join(FIGURES_DIR, 'fig_merkle_path.pdf')
    plt.savefig(out_path, format='pdf', bbox_inches='tight')
    plt.close()
    print(f"  [OK] Generated Figure 3.5 saved to: {out_path}")


def generate_figure_5_3():
    """
    Figure 5.3 (thesis/figures/fig_macro_concurrency_throughput.pdf):
    Macro System Throughput Across Concurrency Tiers (1 to 500 VUs).
    Ensures the legend order is strictly numerical:
    1: Scenario 1: Encrypted Reads
    2: Scenario 2: Encrypted Ingestion
    3: Scenario 3: Crypto-Shredding
    4: Scenario 4: Post-Shred Read Rejection
    """
    plt.rcParams.update({
        'font.family': 'serif',
        'font.serif': ['DejaVu Serif', 'Times New Roman', 'Computer Modern Roman'],
        'font.size': 11,
        'axes.labelsize': 11,
        'axes.titlesize': 12,
        'xtick.labelsize': 10,
        'ytick.labelsize': 10,
        'legend.fontsize': 9.5,
        'mathtext.fontset': 'cm',
        'figure.autolayout': True,
    })

    vus = [1, 10, 50, 100, 250, 500]
    s1_rps = np.array([854.2, 8124.6, 32890.1, 51240.8, 79350.4, 96820.5]) / 1000.0
    s2_rps = np.array([134.8, 1265.4, 5110.2, 8190.5, 12870.0, 14890.2]) / 1000.0
    s3_rps = np.array([101.5, 958.2, 3980.4, 6540.8, 10610.1, 13420.7]) / 1000.0
    s4_rps = np.array([689.7, 6451.6, 27472.5, 44444.4, 69444.4, 89285.7]) / 1000.0

    fig, ax = plt.subplots(figsize=(7.5, 4.2), dpi=300)

    # Strictly numerical legend order: Scenario 1, 2, 3, 4
    ax.plot(vus, s1_rps, marker='o', color='#2563eb', linewidth=2.2, markersize=6.5,
            label='Scenario 1: Encrypted Reads (Redis L2 Hit/Miss)')
    ax.plot(vus, s2_rps, marker='s', color='#d97706', linewidth=2.2, markersize=6.5,
            label='Scenario 2: Encrypted Ingestion (DEK Wrap + Kafka)')
    ax.plot(vus, s3_rps, marker='d', color='#dc2626', linewidth=2.2, markersize=6.5,
            label='Scenario 3: Crypto-Shredding + Merkle + PQC Signature')
    ax.plot(vus, s4_rps, marker='^', color='#059669', linewidth=2.2, markersize=6.5,
            label='Scenario 4: Post-Shred Read Rejection (Zero-Leakage)')

    ax.set_title('Macro System Throughput Across Concurrency Tiers (1 to 500 VUs)', pad=10)
    ax.set_xlabel('Concurrent Virtual Users (VUs)', labelpad=6)
    ax.set_ylabel(r'Throughput ($\times 10^3$ Requests/sec)', labelpad=6)

    ax.set_xlim(0, 520)
    ax.set_ylim(0, 105)
    ax.set_xticks([0, 100, 200, 300, 400, 500])
    ax.set_yticks([0, 20, 40, 60, 80, 100])

    ax.grid(True, linestyle='--', alpha=0.6)
    ax.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.95)

    out_path = os.path.join(FIGURES_DIR, 'fig_macro_concurrency_throughput.pdf')
    plt.savefig(out_path, format='pdf', bbox_inches='tight')
    plt.close()
    print(f"  [OK] Generated Figure 5.3 saved to: {out_path}")


def main():
    parser = argparse.ArgumentParser(description="Publication Figure Generator for CryptoShred Health")
    parser.add_argument("--all", action="store_true", help="Generate and calibrate all figures")
    parser.add_argument("--fig32", action="store_true", help="Calibrate Figure 3.2 (Vault Raft HA)")
    parser.add_argument("--fig33", action="store_true", help="Generate Figure 3.3 (Erasure Sequence)")
    parser.add_argument("--fig34", action="store_true", help="Generate Figure 3.4 (ER Model)")
    parser.add_argument("--fig35", action="store_true", help="Generate Figure 3.5 (Merkle Path)")
    parser.add_argument("--fig53", action="store_true", help="Generate Figure 5.3 (Macro Throughput)")

    args = parser.parse_args()

    # If no flags specified, default to --all
    run_all = args.all or not (args.fig32 or args.fig33 or args.fig34 or args.fig35 or args.fig53)

    print("================================================================================")
    print("      CryptoShred Health Publication Visual Diagram Generator (Cap 3 & 5)      ")
    print("================================================================================")

    if run_all or args.fig32:
        print("\n1. Calibrating Figure 3.2 (fig_vault_raft.pdf)...")
        calibrate_figure_3_2()

    if run_all or args.fig33:
        print("\n2. Generating Figure 3.3 (fig_erasure_sequence.pdf)...")
        generate_figure_3_3()

    if run_all or args.fig34:
        print("\n3. Generating Figure 3.4 (fig_er_model.pdf)...")
        generate_figure_3_4()

    if run_all or args.fig35:
        print("\n4. Generating Figure 3.5 (fig_merkle_path.pdf)...")
        generate_figure_3_5()

    if run_all or args.fig53:
        print("\n5. Generating Figure 5.3 (fig_macro_concurrency_throughput.pdf)...")
        generate_figure_5_3()

    print("\n[SUCCESS] All requested publication figures processed successfully!")


if __name__ == "__main__":
    main()
