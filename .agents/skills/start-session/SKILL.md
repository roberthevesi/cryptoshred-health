---
name: start-session
description: Session initialization protocol for starting or resuming work on CryptoShred Health. Checks current project state, verifies repository health, and outlines immediate roadmap priorities. Triggered when the user types /start-session, /start, /resume, or asks for project status.
---

# Session Start & Status Assessment Protocol

This skill dictates the automated workflow to inspect the current state of `cryptoshred-health`, verify environment and repository health, and provide a clear, actionable briefing on the next immediate tasks to advance the project toward dissertation completion.

---

## 1. Automated State & Documentation Review
Upon invocation, the agent MUST perform the following inspection steps:

1. **Read Project Status & Roadmap**:
   * Inspect `.knowledge/status.md` to review the architectural component completion matrix and identify pending items in the 4 Dissertation Pillars.
2. **Review Operational Handbook & Directives**:
   * Inspect `.knowledge/workflow.md` to verify strict agent rules (e.g., zero hardcoded secrets, no remote `git push`, port mappings).
3. **Inspect Git Status & Recent History**:
   * Run `git status` to detect any unstaged or untracked changes.
   * Run `git log -n 5 --oneline` to review the latest local milestones.
4. **Sanity Check Environment & Verification**:
   * Verify that local `.env` and `backend/.env` files exist.
   * If requested or if state is ambiguous, run `./mvnw test` or `npm run build` to confirm system stability.

---

## 2. Roadmap Evaluation Against the 4 Dissertation Pillars
The agent evaluates pending work across the 4 core pillars defined in `.knowledge/status.md`:

1. **Pillar 1: System Core & Zero-Plaintext Storage (Practical)** — 100% Complete
   * Spring Boot 3, zero-plaintext PostgreSQL, Vault Transit KMS, Redis L2, Kafka KRaft, WORM, React 19 UI.
2. **Pillar 2: Cryptographic Protocols & Standards (Practical)** — 100% Complete
   * Dual-level crypto-shredding, key rotation protocol (`/api/keys/rotate`), HL7 FHIR R4 exporter (`/api/patients/{id}/fhir`), Merkle DAG & RSA-2048 deletion proof engine.
3. **Pillar 3: Empirical Benchmarks & Load Testing Suite (Practical)** — 100% Complete
   * JMH microbenchmarks ($O(1)$ vs $O(N)$), Merkle DAG scaling, k6 macro load testing harness up to 500 VUs / 96k RPS.
4. **Pillar 4: Written Dissertation Thesis Document & Syntheses (Active)**:
   * Formal STRIDE/DREAD threat matrix & 4 adversary models (Chapter 3).
   * Formal IND-CCA2 cryptographic reduction proofs & residual memory zeroization audit (Chapter 6).
   * GDPR Art. 17 vs HIPAA/NHS legal retention reconciliation synthesis (Chapter 6).
   * Chapter-by-chapter drafting progress (Chapters 1–8).

---

## 3. Session Kickoff Briefing Output
Provide a structured, concise Markdown report to the user containing:

* 🧭 **Current Milestone & System Health**:
  * Summary of active components (Backend, KMS Vault, Redis, Kafka, WORM, React UI) and test status.
  * Latest git commit hash and message.
* 📋 **Immediate Next Step Priorities**:
  * 2–3 specific, prioritized tasks ready for implementation from the active sprint.
* 🚀 **Ready-to-Execute Options**:
  * Present 2–3 concrete implementation choices (e.g., *"Option 1: Build JMH benchmark harness"*, *"Option 2: Implement HL7 FHIR R4 export"*), asking the user which one they would like to focus on for this session.
