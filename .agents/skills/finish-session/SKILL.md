---
name: finish-session
description: Complete wrap-up protocol for finishing a coding session or feature implementation. Triggered when the user types /finish-session, /finish, or asks to wrap up a session.
---

# Session Wrap-Up & Completion Protocol

This skill dictates the automated verification, documentation, version control, and hand-off protocol when wrapping up a session or completing a feature implementation in `cryptoshred-health`.

---

## 1. Automated Verification & Code Quality
Before declaring a session complete:
1. **Backend Build & Tests**:
   * Navigate to `backend/` and run `./mvnw test` to ensure all unit and integration tests compile cleanly and pass.
2. **Frontend Type & Build Check**:
   * Navigate to `frontend/cryptoshred-health/` and run `npm run build` or typecheck to verify zero TypeScript errors.
3. **Secret & Hardening Audit**:
   * Verify no secret literals, real passwords, or fallback default strings exist in version-controlled configuration files (`application.properties`, `docker-compose.yml`, `pom.xml`).
   * Ensure credentials reside strictly in git-ignored `.env` files.

---

## 2. Documentation & Handbooks Synchronization
Keep local documentation fully up to date:
1. **`.knowledge/status.md`**:
   * Update component completion status table and percentages.
   * Update current file inventory and implementation details.
   * Update roadmap and next step priorities.
2. **`.knowledge/workflow.md`**:
   * Update port mappings, service architecture table, or new environment variables.
   * Document any new troubleshooting tips or command shortcuts.
3. **`walkthrough.md` Artifact**:
   * Create or update the walkthrough document summarizing key changes, file links, and test results.
4. **`README.md`**:
   * Ensure user-facing installation, environment variables template, and feature descriptions are current.

---

## 3. Git Version Control Protocol (Local Only)
1. **Check Git Status**:
   * Run `git status` to identify modified and untracked files.
2. **Verify `.gitignore` Integrity**:
   * Ensure `.knowledge/`, `.env`, and local build artifacts remain un-tracked.
3. **Stage & Commit Locally**:
   * Stage changes using `git add <files>`.
   * Create a structured local commit (`git commit -m "<type>: <description>"`).
4. **Strict Rule — No Remote Pushing**:
   * Do **NOT** execute `git push` (remote pushes are blocked in the background sandbox).
   * Inform the user of the local commit hash and remind them they can push when ready with `git push`.

---

## 4. Final Session Hand-off Report
Output a clean, structured Markdown response to the user containing:
* 🛠️ **Completed Deliverables**: Bulleted list of implemented features, refactors, or bug fixes.
* 📚 **Documentation Updated**: Links to modified `.md` files (`status.md`, `workflow.md`, `walkthrough.md`).
* 🔀 **Local Git Commit**: Hash and commit message.
* 📋 **Next Session Priorities**: 1–3 clear bullet points outlining the immediate next steps for the project.
