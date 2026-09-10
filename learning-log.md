# Learning Log

**Owner:** Abdulaziz · **Method:** see learning-playbook.md (Rules 4 & 6 — this file is the memory of the whole system)
**AI instructions:** Read this file at the start of every session. Use the question bank for weekly retain-session quizzes (sample PAST weeks only, never the current one). After each session, the learner appends one entry — remind them if they forget.

---

## Current state

| Field | Value |
|---|---|
| Active project / phase | QuickPay — Phase 6 (build flows). Wallet service DONE & merged to `main`. **Bill-payment flow DESIGNED (2026-06-19 → 06-21), not yet built.** Bill payment = a SEPARATE service (service #2). |
| Current loop (playbook Rule 7) | Bill-payment **design loop CLOSED**. Open next: **build it** — write predictions first (Rule 2), and write the **bill-service schema ADR** (decides: one shared vs per-bill suspense account; idempotency strategy pending the **biller's contract**). Still partly open: backfill Phases 1–2 (data-ownership map + decomposition) now that service #2 is real. |
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
| Q06 | A bill is stuck `pending`; your EOD sweep re-runs "move temp→biller" and the move isn't idempotent. Walk the double-spend, then name what makes retry safe (and why "retry until success" alone is not). | Distributed tx / exactly-once | 2026-06-21 | — | — |
| Q07 | Capture (temp→biller) and reverse (temp→customer) are mutually exclusive — only one ever runs per bill. Why can they still NOT share one idempotency key? What goes wrong on the replay? | Idempotency / data modeling | 2026-06-21 | — | — |
| Q08 | One `is_system` boolean gates both "internal account" and "may go negative." A suspense account must be internal but never negative. Explain why one flag can't express that, and the fix. | Schema / constraints | 2026-06-21 | — | — |
| Q09 | After splitting into `is_internal` + `allows_negative`, which flag must the transfer balance-check skip use — and what bug appears if you pick the other one? | Schema / app-DB agreement | 2026-06-21 | — | — |
| Q10 | Why guard a reversal entry by `reverses_entry_id` (UNIQUE) instead of a synthetic idempotency key — and what new CHECK does making `idempotency_key` nullable then force? | Data modeling / reversals | 2026-06-21 | — | — |

> Q06–Q10 seeded 2026-06-21 from the bill-payment **design** loop (no bug-run — design reasoning). Reword in your own terms so they're yours.

---

## Entries

*(newest at top)*

> **Note:** the two entries below (06-19, 06-21) are AI-drafted at my request on 2026-06-21 from the
> session transcripts, so they capture *what was decided* faithfully but the **predictions and
> teach-backs were not written live** — both were design sessions with no experiment run. Predictions
> are owed at BUILD time per Rule 2. Reword the quiz questions in my own terms.

### 2026-06-21 — Session B — bill-payment design: completion (review-driven)
- What I did: resumed a dropped thread (the bill-payment design from 06-19 wasn't in AI memory). Recovered context from the `docs/adr-and-learning-docs` branch + transcripts. Took the temp/suspense-account sequence diagram through a blunt review and drove it to a complete, defensible design:
  - **Ordering:** record intent (`pending`) BEFORE moving money; never flip status to terminal until the move is confirmed (move-first-then-flip). Flip-first would strand money in temp; move-without-record orphans it.
  - **State machine:** bill record `PENDING → CAPTURED(PAID) | REJECTED`; terminal states immutable.
  - **Exactly-once = at-least-once retry + idempotent consumer** (same mechanism as the gateway webhook). The EOD sweep is itself a retry, so the money move MUST be idempotent or it double-spends.
  - **Idempotency keys:** reserve (customer→temp)=key_1; capture (temp→biller)=key_2 — each a forward op with its OWN key. **Reverse is NOT given a synthetic key** — guarded by `reverses_entry_id` (UNIQUE) pointing at the original entry (chose option (a): explicit column + queryable audit link). Consequence: `idempotency_key` becomes nullable (Postgres UNIQUE treats NULLs as distinct), which forces a CHECK that exactly one of (`idempotency_key`, `reverses_entry_id`) is non-null.
  - **Suspense account must NEVER go negative** (pass-through) → `balance >= 0` enforced → reverse-after-capture fails at the DB = golden-rule backstop behind the state machine.
  - **Split the `is_system` flag** into `is_internal` (not a customer wallet) + `allows_negative` (money source; ONLY inward = true). New constraint `CHECK(balance >= 0 OR allows_negative)`.
  - Wrote **ADR-0004** (system account types) — Proposed; *refines* ADR-0002 (doesn't supersede). Assumes a single shared suspense account; one-vs-many deferred to the bill-service schema ADR.
- Prediction(s) I wrote and what actually happened: none — design session, no experiment run. Predictions owed at build (esp. "temp balance after capture" and "what a forced reverse-after-capture does").
- The surprise, and my explanation of it: **`is_system` was secretly TWO concepts** ("internal" vs "may go negative") that only coincided by accident on the inward account; the suspense account is the first account that breaks the coincidence. Also: "retry until success" is NOT safe on its own — the retry is the very thing that re-runs the money move, so it only works *because* the consumer is idempotent.
- Teach-back given? Partial / live — not a formal teach-back, but I drove the key insight myself: a reversal is defined by the entry it undoes, so guard it by `entry_id`, not a fresh key. AI's main correction: capture and reverse can't share an idempotency key even though only one runs (replay returns the wrong stored result); and the balance-check skip must follow `allows_negative`, not `is_internal`.
- New quiz questions added to bank: Q06–Q10.
- Open questions / where the next session resumes: **build the bill-payment flow** (predictions first). Then the **bill-service schema ADR** — must resolve (1) one shared vs per-bill suspense account, (2) the idempotency strategy, which depends on the **biller's contract** (does it dedupe on a client-supplied reference?). Also still owed: Phases 1–2 backfill now that service #2 is real.

### 2026-06-19 — Session A — ADR/continuity setup + bill-payment design (started)
- What I did: (1) set up the continuity machinery — scaffolded `adr/` with a template, added the "Method & continuity" section to `CLAUDE.md`, AI-filled ADR-0001 (two-leg ledger), 0002 (system accounts may go negative), 0003 (applied migrations immutable) from memory + git (I take over future ADRs), updated `quickpay-project-brief.md` + `learning-log.md`; committed all docs to branch `docs/adr-and-learning-docs` (kept local, not pushed). (2) Finished the `@Size` on the webhook DTO. (3) **Started the bill-payment design**: decided bill payment is a **separate service** (multiple billers/integrations don't belong in the core wallet), and iterated the sequence diagram from a naive "hold/deduct/release" through to the **temp/suspense-account** model with a `pending` record, biller inquiry on 5xx/timeout, and an EOD sweep.
- Prediction(s) I wrote and what actually happened: none — design session.
- The surprise, and my explanation of it: I pushed back hard on "define an idempotency key" because **I don't own the biller** — I can send any reference, but I can't know if the biller *stores/dedupes* on it. Conclusion: the idempotency strategy can't be chosen until the **biller's contract** is known. (I got frustrated mid-session — "I give up" — then the temp-account framing made it click: hold money in a suspense account, resolve to capture or reverse.)
- Teach-back given? No (design exploration).
- New quiz questions added to bank: — (seeded next session as Q06–Q10).
- Decisions logged: bill payment = separate service; wallet exposes **hold** (new API, like withdraw), **reverse** (new API), **capture** (a simple transfer); the bill service owns its own customers + their biller account numbers. EOD/ops rule: act immediately on PAID, no waiting; if pending > 1 day → reverse (business call) + raise a payment-ops ticket; reconciliation backstops it.
- Open questions / where the next session resumes: tighten the design's failure modes (→ done 06-21).

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
