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
            'desc': 'Offline O(log N) proof verification',
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



def generate_figure_5_1():
    """
    Figure 5.1 (thesis/figures/fig_deletion_scaling.pdf):
    GDPR Article 17 Erasure Performance Comparison:
    (a) Raw execution time in milliseconds: Vault KMS O(1) vs PostgreSQL O(N).
    (b) Log-log scaling highlighting O(1) constant time vs linear O(N) database and memory deletion.
    """
    plt.rcParams.update({
        'font.family': 'serif',
        'font.serif': ['DejaVu Serif', 'Times New Roman', 'Computer Modern Roman'],
        'font.size': 10.5,
        'axes.labelsize': 10.5,
        'axes.titlesize': 11,
        'xtick.labelsize': 9.5,
        'ytick.labelsize': 9.5,
        'legend.fontsize': 9.0,
        'mathtext.fontset': 'cm',
        'figure.autolayout': True,
    })

    records = [10, 100, 1000, 5000, 10000]
    vault_ms = [6.432, 7.649, 6.230, 6.814, 5.629]
    postgres_ms = [27.608, 25.190, 35.069, 34.855, 39.121]
    inmem_ms = [0.000047, 0.000231, 0.003549, 0.016358, 0.041748]
    h2_ms = [0.014685, 0.014978, 0.013803, 0.013419, 0.036556]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10.5, 4.4), dpi=300)

    # Subplot (a): Raw Execution Latency vs Record Count (Linear scale)
    ax1.plot(records, postgres_ms, marker='s', color='#dc2626', linewidth=2.0, markersize=6,
             label=r'PostgreSQL Direct SQL $\mathcal{O}(N)$')
    ax1.plot(records, vault_ms, marker='o', color='#2563eb', linewidth=2.0, markersize=6,
             label=r'Vault Transit KMS $\mathcal{O}(1)$')

    ax1.set_title('(a) Absolute Erasure Latency vs Record Count ($N$)', pad=8)
    ax1.set_xlabel('Historical Records per Patient ($N$)', labelpad=6)
    ax1.set_ylabel('Execution Latency (ms)', labelpad=6)
    ax1.set_xlim(-200, 10500)
    ax1.set_ylim(0, 45)
    ax1.grid(True, linestyle='--', alpha=0.55)
    ax1.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.92)

    # Subplot (b): Log-Log Algorithmic Complexity Curve
    ax2.loglog(records, postgres_ms, marker='s', color='#dc2626', linewidth=1.8, markersize=5.5,
               label='PostgreSQL Indexed SQL')
    ax2.loglog(records, vault_ms, marker='o', color='#2563eb', linewidth=2.0, markersize=5.5,
               label=r'Vault KMS KEK Destruction $\mathcal{O}(1)$')
    ax2.loglog(records, h2_ms, marker='d', color='#059669', linewidth=1.8, markersize=5.5,
               label='Embedded H2 JDBC')
    ax2.loglog(records, inmem_ms, marker='^', color='#d97706', linewidth=1.8, markersize=5.5,
               label=r'In-Memory Array $\mathcal{O}(N)$')

    ax2.set_title('(b) Log-Log Algorithmic Complexity Curve', pad=8)
    ax2.set_xlabel('Historical Records per Patient ($N$)', labelpad=6)
    ax2.set_ylabel('Execution Latency (ms) [log scale]', labelpad=6)
    ax2.grid(True, linestyle='--', which='both', alpha=0.55)
    ax2.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.92)

    out_path = os.path.join(FIGURES_DIR, 'fig_deletion_scaling.pdf')
    plt.savefig(out_path, format='pdf', bbox_inches='tight')
    plt.close()
    print(f"  [OK] Generated Figure 5.1 saved to: {out_path}")


def generate_figure_5_2():
    """
    Figure 5.2 (thesis/figures/fig_merkle_scaling.pdf):
    Merkle DAG Audit Trail Scalability:
    (a) Logarithmic proof generation & verification scaling and constant-time leaf insertion.
    (b) Full tree root reconstruction latency across cumulative deletion records.
    """
    plt.rcParams.update({
        'font.family': 'serif',
        'font.serif': ['DejaVu Serif', 'Times New Roman', 'Computer Modern Roman'],
        'font.size': 10.5,
        'axes.labelsize': 10.5,
        'axes.titlesize': 11,
        'xtick.labelsize': 9.5,
        'ytick.labelsize': 9.5,
        'legend.fontsize': 9.0,
        'mathtext.fontset': 'cm',
        'figure.autolayout': True,
    })

    leaves = [100, 1000, 10000, 100000]
    proof_ms = [2.846, 33.197, 317.060, 2477.516]
    root_ms = [2.203, 14.168, 126.133, 2098.292]
    leaf_us = [0.174, 0.179, 0.157, 0.133]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10.5, 4.4), dpi=300)

    # Subplot (a): Dynamic Proof Path Generation & Root Verify
    ax1.loglog(leaves, proof_ms, marker='s', color='#7c3aed', linewidth=2.0, markersize=6,
               label='Dynamic Path Gen & Root Verify (ms)')
    
    # Twin axis for leaf insertion in microseconds
    ax1_twin = ax1.twinx()
    ax1_twin.semilogx(leaves, leaf_us, marker='o', color='#059669', linewidth=1.8, linestyle='--', markersize=6,
                      label=r'Leaf Insertion ($\mu$s)')
    ax1_twin.set_ylabel(r'Leaf Insertion Time ($\mu$s)', color='#059669', labelpad=6)
    ax1_twin.tick_params(axis='y', labelcolor='#059669')
    ax1_twin.set_ylim(0, 0.35)

    ax1.set_title(r'(a) Proof Path Gen \& Leaf Insertion ($\mathcal{O}(1)$)', pad=8)
    ax1.set_xlabel('Cumulative Deletion Records ($N$)', labelpad=6)
    ax1.set_ylabel('Path Gen & Verify (ms) [log scale]', color='#7c3aed', labelpad=6)
    ax1.tick_params(axis='y', labelcolor='#7c3aed')
    ax1.grid(True, linestyle='--', which='both', alpha=0.55)
    ax1.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.92)

    # Subplot (b): Root Reconstruction Latency
    ax2.loglog(leaves, root_ms, marker='d', color='#d97706', linewidth=2.0, markersize=6,
               label='Full Root Recomputation')
    ax2.set_title('(b) Full Tree Root Reconstruction Latency', pad=8)
    ax2.set_xlabel('Cumulative Deletion Records ($N$)', labelpad=6)
    ax2.set_ylabel('Root Computation Latency (ms) [log scale]', labelpad=6)
    ax2.grid(True, linestyle='--', which='both', alpha=0.55)
    ax2.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.92)

    out_path = os.path.join(FIGURES_DIR, 'fig_merkle_scaling.pdf')
    plt.savefig(out_path, format='pdf', bbox_inches='tight')
    plt.close()
    print(f"  [OK] Generated Figure 5.2 saved to: {out_path}")


def generate_figure_5_3():
    """
    Figure 5.3 (thesis/figures/fig_macro_concurrency_throughput.pdf):
    Macro System Throughput Across Concurrency Tiers (1 to 500 VUs).
    Ingests measured results from metrics.csv.
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
    # Measured values from benchmarks/macro-system/results/metrics.csv
    s1_rps = np.array([37.79, 179.88, 197.45, 167.73, 193.16, 153.75])
    s2_rps = np.array([15.45, 69.80, 52.94, 60.31, 78.95, 67.69])
    s3_rps = np.array([6.18, 6.23, 6.88, 7.99, 12.78, 23.43])
    s4_rps = np.array([0.07, 0.67, 3.54, 546.65, 571.58, 557.36])

    fig, ax = plt.subplots(figsize=(7.5, 4.2), dpi=300)

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
    ax.set_ylabel('Throughput (Requests/sec)', labelpad=6)

    ax.set_xlim(0, 520)
    ax.set_ylim(0, 620)
    ax.set_xticks([0, 100, 200, 300, 400, 500])

    ax.grid(True, linestyle='--', alpha=0.6)
    ax.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.95)

    out_path = os.path.join(FIGURES_DIR, 'fig_macro_concurrency_throughput.pdf')
    plt.savefig(out_path, format='pdf', bbox_inches='tight')
    plt.close()
    print(f"  [OK] Generated Figure 5.3 saved to: {out_path}")


def generate_figure_5_4():
    """
    Figure 5.4 (thesis/figures/fig_macro_latency_percentiles.pdf):
    Multi-User Response Latency Percentiles (p50, p90, p95, p99):
    (a) Scenario 1 Encrypted Reads
    (b) Scenario 2 Encrypted Ingestion Pipeline
    """
    plt.rcParams.update({
        'font.family': 'serif',
        'font.serif': ['DejaVu Serif', 'Times New Roman', 'Computer Modern Roman'],
        'font.size': 10.5,
        'axes.labelsize': 10.5,
        'axes.titlesize': 11,
        'xtick.labelsize': 9.5,
        'ytick.labelsize': 9.5,
        'legend.fontsize': 9.0,
        'mathtext.fontset': 'cm',
        'figure.autolayout': True,
    })

    vus = [1, 10, 50, 100, 250, 500]

    # Scenario 1 percentiles (from metrics.csv)
    s1_p50 = [23.892, 49.870, 241.901, 508.893, 1255.935, 3043.187]
    s1_p90 = [33.311, 91.643, 358.289, 943.202, 1805.047, 3901.544]
    s1_p95 = [35.056, 103.298, 445.876, 1147.591, 2136.783, 4086.597]
    s1_p99 = [92.338, 152.030, 545.144, 1443.663, 2443.948, 4701.662]

    # Scenario 2 percentiles (from metrics.csv)
    s2_p50 = [54.356, 135.583, 844.506, 1503.404, 2993.552, 6057.198]
    s2_p90 = [91.945, 206.163, 1479.785, 2047.468, 3439.747, 8154.042]
    s2_p95 = [103.150, 246.288, 1632.961, 2210.049, 4018.259, 8960.836]
    s2_p99 = [152.005, 305.175, 2021.703, 2947.651, 4304.042, 9826.771]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10.5, 4.4), dpi=300)

    # Subplot (a): Scenario 1
    ax1.plot(vus, s1_p50, marker='o', color='#2563eb', linewidth=1.8, markersize=5.5, label=r'Median ($p_{50}$)')
    ax1.plot(vus, s1_p90, marker='s', color='#059669', linewidth=1.8, markersize=5.5, label=r'$p_{90}$')
    ax1.plot(vus, s1_p95, marker='d', color='#d97706', linewidth=1.8, markersize=5.5, label=r'$p_{95}$')
    ax1.plot(vus, s1_p99, marker='^', color='#dc2626', linewidth=2.0, markersize=5.5, label=r'$p_{99}$ (Tail)')

    ax1.set_title('(a) Scenario 1: Encrypted Read Latency (Redis L2)', pad=8)
    ax1.set_xlabel('Concurrent Virtual Users (VUs)', labelpad=6)
    ax1.set_ylabel('Response Latency (ms)', labelpad=6)
    ax1.set_xlim(0, 520)
    ax1.set_ylim(0, 5200)
    ax1.grid(True, linestyle='--', alpha=0.55)
    ax1.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.92)

    # Subplot (b): Scenario 2
    ax2.plot(vus, s2_p50, marker='o', color='#2563eb', linewidth=1.8, markersize=5.5, label=r'Median ($p_{50}$)')
    ax2.plot(vus, s2_p90, marker='s', color='#059669', linewidth=1.8, markersize=5.5, label=r'$p_{90}$')
    ax2.plot(vus, s2_p95, marker='d', color='#d97706', linewidth=1.8, markersize=5.5, label=r'$p_{95}$')
    ax2.plot(vus, s2_p99, marker='^', color='#dc2626', linewidth=2.0, markersize=5.5, label=r'$p_{99}$ (Tail)')

    ax2.set_title('(b) Scenario 2: Ingestion Latency (DEK Wrap + DB)', pad=8)
    ax2.set_xlabel('Concurrent Virtual Users (VUs)', labelpad=6)
    ax2.set_ylabel('Response Latency (ms)', labelpad=6)
    ax2.set_xlim(0, 520)
    ax2.set_ylim(0, 11000)
    ax2.grid(True, linestyle='--', alpha=0.55)
    ax2.legend(loc='upper left', frameon=True, fancybox=True, framealpha=0.92)

    out_path = os.path.join(FIGURES_DIR, 'fig_macro_latency_percentiles.pdf')
    plt.savefig(out_path, format='pdf', bbox_inches='tight')
    plt.close()
    print(f"  [OK] Generated Figure 5.4 saved to: {out_path}")


def main():
    parser = argparse.ArgumentParser(description="Publication Figure Generator for CryptoShred Health")
    parser.add_argument("--all", action="store_true", help="Generate and calibrate all figures")
    parser.add_argument("--fig32", action="store_true", help="Calibrate Figure 3.2 (Vault Raft HA)")
    parser.add_argument("--fig33", action="store_true", help="Generate Figure 3.3 (Erasure Sequence)")
    parser.add_argument("--fig51", action="store_true", help="Generate Figure 5.1 (Deletion Complexity)")
    parser.add_argument("--fig52", action="store_true", help="Generate Figure 5.2 (Merkle Scaling)")
    parser.add_argument("--fig53", action="store_true", help="Generate Figure 5.3 (Macro Throughput)")
    parser.add_argument("--fig54", action="store_true", help="Generate Figure 5.4 (Macro Latency Percentiles)")

    args = parser.parse_args()

    # If no specific figure flags passed, run all
    run_all = args.all or not (args.fig32 or args.fig33 or 
                               args.fig51 or args.fig52 or args.fig53 or args.fig54)

    print("================================================================================")
    print("      CryptoShred Health Publication Visual Diagram Generator (Cap 3 & 5)      ")
    print("================================================================================")

    if run_all or args.fig32:
        print("\n1. Calibrating Figure 3.2 (fig_vault_raft.pdf)...")
        calibrate_figure_3_2()

    if run_all or args.fig33:
        print("\n2. Generating Figure 3.3 (fig_erasure_sequence.pdf)...")
        generate_figure_3_3()

    if run_all or args.fig51:
        print("\n3. Generating Figure 5.1 (fig_deletion_scaling.pdf)...")
        generate_figure_5_1()

    if run_all or args.fig52:
        print("\n4. Generating Figure 5.2 (fig_merkle_scaling.pdf)...")
        generate_figure_5_2()

    if run_all or args.fig53:
        print("\n5. Generating Figure 5.3 (fig_macro_concurrency_throughput.pdf)...")
        generate_figure_5_3()

    if run_all or args.fig54:
        print("\n6. Generating Figure 5.4 (fig_macro_latency_percentiles.pdf)...")
        generate_figure_5_4()

    print("\n[SUCCESS] All requested publication figures processed successfully!")


if __name__ == "__main__":
    main()

