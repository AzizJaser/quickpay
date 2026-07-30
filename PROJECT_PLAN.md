# QuickPay — Project Plan & Session Handover

> **Read this first.** This is the living plan for the whole project. It survives lost
> sessions and context resets. Whoever (human or AI) picks the project up should read
> this file, then act. **Keep it updated** — when a milestone lands or a decision is
> made, edit this file in the same commit.
>
> Last updated: **2026-07-30**

---

## ▶ NEXT ACTION (update this line every session)

**Write automated tests for `bill-service` — ONE test at a time.** It currently has
zero, so CI passes trivially and nothing guards the saga against regressions. This is
the single biggest quality gap in the project.

**Approach for the first pass:** service-layer integration tests (Testcontainers for
the real Postgres, like `WalletServiceIntegrationTest`) with the two HTTP clients
mocked — `@MockBean WalletClient` and `@MockBean BillerClient`. Fast, and it targets
the logic that actually carries risk (guards, state transitions, `resolve` branching)
rather than HTTP plumbing. Wiremock can come later if real-HTTP coverage is wanted.

**Write them in this order, one per sitting, reviewed before moving on:**
1. `createPayment` dedup — same client `Idempotency-Key` returns the same record, no
   second row; a new key creates a new one.
2. `reserveFunds` happy — wallet returns an entry id → bill becomes `Reserved` and
   stores `entry_id`.
3. `reserveFunds` declined — wallet client throws `ReserveDeclinedException` → bill
   becomes `Rejected`, `entry_id` stays null.
4. `resolve` PAID → capture called once, bill `Paid`.
5. `resolve` FAILED → reverse called once, bill `Rejected`.
6. Sweep picks up a `Reserved` bill, inquires, and resolves it.

⚠️ Reuse the wallet's hard-won test lesson: the Testcontainers DB is shared across test
methods with no rollback, so **every test needs unique idempotency keys and CIFs**.

---

## 1. What this project is

A learning lab: a miniature SAR digital-wallet platform, built end-to-end and then
deliberately broken. It mirrors the learner's real job (designing transaction flows).

**The learner (Abdulaziz) writes ALL application code.** The AI is a blunt senior
reviewer + explainer, and may only scaffold: the mock simulators, Docker Compose,
build config (`pom.xml`) when asked, and docs. Full rules in `CLAUDE.md`.

### Non-negotiable working rules
1. **The golden rule** — money is never created or destroyed, and the *database*
   enforces it (constraints), not trust in application code.
2. **Predict-then-run** — before any test or migration run, the learner writes the
   predicted outcome first, then runs and compares. This is the single highest-value
   habit in the project; it has caught many real bugs.
3. **No gold-plating** — max 4 services, no Kubernetes, no Spring Cloud.
4. **Review-gated phases** — no phase starts before the previous deliverable passes review.
5. **Applied migrations are immutable** (ADR-0003) — never edit one that has run; fix forward.

---

## 2. Target state (from `quickpay-project-brief.md`, docs branch)

Sponsor = a Riyadh fintech founder. Seven business requirements:

| # | Requirement | Status |
|---|---|---|
| 1 | Accounts — customers hold a SAR wallet (thin auth) | 🟡 wallets ✅, **no auth** |
| 2 | Top-up via async gateway; callbacks may duplicate or never arrive | ✅ done (HMAC webhook + idempotency) |
| 3 | P2P transfer, instant; app auto-retries after 5s | ✅ done (idempotency key makes retry safe) |
| 4 | Bill payment via slow (≤60s) flaky biller; never lose customer money, never show "paid" falsely | ✅ **done 2026-07-01** |
| 5 | Notifications on every completed transaction; channel fails often, must never block a payment | ⛔ **not started** |
| 6 | History + monthly statement per customer | ⛔ not started |
| 7 | Golden rule — every movement explainable, finance will audit | ✅ structurally enforced |

**Non-functional:** P2P targets 500 TPS at peak; every payment op safe to retry; full audit trail.
**Stretch:** nightly reconciliation of gateway records vs wallet records.

### Hard constraints
- Spring Boot 3 + PostgreSQL, **one database per service**
- **RabbitMQ** for async messaging (Kafka is a different project)
- Docker Compose only — no K8s, no Spring Cloud
- **Maximum 4 services** (currently using 2)
- External gateway + biller are mocks (AI-built, permitted scaffolding)

---

## 3. Baseline — what exists today

### Modules
| Module | Port | DB | State |
|---|---|---|---|
| `wallet-service` | 8080 | `wallet` @ 5432 | ✅ complete, 8/8 tests green, in CI |
| `bill-service` | 8081 | `bill` @ 5433 | ✅ feature-complete, **0 automated tests** |
| `scaffolding/gateway-simulator` | 9090 | — | mock payment gateway (HMAC webhooks) |
| `scaffolding/biller-simulator` | 9091 | — | mock biller (force PAID/FAIL/SERVER_ERROR/TIMEOUT) |

Simulators mock *external* systems and do **not** count against the 4-service budget.

### Wallet service (the money core)
- **Two-leg single-row ledger**: one row holds both sides; `CHECK(debited + credited = 0)`
  makes a one-sided money move impossible to commit. (ADR-0001)
- Money = BIGINT minor units. Ledger is **append-only** — reversals are new rows.
- Idempotency = client-supplied key + `UNIQUE(idempotency_key)`.
- Concurrency = pessimistic `FOR UPDATE`, wallets always locked in a consistent order
  (deadlock prevention by lock ordering, not timeouts).
- Reconciliation job asserts `balance == SUM(ledger legs)` per wallet.
- Migrations **V1–V10**.

**System accounts:**

| Number | Name | `is_internal` | `allows_negative` | Role |
|---|---|---|---|---|
| `000000000001` | Inward / TopUp | ✅ | ✅ | money source (negative by design) |
| `000000000002` | Outward | ✅ | ❌ | withdrawal sink |
| `000000000003` | Suspense | ✅ | ❌ | holds reserved funds mid-bill |
| `000000000004` | Biller settlement | ✅ | ❌ | receives captured bill funds |

Conservation invariant: `inward + customers + suspense + outward + biller = 0`.

#### Wallet baseline — settled vs still open (2026-07-30)

**The wallet's money model is BASELINED and will not be redesigned.** Settled: the
two-leg ledger and DB-enforced conservation, idempotency, lock ordering, the
reconciliation job, the four system accounts and the `is_internal`/`allows_negative`
model, reserve/capture/reverse, V1–V10, 8/8 tests in CI. Nothing remaining on this plan
requires rethinking any of it — future work builds *on top*.

**But the wallet service is NOT closed.** Four planned items will reopen its code:
1. **Notifications (req 5)** — the wallet must *publish an event* when money moves
   (RabbitMQ publisher, likely an outbox). New code inside the wallet, not just a new service.
2. **Load test (Phase 8)** — targets P2P at 500 TPS, which *is* the wallet; expect index,
   pool and lock-contention work.
3. **Sabotage (Phase 7)** — aimed largely at the money core; expect findings.
4. **Auth (req 1)** — the wallet API is currently wide open (see Bucket D).

Plus two smaller ones: **history/statement** may live in the wallet (undecided — that's
the service-budget call), and **V9's `UNIQUE(reverses_entry_id)` has no automated test**.

### Bill service (the saga)
```
create (Pending, write-ahead, COMMITTED first)
  → reserve  (customer → suspense)         → Reserved
  → call biller →  PAID    → capture (suspense → biller) → Paid
                   FAILED  → reverse (suspense → customer) → Rejected
                   UNKNOWN → leave Reserved (do NOT guess)
  → EOD sweep re-inquires stranded Reserved bills and finishes them
```
Key properties: **write-ahead intent**, **move money first / flip status last**,
`@Async` so the slow biller never blocks the customer, and `resolve(bill, result)` —
one shared method both the live path and the sweep funnel through.

**Two identifiers, deliberately distinct:**
- `billReference` — issued by the biller, on the customer's bill. Answers *which bill*.
  Reused across attempts → **never** dedupe on it.
- `paymentId` — generated per payment record. The biller's dedup/inquire handle, and
  the seed for wallet keys (`r`/`c`/`v` + hyphen-stripped id, 33 chars to fit `varchar(36)`).
- Inbound `Idempotency-Key` header (client-supplied, UNIQUE) → one request = one record.

**All four biller outcomes verified live:** PAID→capture, FAILED→reverse,
UNKNOWN(503)→parked, UNKNOWN(timeout)→**recovered by the sweep**.

---

## 4. Phase status (brief's 0–8 plan)

| Phase | Deliverable | Status |
|---|---|---|
| 0 | Sponsor interrogation → decisions log | ⛔ skipped (log still empty) |
| 1 | Data-ownership map | ⛔ skipped |
| 2 | Decomposition + 3 sequence diagrams | 🟡 partial — bill-payment + gateway-webhook diagrams exist; no P2P diagram, no boxes-and-arrows |
| 3 | Schemas (DDL) | ✅ wallet V1–V10, bill V1 |
| 4 | Contracts (OpenAPI + message schemas) | 🟡 endpoints exist; **no OpenAPI, no async schemas** |
| 5 | Walking skeleton in Compose | ✅ done, CI runs `mvn verify` |
| 6 | Build flows | 🟡 top-up ✅ P2P ✅ withdrawal ✅ webhook ✅ bill-pay ✅ — **notifications ⛔** |
| 7 | Sabotage (~12 scenarios, predict vs outcome log) | ⛔ not started (ad-hoc only) |
| 8 | Load test (k6, find breaking TPS on P2P) | ⛔ not started |

**Key framing:** almost every gap is *additive*, not *corrective* — nothing built has to
be torn up to reach the target. The baseline conforms to the target architecture.

---

## 5. Open decisions (resolve deliberately, not by accident)

1. **⚠️ RabbitMQ divergence.** The brief lists RabbitMQ as a hard constraint. The bill
   service uses in-memory `@Async` instead (guarded by the EOD sweep). Two honest options:
   **(a) remediate** — introduce RabbitMQ for notifications (and possibly the biller step);
   **(b) change the target** — formally decide `@Async` + DB sweep is sufficient at this
   scale and log it as an architecture/sponsor decision. *Leaving it undeclared is the
   only wrong answer.*
2. **Service budget.** Max 4; currently 2 (wallet, bill). Notifications would be #3.
   Does history become #4, or live inside an existing service? This is Phase-2
   decomposition work — decide before building.
3. **How much design debt to repay?** Phases 0–2 and 4 were skipped. They're the
   upfront-design muscle the brief exists to train (and map closely to TOGAF ADM
   deliverables). Legitimate to backfill, or to consciously accept the debt.
4. **Auth.** Requirement 1 mentions thin JWT auth; nothing exists. Services are wide open.

---

## 6. Remaining work — ordered increments

Work in **one increment per session**. Do not open several at once.

### Immediate
- [x] ~~Merge `feat/biller-simulator` → `main`~~ — **done 2026-07-30 via PR #3.**
      `main` now contains bill-service, wallet V7–V10, and both simulators.

### Bucket A — finish the build (Phase 6)
- [ ] **◀ IN PROGRESS — Bill-service automated tests.** Currently zero; CI passes
      trivially. See the NEXT ACTION section at the top for the approach and the
      ordered list of six tests. **Write ONE at a time, reviewed before the next.**
- [ ] **Notifications (req 5)** — the natural home for **RabbitMQ**. Must never block or
      fail a payment. Likely service #3.
- [ ] **History / statement (req 6)** — decide service #4 vs inside wallet first.

### Bucket B — prove it (Phases 7–8)
- [ ] **⚠️ Traceability — correlation ids in logs. DO THIS *BEFORE* THE SABOTAGE PASS.**
      Not a nicety: sabotage deliberately breaks a system spanning three processes, an
      `@Async` thread and a scheduled sweep. Answering *"what happened to this one
      payment?"* means correlating log lines across bill-service, wallet and the
      simulator — archaeology without a shared id. It is also the one cross-cutting
      concern that is **expensive to retrofit** (touches every log statement and every
      outbound call), unlike auth which is a single filter.
      *Cheap version:* accept-or-generate an `X-Correlation-Id` at the edge → put it in
      MDC → add to the log pattern → forward as a header on both `RestClient`s.
      *Already have (accidentally):* the wallet's idempotency keys embed the paymentId
      (`r`/`c`/`v` + hyphen-stripped id), so a bill's ledger legs are already findable
      from its `paymentId`. That audit trail exists — it just isn't in the logs.
- [ ] **Sabotage pass (Phase 7)** — ~12 failure scenarios, a written prediction for each,
      run, and explain every surprise. Formalizes what's been done ad hoc.
- [ ] **Load test (Phase 8)** — k6 against P2P, find breaking TPS, fix, explain.
      Note: this reopens the **wallet** (indexes, pool sizing, lock contention).

### Bucket D — cross-cutting concerns (deliberately deferred, NOT forgotten)

**Decision (2026-07-30): defer what is additive, do early what is pervasive.**

- [ ] **Auth / security (req 1) — DEFERRED to near the end, on purpose.**
      Both services are currently wide open (no authn/authz on any endpoint).
      *Why defer:* it is purely additive — a filter in front of the controllers that
      touches no schema, no ledger, no saga logic, so nothing built now becomes wrong
      when it lands. The brief itself says keep it thin (learner has built JWT before),
      so the learning value is low. And adding it now puts a token in front of every
      manual curl and every sabotage scenario — friction on the exact loop producing
      the learning.
      *Risk being accepted:* "later" can become "never." If it is still undone when the
      project wraps, **close it explicitly** as out of scope rather than letting it rot.
- [ ] **Structured logging** — logging exists (`logger.info/warn/error`) but is
      unstructured and uncorrelated. Pairs with the correlation-id item in Bucket B.

### Bucket C — design debt (Phases 0–2, 4)
- [ ] Sponsor interrogation + decisions log (Phase 0)
- [ ] Data-ownership map (Phase 1)
- [ ] Boxes-and-arrows + P2P sequence diagram (Phase 2)
- [ ] OpenAPI + async message schemas (Phase 4)

### ⚠️ Known test-coverage boundary — mocks prove *reaction*, not *plumbing*

The bill-service tests use `@MockBean` on `WalletClient` / `BillerClient`. Timeouts and
5xx are simulated by telling the mock to **throw** (`ResourceAccessException`,
`HttpServerErrorException`), which correctly tests **our reaction** — bill stays
`Reserved`/`Pending`, and `verifyNoInteractions` proves no money moved.

**These tests do NOT cover, and green CI must not be read as covering:**
- that the **read timeout is actually configured** on the `billerRestClient` bean — if
  `.withReadTimeout(...)` were deleted, the mock tests still pass while production
  hangs forever
- that `.onStatus(422, no-throw)` correctly turns a real 422 into a `FAILED` body
- that cross-service DTO field names match the other service's JSON
  (the `billNumber`/`reference`, `original_entry_id` traps)

Those are real-HTTP concerns; only **Wiremock** (a stub server that can delay or return
503) would guard them. Deliberately deferred.

**Manually verified, but unguarded against regression:** the full TIMEOUT path was
proven by hand on 2026-07-02 (biller settled PAID after the caller gave up → EOD sweep
inquired → captured → `Paid`, capture applied exactly once).

- [ ] *(optional, later)* Wiremock tests for the two HTTP clients to close this gap

### Small deferred refinements
- [ ] Bill NOT_FOUND age policy: pending > 24h → reverse + raise ops ticket (not built;
      an unused `LocalDateTime` import in `EODReconciliationJob` marks the spot)
- [ ] "OPS ticket" is currently just a log line
- [ ] V9 `UNIQUE(reverses_entry_id)` has no automated test (needs a shared-suspense
      double-reverse case)
- [ ] `POST /bill/create` is a debug-only endpoint; could be removed
- [ ] Endpoint `POST /bill/reserve` actually runs the whole payment — misleading name

---

## 7. How to run & verify

```bash
# databases (both)
docker compose up -d                      # quickpay-wallet-db :5432, quickpay-bill-db :5433

# then start in IntelliJ:
#   QuickpayWalletApplication   -> 8080
#   QuickpayBillApplication     -> 8081
#   BillerSimulatorApplication  -> 9091
#   (gateway simulator          -> 9090, only for top-up webhook work)

# tests (no standalone mvn on this machine — use IntelliJ's bundled maven)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
"/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn" -pl wallet-service test
```

**Smoke test — full happy path** (create wallet → activate → fund → pay a bill):
```bash
# 1. wallet
curl -s -X POST localhost:8080/v1/wallets -H 'Content-Type: application/json' \
  -d '{"cif":"9900000001","walletName":"smoke"}'
curl -s -X PUT localhost:8080/v1/wallets/activate/<walletNumber>
curl -s -X POST localhost:8080/v1/transfer/top-up -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: k1' -d '{"wallet_number":"<walletNumber>","amount":5000}'

# 2. pay a bill  -> responds Reserved instantly, flips to Paid async
curl -s -X POST localhost:8081/bill/reserve -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: k2' \
  -d '{"billReference":"BILL-1","walletNumber":"<walletNumber>","amount":2000}'
```

**Force biller outcomes** (drives the FAILED / UNKNOWN branches):
```bash
curl -s -X POST localhost:9091/simulate/mode -H 'Content-Type: application/json' \
  -d '{"outcome":"NORMAL"}'     # or FAIL | SERVER_ERROR | TIMEOUT
curl -s localhost:9091/simulate/state
```

**Inspect state:**
```bash
docker exec quickpay-wallet-db psql -U wallet -d wallet -c \
  "select wallet_number,balance,is_internal,allows_negative from wallet where is_internal;"
docker exec quickpay-bill-db psql -U bill -d bill -c \
  "select payment_id,status,entry_id,amount from bill order by created_at desc limit 5;"
```

---

## 8. Where the other documents live

- **`quickpay-project-brief.md`** — sponsor requirements, phase plan, sponsor decisions log
- **`learning-log.md`**, **`learning-playbook.md`** — method + quiz questions
- **`adr/`** — ADR-0001 two-leg ledger, ADR-0002 system accounts may go negative,
  ADR-0003 applied migrations immutable, ADR-0004 split `is_system`
- **`docs/diagrams/`** — bill-payment and gateway-webhook sequence diagrams

⚠️ **All of the above live on the `docs/adr-and-learning-docs` branch, NOT on `main`.**
`PROJECT_REPORT.txt` (a fuller prose snapshot) is on `feat/biller-simulator`.

---

## 9. Hard-won lessons (do not relearn these)

- Flyway executes the copy in `target/classes`, not `src` — rebuild before re-running a changed migration.
- Never edit an applied migration → checksum mismatch on next boot (ADR-0003).
- snake_case entity fields break Spring Data derived queries — use camelCase fields mapped to snake columns.
- `@Enumerated` with no argument defaults to **ORDINAL** — always `@Enumerated(EnumType.STRING)`, and match enum constant *case* to the DB CHECK.
- Native Postgres `ENUM` types fight Hibernate — use `varchar` + `CHECK`.
- A boolean field named `internal` → getter `isInternal()` → JPA expects column `internal`. Pin with `@Column(name=...)`.
- `@Transactional` / `@Async` **self-invocation** (`this.method()`) bypasses the proxy — call across a bean boundary.
- `Optional` is never `null` — use `isPresent()` / `orElse`.
- Cross-service DTO field names must match the *other* service's JSON exactly, or Jackson silently sends `null`.
- Idempotency keys must fit `varchar(36)` — strip hyphens, 1-char prefix.
- Two beans of the same type (two `RestClient`s) need name-matched injection or `@Qualifier`.
- A read timeout must be configured, or a hung HTTP call blocks forever and the timeout exception never fires.
- Shared Testcontainers DB + no rollback ⇒ every test needs unique idempotency keys and CIFs.

---

## 10. Plan change log

| Date | Change |
|---|---|
| 2026-07-30 | Plan created. Bill-payment phase complete and re-verified; merge to `main` pending. |
| 2026-07-30 | Cross-cutting decisions recorded (Bucket D): **auth deferred** (additive, low learning value, adds friction to every test) but **traceability/correlation-ids moved ahead of the sabotage pass** (pervasive, expensive to retrofit, and Phase 7 is unreadable without it). Added the wallet baseline note — money model settled, service still reopened by notifications, load test, sabotage and auth. |
| 2026-07-30 | `feat/biller-simulator` merged to `main` via PR #3 — bill-service, wallet V7–V10 and both simulators are now on `main`. NEXT ACTION moved to bill-service automated tests (six tests, one at a time). |