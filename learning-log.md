# Learning Log

**Owner:** Abdulaziz · **Method:** see learning-playbook.md (Rules 4 & 6 — this file is the memory of the whole system)
**AI instructions:** Read this file at the start of every session. Use the question bank for weekly retain-session quizzes (sample PAST weeks only, never the current one). After each session, the learner appends one entry — remind them if they forget.

---

## Current state

| Field | Value |
|---|---|
| Active project / phase | QuickPay — Phase 6 (build flows). Wallet service DONE & merged to `main` (top-up, P2P transfer, withdrawal, gateway webhook). Next flow: **bill payment** (reserve/capture + compensation) |
| Current loop (playbook Rule 7) | Open: **revisit skipped Phases 0–2** (sponsor interrogation, data-ownership map, decomposition + sequence diagrams) BEFORE adding service #2 / RabbitMQ |
| Last quiz score on past topics | — (target ≥80%; below → re-study before new topics) — no retain-session run yet |
| Certification in progress | TOGAF Practitioner (target: Oct 2026) |

> **Note:** entries below for 2026-06-11 → 06-17 were reconstructed on 2026-06-17 from the project memory file and git history (sessions were not logged live). Detail is faithful to recorded facts but lacks the verbatim predictions/teach-backs the template wants. Log live from here on.

---

## Entry template (copy per session)

```
### YYYY-MM-DD — Session A/B/C — [topic]
- What I did:
- Prediction(s) I wrote and what actually happened:
- The surprise, and my explanation of it:
- Teach-back given? (Y/N) — AI's main correction:
- New quiz questions added to bank: Q##–Q##
- Open questions / where the next session resumes:
```

---

## Question bank

Scenario-style only (playbook Rule 4). Number sequentially; never delete — retired questions get struck through with the date mastered.

> Q02–Q05 were seeded on 2026-06-17 from real bugs in the wallet build — review and reword in your own terms so they're yours.

| # | Question | Topic | Added | Last asked | Result |
|---|---|---|---|---|---|
| Q01 | The mobile app retries a transfer after a 5 s timeout and the customer is debited twice. Name three places the design must defend against this, and which one is the real guarantee. | Idempotency | 2026-06-13 | — | — |
| Q02 | A `topUp` method calls `this.transfer(...)`, and `transfer` is `@Transactional` but `topUp` is not. At runtime the pessimistic lock throws `TransactionRequiredException`. Explain why the annotation didn't apply, and give the fix. | Spring tx / proxies | 2026-06-16 | — | — |
| Q03 | A payment gateway redelivers the same webhook three times. Walk through how a credit happens exactly once, and name the single DB object that makes it safe. | Webhooks / idempotency | 2026-06-17 | — | — |
| Q04 | You verify a webhook HMAC with `a.equals(b)`. Why switch to `MessageDigest.isEqual`, and what class of attack does the constant-time compare close? | Security / timing | 2026-06-17 | — | — |
| Q05 | An external gateway you do NOT control sends a 39-char transaction id, but your `idempotency_key` column is `varchar(36)`. Give two designs that keep idempotency without truncating or colliding. | Contracts / data modeling | 2026-06-17 | — | — |

---

## Entries

*(newest at top)*

### 2026-06-17 — gateway webhook (HMAC + idempotency)
- What I did: built `GatewayWebhookController` — raw-body capture, HMAC-SHA256 verify (lowercase hex, constant-time compare), then delegate to `topUp` using the gateway txn id as the idempotency key. AI scaffolded the gateway-simulator (:9090). Merged via PR#2; all branches deleted, clean `main`.
- Prediction(s) vs reality: verified valid→200+credit, redelivery→200 + NO double-credit (exactly one ledger row), forged signature→401. All held.
- The surprise + explanation: (a) `@RequiredArgsConstructor` only injects `final` fields — `objectMapper` wasn't final → null → NPE → 500. (b) `"GW-"+UUID` = 39 chars overflowed `idempotency_key varchar(36)` → since we control the simulator, made it emit a 35-char hyphenless id. Real uncontrollable gateways would need hash-to-fixed-width or a separate `external_reference` column.
- Teach-back given? reconstructed — not captured.
- New quiz questions added: Q03, Q04, Q05.
- Resumes at: bill-payment flow (the first flow needing reserve/capture + compensation), OR backfill Phases 0–2 design first.

### 2026-06-16 — full flow end-to-end + 1–5 hardening + withdrawal
- What I did: confirmed full money lifecycle. Added `@Transactional` to `topUp` (the bug from 06-15). Completed the 5-item hardening list: error handling (DataIntegrityViolation→409, catch-all→500), Testcontainers integration tests (transfer/insufficient-balance/duplicate-key + drift detection), scheduled reconciliation job (`findDriftedWallets`, balance vs SUM of legs), real-ish `wallet_number` scheme with bounded retry, and the Maven/Lombok-on-JDK-23 build fix (annotationProcessorPaths). Added withdrawal flow via a second system account (000000000002, outward). Set up CI + monorepo. PR#1 merged.
- Prediction(s) vs reality: ran create A,B → activate → top-up A 2000 → transfer A→B → replay. **All 5 written predictions matched**: A=3000/B=2000/internal=99,995,000, replay→409, and A+B+internal = exactly 100,000,000.
- The surprise + explanation: predict-then-run paid off — conservation held to the cent because it's a DB CHECK (`debited+credited=0`), not application logic. Lesson the hard way: never renumber an *applied* Flyway migration (V5→V6 rename would've broken dev on checksum mismatch — reverted).
- Teach-back given? reconstructed.
- New quiz questions added: Q02.
- Resumes at: gateway webhook (done next day).

### 2026-06-13 → 06-15 — wallet schema, service layer, HTTP layer, first E2E
- What I did: V1/V2 migrations (wallet + append-only ledger, all constraints live). Both JPA entities pass Hibernate `validate`. `WalletRepository` with `@Lock(PESSIMISTIC_WRITE)` + JPQL generating `SELECT … FOR UPDATE`. `WalletService.transfer` (validate → self-transfer guard → idempotency check → lock both wallets sorted larger-id-first → status/balance checks → move money → save one ledger row, all in one `@Transactional`). DTOs + controllers + `GlobalExceptionHandler` (ProblemDetail). Decided to KEEP the two-leg single-row ledger (rejected parent/child + total/available/hold — no concrete >2-leg or two-phase requirement; no gold-plating).
- Prediction(s) vs reality: first E2E run surfaced two bugs.
- The surprise + explanation: (1) `entry_id` was `varchar(20)` but a UUID is 36 chars → V3 widened it. (2) `topUp` self-invokes `transfer()`, so Spring's proxy never applies `transfer`'s `@Transactional` (self-invocation bypasses the proxy) → no tx → pessimistic lock throws `TransactionRequiredException` → 500. Fix = annotate `topUp` itself.
- Teach-back given? reconstructed. Lessons: FOR UPDATE (locking read vs plain read), waiting-vs-deadlock, lock-ordering to break deadlock cycles; snake_case fields break Spring Data derived queries → use `@Query`.
- New quiz questions added: Q01 (now a real scenario, was the placeholder).
- Resumes at: hardening list (06-16).

### 2026-06-11 — scaffolding
- What I did: AI-scaffolded `pom.xml` (+Lombok), `docker-compose.yml` (Postgres), `application.yml` (`ddl-auto=validate` + Flyway). App boots. Committed AI-agent rules (CLAUDE.md).
- Resumes at: schema + entities (06-13).
