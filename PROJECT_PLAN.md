# QuickPay — Project Plan & Session Handover

> **Read this first.** This is the living plan for the whole project. It survives lost
> sessions and context resets. Whoever (human or AI) picks the project up should read
> this file, then act. **Keep it updated** — when a milestone lands or a decision is
> made, edit this file in the same commit.
>
> Last updated: **2026-07-30**

---

## ▶ NEXT ACTION (update this line every session)

**Merge `feat/biller-simulator` into `main`.** It is verified (both happy and failure
paths re-tested 2026-07-30) but `main` is still stale at 2026-06-17 and does not
contain the bill-service at all. After that, pick the next increment from §6.

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
- [ ] **Merge `feat/biller-simulator` → `main`** (~20 min). Verified; `main` is 11 commits behind.

### Bucket A — finish the build (Phase 6)
- [ ] **Bill-service automated tests** — currently zero; CI passes trivially.
      Recommended first pass: service-layer tests with `WalletClient`/`BillerClient`
      mocked (fast, covers dedup, reserve→Reserved, decline→Rejected, resolve
      PAID→Paid / FAILED→Rejected, sweep picks up a Reserved bill). Wiremock later if
      HTTP coverage is wanted. **Write ONE test at a time.**
- [ ] **Notifications (req 5)** — the natural home for **RabbitMQ**. Must never block or
      fail a payment. Likely service #3.
- [ ] **History / statement (req 6)** — decide service #4 vs inside wallet first.

### Bucket B — prove it (Phases 7–8)
- [ ] **Sabotage pass (Phase 7)** — ~12 failure scenarios, a written prediction for each,
      run, and explain every surprise. Formalizes what's been done ad hoc.
- [ ] **Load test (Phase 8)** — k6 against P2P, find breaking TPS, fix, explain.

### Bucket C — design debt (Phases 0–2, 4)
- [ ] Sponsor interrogation + decisions log (Phase 0)
- [ ] Data-ownership map (Phase 1)
- [ ] Boxes-and-arrows + P2P sequence diagram (Phase 2)
- [ ] OpenAPI + async message schemas (Phase 4)

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