# Thesis Revisions Summary (Coordinator Feedback Actions)

**Project:** CryptoShred Health (MSc Dissertation)  
**Scope:** Thesis Source Code (`thesis/chapters/`, `thesis/figures/`, `main.tex`, `bibliography.bib`)

---

## 1. Files Touched & Created

| Category | File Path | Type | Action Summary |
|---|---|---|---|
| **Front Matter** | `thesis/main.tex` | LaTeX | Changed *List of Snippets* $\rightarrow$ *List of Listings*; included `abbreviations.tex`. |
| **Front Matter** | `thesis/chapters/abbreviations.tex` | LaTeX | **NEW**: 55-entry formal List of Abbreviations (`longtable`). |
| **Abstracts** | `thesis/chapters/2.AbstractRO.tex` | LaTeX | Added Romanian title, updated metrics ($4\text{--}5\times$, $5.6\text{--}7.6\text{ ms}$), added 7 keywords. |
| **Abstracts** | `thesis/chapters/3.AbstractEN.tex` | LaTeX | Updated metrics ($4\text{--}5\times$, $5.6\text{--}7.6\text{ ms}$, work factor $2^{255}$), added 7 keywords. |
| **Chapter 1** | `thesis/chapters/4.Introduction.tex` | LaTeX | Calibrated $H_1$ to $4\text{--}5\times$; $H_3$ to linear time sub-$5\text{ ms/key}$; Eq 1.1 inline; added CWE/NIST citations. |
| **Chapter 2** | `thesis/chapters/5.TheoreticalFoundations.tex` | LaTeX | Added single-use DEK rationale, AEAD definition ($\text{IND-CCA2}$), Binary Merkle Tree clarification, $2^{255}$ work factor, Table 2.1 *Verification Result* column. |
| **Chapter 3** | `thesis/chapters/6.SystemDesign.tex` | LaTeX | Added $\mathcal{O}(1)$ vs $\mathcal{O}(m)$ shredding distinction; embedded Figures 3.3, 3.4, 3.5; updated failover to $<2.5\text{ s}$; documented WORM paradox fix. |
| **Chapter 4** | `thesis/chapters/7.Implementation.tex` | LaTeX | Documented `Arrays.fill` & String GC limits; Vault `IPC_LOCK` vs Docker anti-swap; `min_decryption_version`; Dilithium3/ML-DSA-65 & OpenSSL PSS; removed port/UI bloat. |
| **Chapter 5** | `thesis/chapters/8.Evaluation.tex` | LaTeX | Table 5.1 microbenchmark & JIT warmup explanation; Table 5.2 units ($\text{ops/s}$, $\text{ms}$); unpooled JMH vs pooled k6; Table 5.3 heavy tail; Table 5.7 sum fix ($2{,}377{,}883$); 25-key purge results ($42.3\text{ ms}$); renamed Sec 5.4.1. |
| **Chapter 6** | `thesis/chapters/9.Conclusions.tex` | LaTeX | Table 6.1 & text calibrated to honest $4\text{--}5\times$ speedup; toned down marketing language to academic register. |
| **Bibliography** | `thesis/bibliography.bib` | BibTeX | Expanded from 16 to 38 citations (IEEEtran double braces, protected acronyms, Shor 1997, NIST 800-88, Romanian laws). |
| **Figures Suite** | `thesis/figures/generate_figures.py` | Python | **NEW**: Master CLI generator (`--all`, `--fig32`, `--fig33`, `--fig34`, `--fig35`, `--fig53`). |
| **Figure 3.2** | `thesis/figures/fig_vault_raft.pdf` | Vector PDF | Calibrated text operators: `< 500ms` $\rightarrow$ `< 2.5s` ($1.84\text{ s}$ Raft, $2.15\text{ s}$ HAProxy). |
| **Figure 3.3** | `thesis/figures/fig_erasure_sequence.pdf` | Vector PDF | **NEW**: Publication sequence diagram (5-stage cryptographic erasure pipeline). |
| **Figure 3.4** | `thesis/figures/fig_er_model.pdf` | Vector PDF | **NEW**: Publication Crow's Foot ER diagram (encrypted payloads vs plaintext metadata). |
| **Figure 3.5** | `thesis/figures/fig_merkle_path.pdf` | Vector PDF | **NEW**: Publication Binary Merkle Tree diagram (directional inclusion proof path). |
| **Figure 5.3** | `thesis/figures/fig_macro_concurrency_throughput.pdf` | Vector PDF | Regenerated with legend sorted in strict numerical order (1, 2, 3, 4). |

---

## 2. Key Metric & Architectural Calibrations

1. **Speedup Metric Honesty ($H_1$, Tables 5.1, 6.1, Abstracts)**:
   - Primary real-world speedup framed on end-to-end latency: **$5.63\text{--}7.65\text{ ms}$ vs. $25.19\text{--}39.12\text{ ms}$ ($\approx 4\text{--}5\times$)**.
   - Table 5.1 ($59.50\,\mu\text{s}$) explicitly identified as an in-memory CPU microbenchmark.
2. **Hypothesis $H_3$ Formulation ($H_3$, Section 5.3.3)**:
   - Formulated as linear scaling ($\mathcal{O}(k)$) with sub-$5\text{ ms}$ per-key latency ($< 50\text{ ms}$ for typical drift cohorts $k \le 30$). Empirical 25-key test reported at **$42.3\text{ ms} \implies 1.69\text{ ms/key}$**.
3. **Failover Bound Calibration (Figure 3.2, Section 3.4.1)**:
   - Obsolete `< 500ms` claims replaced with measured benchmarks: **$1.84\text{ s}$ Raft re-election, $2.15\text{ s}$ HAProxy reroute (total $< 2.5\text{ s}$)**.
4. **Snapshot Restoration Paradox (Sections 3.4.2–3.4.3, 4.3, 5.3.3)**:
   - Documented the architectural solution: harvesting tombstones from the external immutable WORM deletion receipts directory (`backups/deletion-receipt_*.json`) during startup.
5. **Table 5.2 Units & Network Pooling**:
   - Converted throughput unit from $0.0001\text{ ops}/\mu\text{s}$ to **$93.6\text{ ops/s}$** and latency to **$10.68\text{ ms}$**. Explained JMH unpooled TCP vs. k6 keep-alive connection pooling ($7.30\text{ ms}$).
6. **Academic Style & Vocabulary**:
   - Zero AI clichés/tropes (audited and confirmed); non-breaking spaces before citations (`~\cite{...}`) and units (`5.63~ms`, `59.5~\mu s`); em-dashes `---` without surrounding spaces.
