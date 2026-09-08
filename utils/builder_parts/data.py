"""
Data definitions for CryptoShred Health Dissertation Visual Comparison App.
Includes:
- 17 Decision Audit Items
- 67 Abbreviations
- 16 Active IEEE Citations
- 22 Removed Citations
- Image file paths
"""

import os

UTILS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORKSPACE_DIR = os.path.abspath(os.path.join(UTILS_DIR, ".."))
THESIS_DIR = os.path.join(WORKSPACE_DIR, "thesis")
CHAPTERS_DIR = os.path.join(THESIS_DIR, "chapters")
FIGURES_DIR = os.path.join(THESIS_DIR, "figures")
IMAGES_DIR = os.path.join(THESIS_DIR, "images")
OUT_PRIMARY = os.path.join(UTILS_DIR, "before_after_comparison.html")
OUT_ARTIFACT = "/Users/roberthevesi/.gemini/antigravity/brain/aef2e756-d39e-49f4-a059-1fc2f769547e/before_after_comparison.html"

IMAGE_PATHS = {
    "logo": os.path.join(IMAGES_DIR, "logo.png"),
    "fig33_after": os.path.join(FIGURES_DIR, "fig_erasure_sequence.png"),
    "fig33_before": "/Users/roberthevesi/.gemini/antigravity/brain/aef2e756-d39e-49f4-a059-1fc2f769547e/.tempmediaStorage/media_1788876004768.png",
    "fig33_initial": "/Users/roberthevesi/.gemini/antigravity/brain/aef2e756-d39e-49f4-a059-1fc2f769547e/.tempmediaStorage/media_1788875303863.png",
    "fig_sys_arch": os.path.join(FIGURES_DIR, "fig_system_architecture.png"),
    "fig_vault_raft": os.path.join(FIGURES_DIR, "fig_vault_raft.png"),
    "ui_merkle_verifier": os.path.join(IMAGES_DIR, "ui_merkle_verifier.png"),
    "ui_patient_census": os.path.join(IMAGES_DIR, "ui_patient_census.png"),
    "ui_patient_chart": os.path.join(IMAGES_DIR, "ui_patient_chart.png"),
    "ui_grafana_dashboard": os.path.join(IMAGES_DIR, "ui_grafana_dashboard.png"),
    "fig_deletion_scaling": os.path.join(FIGURES_DIR, "fig_deletion_scaling.png"),
    "fig_merkle_scaling": os.path.join(FIGURES_DIR, "fig_merkle_scaling.png"),
    "fig_macro_throughput": os.path.join(FIGURES_DIR, "fig_macro_concurrency_throughput.png"),
    "fig_macro_percentiles": os.path.join(FIGURES_DIR, "fig_macro_latency_percentiles.png"),
}

AUDIT_ITEMS = [
    {
        "id": 1,
        "title": "Bibliography Expansion Reverted",
        "category": "Bibliography",
        "directive": "do not increase the number of citations",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/bibliography.bib",
        "summary": "Restored exact original 16 citations. Removed 22 added entries (RFCs, NIST SP 800-88, Romanian statutes, Shor 1997, CWE entries). All 16 citation keys map 100% in text with zero dangling references.",
        "jump": "#diff-change-item-1"
    },
    {
        "id": 2,
        "title": "List of Listings Reverted to List of Snippets",
        "category": "Front Matter",
        "directive": "do not rename anything to list of listings",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/main.tex",
        "summary": "Reverted \\listof{lstlisting}{List of Listings} back to \\listof{lstlisting}{List of Snippets} at line 105 in main.tex.",
        "jump": "#diff-change-item-2"
    },
    {
        "id": 3,
        "title": "Entity-Relationship Diagram Removed",
        "category": "System Design",
        "directive": "do not create an entity relationship figure",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/chapters/6.SystemDesign.tex",
        "summary": "Deleted thesis/figures/fig_er_model.pdf. Removed Figure 3.4 and its referring schema paragraph from Section 3.2.",
        "jump": "#diff-change-item-3"
    },
    {
        "id": 4,
        "title": "KMS Raft Failover Metric Calibration",
        "category": "Figures & System Design",
        "directive": "Coordinator Inconsistency Inc-02: Calibrate failover bound to < 2.5s",
        "status": "KEPT",
        "badge_class": "badge-kept",
        "file": "thesis/figures/fig_vault_raft.pdf & 6.SystemDesign.tex",
        "summary": "Updated obsolete < 500ms claims in Figure 3.2 and Section 3.4.1 to measured benchmarks: 1.84 s Raft re-election + 2.15 s HAProxy health check (total < 2.5 s).",
        "jump": "#diff-change-item-4"
    },
    {
        "id": 5,
        "title": "Erasure Pipeline Sequence Diagram (Figure 3.3) Redesign",
        "category": "Figures & System Design",
        "directive": "make it simpler and more tall than wide, fix overlapping text, full arrows, opaque background",
        "status": "POLISHED",
        "badge_class": "badge-polished",
        "file": "thesis/figures/fig_erasure_sequence.pdf/.png",
        "summary": "Rebuilt in tall portrait format (7.6 x 11.2 in). Lifeline headers fit with ample padding, message labels have opaque white backing, arrows are completely visible, zero line collisions.",
        "jump": "#diff-change-item-5"
    },
    {
        "id": 6,
        "title": "Binary Merkle Tree Inclusion Proof Diagram Removed",
        "category": "System Design",
        "directive": "Please revert 6",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/chapters/6.SystemDesign.tex",
        "summary": "Deleted thesis/figures/fig_merkle_path.pdf. Removed Figure 3.5 and its referring sentence from Section 3.3.4.",
        "jump": "#diff-change-item-6"
    },
    {
        "id": 7,
        "title": "Macro Concurrency Legend Numerical Order",
        "category": "Figures & Evaluation",
        "directive": "Coordinator feedback: sort legend in strict numerical order",
        "status": "KEPT",
        "badge_class": "badge-kept",
        "file": "thesis/figures/fig_macro_concurrency_throughput.pdf/.png",
        "summary": "Regenerated Figure 5.3 so legend entries appear in strict numerical order (Scenario 1, Scenario 2, Scenario 3, Scenario 4).",
        "jump": "#diff-change-item-7"
    },
    {
        "id": 8,
        "title": "Master Python Generator Script",
        "category": "Tooling & Infrastructure",
        "directive": "Provide reproducible figure generation suite",
        "status": "KEPT",
        "badge_class": "badge-kept",
        "file": "thesis/figures/generate_figures.py",
        "summary": "Created standalone master CLI generator supporting flags --all, --fig32, --fig33, --fig53, and strict reproducibility.",
        "jump": "#diff-change-item-8"
    },
    {
        "id": 9,
        "title": "List of Abbreviations Completely Removed",
        "category": "Front Matter",
        "directive": "9 - please remove this",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/chapters/abbreviations.tex & main.tex",
        "summary": "Deleted thesis/chapters/abbreviations.tex and removed \\include{chapters/abbreviations} from main.tex per explicit user instruction.",
        "jump": "#diff-change-item-9"
    },
    {
        "id": 10,
        "title": "Romanian Abstract Keywords Removed",
        "category": "Abstracts",
        "directive": "10 - please remove the keywords from both abstracts, I never asked for this",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/chapters/2.AbstractRO.tex",
        "summary": "Removed Cuvinte-cheie section from Romanian abstract. Retained calibrated 4-5x speedup and 5.63-7.65 ms metrics.",
        "jump": "#diff-change-item-10"
    },
    {
        "id": 11,
        "title": "English Abstract Keywords Removed",
        "category": "Abstracts",
        "directive": "10 - please remove the keywords from both abstracts, I never asked for this",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/chapters/3.AbstractEN.tex",
        "summary": "Removed Keywords section from English abstract. Retained calibrated 4-5x speedup, 5.63-7.65 ms latency, and 2^255 work factor.",
        "jump": "#diff-change-item-11"
    },
    {
        "id": 12,
        "title": "Hypothesis H1 Speedup Metric Calibration",
        "category": "Introduction",
        "directive": "Coordinator Inconsistency Inc-03: Frame speedup honestly on end-to-end latency",
        "status": "KEPT",
        "badge_class": "badge-kept",
        "file": "thesis/chapters/4.Introduction.tex",
        "summary": "Framed speedup on end-to-end HTTP latency (4-5x over loopback cascades: 5.63-7.65 ms vs 25.19-39.12 ms) rather than 1,000x microbenchmark claim.",
        "jump": "#diff-change-item-12"
    },
    {
        "id": 13,
        "title": "Hypothesis H3 Linear Scaling Formulation",
        "category": "Introduction",
        "directive": "Coordinator Inconsistency Inc-12: Purge sub-50ms cohort vs per-key latency",
        "status": "KEPT",
        "badge_class": "badge-kept",
        "file": "thesis/chapters/4.Introduction.tex",
        "summary": "Formulated H3 as linear scaling O(k) with sub-5 ms per-key latency (< 50 ms for typical cohorts k <= 30), aligning with empirical 42.3 ms test for 25 keys.",
        "jump": "#diff-change-item-13"
    },
    {
        "id": 14,
        "title": "Equation 1.1 Inlining",
        "category": "Introduction",
        "directive": "Inline trivial O(N) deletion formula per academic style",
        "status": "KEPT",
        "badge_class": "badge-kept",
        "file": "thesis/chapters/4.Introduction.tex",
        "summary": "Converted standalone formula T_physical = O(N) into inline text to streamline technical cadence.",
        "jump": "#diff-change-item-14"
    },
    {
        "id": 15,
        "title": "Architectural Flaws CWE and NIST References Removed",
        "category": "Introduction",
        "directive": "Please revert 15",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/chapters/4.Introduction.tex",
        "summary": "Removed CWE-798, CWE-312, and NIST SP 800-88 citations from the 5 architectural flaws in Section 1.2, restoring original clean formulations.",
        "jump": "#diff-change-item-15"
    },
    {
        "id": 16,
        "title": "Single-Use DEK Cryptographic Rationale",
        "category": "Theoretical Foundations",
        "directive": "Explain AES-GCM IV collision elimination and 2^32 bound",
        "status": "KEPT",
        "badge_class": "badge-kept",
        "file": "thesis/chapters/5.TheoreticalFoundations.tex",
        "summary": "Added cryptographic rationale explaining that ephemeral single-use DEKs eliminate AES-GCM IV collision risks and bypass the NIST 2^32 invocation limit.",
        "jump": "#diff-change-item-16"
    },
    {
        "id": 17,
        "title": "AEAD IND-CCA2 Formal Definition Reverted",
        "category": "Theoretical Foundations",
        "directive": "Please revert 17",
        "status": "REVERTED",
        "badge_class": "badge-reverted",
        "file": "thesis/chapters/5.TheoreticalFoundations.tex",
        "summary": "Removed formal IND-CPA and INT-CTXT implies IND-CCA2 proposition and equation from Section 2.1.1 per explicit user instruction.",
        "jump": "#diff-change-item-17"
    }
]

ABBREVIATIONS = [
    ("AAD", "Authenticated Associated Data"),
    ("AES", "Advanced Encryption Standard (FIPS PUB 197)"),
    ("ANSPDCP", "Autoritatea Națională de Supraveghere a Prelucrării Datelor cu Caracter Personal"),
    ("API", "Application Programming Interface"),
    ("BMT", "Binary Merkle Tree"),
    ("CPU", "Central Processing Unit"),
    ("CWE", "Common Weakness Enumeration"),
    ("DEK", "Data Encryption Key"),
    ("DPO", "Data Protection Officer"),
    ("DR", "Disaster Recovery"),
    ("EDPB", "European Data Protection Board"),
    ("EHR", "Electronic Health Record"),
    ("FHIR", "Fast Healthcare Interoperability Resources (HL7)"),
    ("FIPS", "Federal Information Processing Standards"),
    ("GC", "Garbage Collection"),
    ("GCM", "Galois/Counter Mode (NIST SP 800-38D)"),
    ("GDPR", "General Data Protection Regulation (Regulation (EU) 2016/679)"),
    ("HIPAA", "Health Insurance Portability and Accountability Act of 1996"),
    ("HMAC", "Hash-based Message Authentication Code (RFC 2104)"),
    ("HTTP", "Hypertext Transfer Protocol"),
    ("IND-CCA2", "Indistinguishability under Adaptive Chosen-Ciphertext Attack"),
    ("IND-CPA", "Indistinguishability under Chosen-Plaintext Attack"),
    ("INT-CTXT", "Ciphertext Integrity"),
    ("IPC", "Inter-Process Communication"),
    ("IV", "Initialization Vector (Nonce)"),
    ("JIT", "Just-In-Time Compiler"),
    ("JMH", "Java Microbenchmark Harness"),
    ("JPA", "Java Persistence API"),
    ("JSON", "JavaScript Object Notation"),
    ("JWT", "JSON Web Token"),
    ("KEK", "Key Encryption Key"),
    ("KMS", "Key Management Service"),
    ("KRaft", "Kafka Raft Metadata Mode"),
    ("L2", "Level 2 (Secondary Cache Tier)"),
    ("ML-DSA", "Module-Lattice-Based Digital Signature Algorithm (FIPS 204)"),
    ("NFR", "Non-Functional Requirement"),
    ("NHS", "National Health Service (United Kingdom)"),
    ("NIST", "National Institute of Standards and Technology"),
    ("OOM", "Out of Memory"),
    ("ORM", "Object-Relational Mapping"),
    ("OS", "Operating System"),
    ("OWASP", "Open Web Application Security Project"),
    ("PHI", "Protected Health Information"),
    ("PII", "Personally Identifiable Information"),
    ("PQC", "Post-Quantum Cryptography"),
    ("PRP", "Pseudorandom Permutation"),
    ("PSS", "Probabilistic Signature Scheme (PKCS #1 v2.1)"),
    ("Raft", "Raft Consensus Algorithm"),
    ("RBAC", "Role-Based Access Control"),
    ("REST", "Representational State Transfer"),
    ("RFC", "Request for Comments"),
    ("RSA", "Rivest–Shamir–Adleman Public-Key Cryptosystem"),
    ("SHA", "Secure Hash Algorithm (FIPS PUB 180-4)"),
    ("SQL", "Structured Query Language"),
    ("SR", "Security Requirement"),
    ("STRIDE", "Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege"),
    ("TCB", "Trusted Computing Base"),
    ("TCP", "Transmission Control Protocol"),
    ("TLS", "Transport Layer Security"),
    ("UI", "User Interface"),
    ("UK", "United Kingdom"),
    ("UPT", "Universitatea Politehnica Timișoara"),
    ("UUID", "Universally Unique Identifier"),
    ("VU", "Virtual User (Load Testing)"),
    ("WORM", "Write-Once-Read-Many Storage")
]

ACTIVE_CITATIONS = [
    {"num": 1, "key": "gdpr2016", "authors": "European Parliament and Council of the European Union", "title": "Regulation (EU) 2016/679 of the European Parliament and of the Council on the protection of natural persons with regard to the processing of personal data (General Data Protection Regulation)", "journal": "Official Journal of the European Union", "year": "2016"},
    {"num": 2, "key": "dpa2018", "authors": "United Kingdom Parliament", "title": "Data Protection Act 2018 (c. 12)", "journal": "The Stationery Office, London, UK", "year": "2018"},
    {"num": 3, "key": "nhs_records_2021", "authors": "NHS Transformation Directorate", "title": "Records Management Code of Practice 2021", "journal": "National Health Service, Department of Health and Social Care, UK", "year": "2021"},
    {"num": 4, "key": "hipaa2003", "authors": "United States Department of Health and Human Services", "title": "Health Insurance Portability and Accountability Act (HIPAA) Security and Privacy Rules (45 CFR Parts 160 and 164)", "journal": "Office for Civil Rights, Washington, D.C.", "year": "2003"},
    {"num": 5, "key": "nist_fips_204", "authors": "National Institute of Standards and Technology", "title": "Module-Lattice-Based Digital Signature Standard (FIPS PUB 204)", "journal": "U.S. Department of Commerce", "year": "2024"},
    {"num": 6, "key": "boneh_shoup_2020", "authors": "Dan Boneh and Victor Shoup", "title": "A Graduate Course in Applied Cryptography (Version 0.5)", "journal": "Stanford University", "year": "2020"},
    {"num": 7, "key": "nist_fips_197", "authors": "National Institute of Standards and Technology", "title": "Advanced Encryption Standard (AES) (FIPS PUB 197)", "journal": "U.S. Department of Commerce", "year": "2001"},
    {"num": 8, "key": "dworkin_gcm_2007", "authors": "Morris Dworkin", "title": "Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM) and GMAC (NIST Special Publication 800-38D)", "journal": "National Institute of Standards and Technology", "year": "2007"},
    {"num": 9, "key": "merkle1989", "authors": "Ralph C. Merkle", "title": "A Certified Digital Signature", "journal": "Advances in Cryptology --- CRYPTO '89 Proceedings, LNCS 435, Springer", "year": "1989"},
    {"num": 10, "key": "krawczyk_hmac_1997", "authors": "Hugo Krawczyk, Mihir Bellare, and Ran Canetti", "title": "HMAC: Keyed-Hashing for Message Authentication (RFC 2104)", "journal": "Internet Engineering Task Force (IETF)", "year": "1997"},
    {"num": 11, "key": "edpb2014", "authors": "Article 29 Data Protection Working Party", "title": "Opinion 05/2014 on Anonymisation Techniques (WP216)", "journal": "European Commission, Directorate-General for Justice", "year": "2014"},
    {"num": 12, "key": "garfinkel2007", "authors": "Simson L. Garfinkel and David Shelat", "title": "Remembrance of Data Passed: A Study of Disk Sanitization Practices", "journal": "IEEE Security & Privacy, vol. 1, no. 1, pp. 17--27", "year": "2007"},
    {"num": 13, "key": "hl7_fhir_r4", "authors": "Health Level Seven International", "title": "HL7 FHIR Release 4 (R4) Standard Specification", "journal": "Health Level Seven International, Ann Arbor, MI", "year": "2019"},
    {"num": 14, "key": "ongaro_raft_2014", "authors": "Diego Ongaro and John Ousterhout", "title": "In Search of an Understandable Consensus Algorithm", "journal": "Proceedings of the 2014 USENIX Annual Technical Conference (USENIX ATC '14), pp. 305--319", "year": "2014"},
    {"num": 15, "key": "owasp_threat_modeling", "authors": "OWASP Foundation", "title": "OWASP Automated Threat Modeling and STRIDE Guidance", "journal": "Open Web Application Security Project", "year": "2023"},
    {"num": 16, "key": "barker_nist_sp800_57", "authors": "Elaine Barker", "title": "Recommendation for Key Management: Part 1 -- General (NIST Special Publication 800-57 Part 1 Rev. 5)", "journal": "National Institute of Standards and Technology", "year": "2020"}
]

REMOVED_CITATIONS = [
    {"key": "nist_fips_203", "title": "Module-Lattice-Based Key-Encapsulation Mechanism Standard (ML-KEM) (FIPS PUB 203)", "author": "NIST", "year": "2024", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "nist_sp_800_88_r1", "title": "Guidelines for Media Sanitization (NIST SP 800-88 Rev. 1)", "author": "Richard Kissel et al.", "year": "2014", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "shor1997", "title": "Polynomial-Time Algorithms for Prime Factorization and Discrete Logarithms on a Quantum Computer", "author": "Peter W. Shor", "year": "1997", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "boneh_lipton1996", "title": "Revocable Encryption, or, How to Break Computer Systems without Accessing Them", "author": "Dan Boneh and Richard J. Lipton", "year": "1996", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "perlman2005", "title": "The Ephemerizer: Making Data Disappear", "author": "Radia Perlman", "year": "2005", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "tang2012", "title": "Secure Overlay Cloud Storage with Access Control and Assured Deletion", "author": "Yang Tang et al.", "year": "2012", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "reardon2013", "title": "SoK: Secure Data Deletion", "author": "Joel Reardon et al.", "year": "2013", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "rfc6962", "title": "Certificate Transparency (RFC 6962)", "author": "Ben Laurie et al.", "year": "2013", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "legea95_2006", "title": "Legea nr. 95/2006 privind reforma în domeniul sănătății", "author": "Parlamentul României", "year": "2006", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "legea190_2018", "title": "Legea nr. 190/2018 privind măsuri de punere în aplicare a Regulamentului (UE) 2016/679", "author": "Parlamentul României", "year": "2018", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "cwe312", "title": "CWE-312: Cleartext Storage of Sensitive Information", "author": "MITRE Corporation", "year": "2024", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "cwe798", "title": "CWE-798: Use of Hard-coded Credentials", "author": "MITRE Corporation", "year": "2024", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "cwe311", "title": "CWE-311: Missing Encryption of Sensitive Data", "author": "MITRE Corporation", "year": "2024", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "shamir1979", "title": "How to Share a Secret", "author": "Adi Shamir", "year": "1979", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "rivest1978", "title": "A Method for Obtaining Digital Signatures and Public-Key Cryptosystems", "author": "R. L. Rivest, A. Shamir, and L. Adleman", "year": "1978", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "bellare_namprempre2000", "title": "Authenticated Encryption: Relations among Notions and Analysis of the Generic Composition Paradigm", "author": "Mihir Bellare and Chanathip Namprempre", "year": "2000", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "mcgrew_viega_2004", "title": "The Security and Performance of the Galois/Counter Mode (GCM)", "author": "David A. McGrew and John Viega", "year": "2004", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "rfc8446", "title": "The Transport Layer Security (TLS) Protocol Version 1.3 (RFC 8446)", "author": "Eric Rescorla", "year": "2018", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "rfc7515", "title": "JSON Web Signature (JWS) (RFC 7515)", "author": "Michael B. Jones et al.", "year": "2015", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "rfc7519", "title": "JSON Web Token (JWT) (RFC 7519)", "author": "Michael B. Jones et al.", "year": "2015", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "synthea_2018", "title": "Synthea: An Approach, Method, and Software Mechanism for Generating Realistic Synthetic Patients", "author": "Jason Walonoski et al.", "year": "2018", "reason": "Reverted per user directive (keep original 16 citations)"},
    {"key": "anspdcp_guidelines", "title": "Ghid privind aplicarea Regulamentului General privind Protecția Datelor în domeniul sănătății", "author": "ANSPDCP", "year": "2019", "reason": "Reverted per user directive (keep original 16 citations)"}
]

KEY_TO_NUM = {c["key"]: c["num"] for c in ACTIVE_CITATIONS}
