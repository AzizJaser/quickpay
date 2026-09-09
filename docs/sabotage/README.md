# Phase 7 — Sabotage

The synthesis exam. Kill, duplicate, delay, flood — and explain every surprise.

**Roadmap topic #7.** Success criterion: *"12 scenarios run, most predicted, every surprise
explained."* Note what that asks for — not "no surprises", but **no unexplained ones**.

Runs on the **three-service system** (wallet · bill · notification). The four-services gate
was dropped on 31 Aug: history #4 is parked to the Kafka project, and the entire
distributed-transaction surface is already here to break. #4 gets its own addendum when it
is born.

---

## 📄 [**PHASE 7 FINAL REPORT**](PHASE7_REPORT.md) — 14 runs, ~56% predicted, 4 fixes built,
the circuit breaker deliberately not among them. Read this before the individual records.

---

## Rules

1. **Write the prediction first, and save it.** A prediction written afterwards is a
   description. This is the project's standing rule and it is the only thing that turns a
   run into learning.
2. **One file per scenario**, from `_TEMPLATE.md`. Rolling logs are deleted after 7 days;
   these files are the record.
3. **Check the golden rule every time.** Money is never created or destroyed — verify it
   from the ledger *and* from the account balances, independently. Two counts that agree
   are evidence; one count is an assumption.
4. **Fixes get earned.** A protection is added because a scenario produced the pain, not
   because it seemed prudent. Record the decision either way.
5. **Every surprise gets explained.** An unexplained surprise means the mental model is
   still wrong — that is the finding, and it outranks the scenario that produced it.

---

## Scenarios found while building — start here

These are not hypotheticals. Each was observed during construction, with evidence.

| # | Scenario | Why it matters | Earns |
|---|---|---|---|
| ~~S01–S03~~ | ✅ **RUN — see the index below.** Three scenarios chased the circuit breaker: S01 could not earn it (a dead process refuses connections in ~1 ms), S02 could not test it (`inquire` was a bare map lookup), S02b **earned** it (10 s/pass, 166 calls to a failing biller) — and **S03 withdrew that verdict** by removing ~80% of the cost with bulk inquiry. | **The lesson: before adding a mechanism to MANAGE a cost, ask whether the cost can be REMOVED.** Had the breaker been built after S02b, it would have been protecting against a problem a design change was about to eliminate — and it would have looked like it was working. | topic #5 **still open** |
| **S04** | **Hold outstanding too long.** In S01 money left the wallet at 09:38 and returned at 09:49 with **nothing told to the customer** — correct per policy (a `HOLD` is not customer-facing), but unbounded. Confirmed twice: **a bill cannot expire while the biller is unreachable or unresponsive**, because the settlement-window check lives inside `resolve`, which a throwing `inquire` never reaches. | | "hold outstanding > N" alert |
| | **Break a binding — the silent drop** | Measured live: `publish_in 79` vs `publish_out 72`. Seven messages accepted by the exchange and routed nowhere, **no error**, and an outbox reporting success. | `publish_in − publish_out` as a Phase 8 metric; publisher confirms + `mandatory` as the loud fix |
| | **Late settlement — violate the window** | The settlement window is a *contract*, not evidence. Set the biller's `delayMs` above `biller-settlement-window-ms` and a settlement arrives **after** the revert: the customer is refunded and the biller still expects payment. | Reconciliation must surface the discrepancy; nothing currently does |
| | **Orphan holds** | The expiry policy lives in the **caller** (`BillService.resolve`), so a hold placed straight through `POST /v1/transfer/hold` has no owner and never expires. One is in the database now. | A wallet-side backstop sweep — a guard that does not depend on callers behaving |

---

## Scenario ideas beyond those

Kill · duplicate · delay · flood, applied to each seam:

- **Kill mid-saga** — between the wallet hold and the biller call; between publish and
  `sent_at`; between the listener's provider call and its state write.
- **Duplicate** — replay the same idempotency key; redeliver the same `message_id`; run two
  sweeps concurrently.
- **Delay** — provider slower than the read timeout; broker slow to accept; database lock
  held across a transfer.
- **Flood** — bill events swamping the shared queue (**head-of-line blocking**, deliberately
  left unfixed so this scenario can earn the split); the retry job's 100-row batch against a
  large backlog.
- **Break a dependency entirely** — RabbitMQ down while the wallet commits transfers (does
  the outbox hold?); the notification DB down while messages arrive.

---

## Index

| # | Scenario | Date | Predicted? | Surprises | Fix earned |
|---|---|---|---|---|---|
| [S01](S01-biller-down.md) | biller down 5 min | 4 Sep | 4 of 5 | scenario's own premise falsified — connection-refused is ~1 ms, not a timeout | **none** — deliberately |
| [S02](S02-hung-biller.md) | hung biller (slow `pay`) | 4 Sep | 3 of 4 | scenario **could not test what it was built for** — `inquire` was a bare map lookup | **none** — plus a real bug: `RestClientException` escaping `payBiller` |
| [S02b](S02b-slow-inquire.md) | slow on **both** paths | 4 Sep | 1 of 4 | golden rule was never at risk — correctness comes from DB constraints, not timing | breaker **EARNED**: 10 s/pass, 166 calls to a failing biller |
| [S03](S03-bulk-inquiry.md) | **bulk inquiry** — remove the cost instead of managing it | 5 Sep | 2 of 3 | **S02b's verdict WITHDRAWN** — batching removed ~80% of what earned the breaker | breaker **NOT built**; topic #5 stays open |
| [S04](S04-hold-outstanding.md) | hold outstanding too long | 5 Sep | 2 of 5 | the settlement-window assumption falsified a **third** time, by a third failure mode | **none** — quantified an already-accepted risk (5 min, 42 SAR, 0 notifications) |
| [S05](S05-slow-under-timeout.md) | slow but UNDER the timeout | 6 Sep | 3 of 4 | **slow-but-working is INVISIBLE** — a 300× slowdown produces byte-identical logs. And S03's batching had incidentally fixed a scheduler-starvation problem nobody noticed | **none** — argues for *instrumentation*, not protection: a failure-count breaker is blind to this |
| [S12](S12-duplicate.md) | **duplicate** — 4 seams, 20 concurrent attacks | 9 Sep | 4 of 5 | 🔴 **a duplicate settle reports as `400 insufficient balance`** — neither guard built for it fired; the suspense account emptying stopped it, and the same fault reports 400 or 409 depending on unrelated account state. 🟢 **Every code-level check caught 0 of 10 in the race; the DB constraints caught 9 of 9** | **none** — the thesis measured: code checks are latency optimisations, constraints are the guarantee |
| [S11a](S11-biller-enforces-window.md) | **biller enforces the window** | 8 Sep | 1 of 3 (1 untested) | 🟢 **S09's divergence eliminated by changing NO application code** — a one-sided timeout became a two-sided contract and the loss disappeared. First run where "the golden rule held" is checked **externally** too: 0 SAR difference | ✅ **BUILT** (scaffolding). Still open: the contract has a number but **no anchor** — two clocks, two start events |
| [S10](S10-consumer-side-loss.md) | **consumer-side loss** (2 arms) | 8 Sep | 3 of 6 | **the retry cap is missing where it is needed and lethal where it is not** — `attempts` never increments when the provider *throws* (frozen at 0, unbounded), and where it does increment, 5 × 5 s = **25 seconds destroys a notification permanently**. Also: `mandatory` is structurally blind here, and arm A's reconciliation is blind to arm B | **none built** — but reconciliation **EARNED**, needing *two* queries; ⚠️ fix order matters: budget before counter |
| [S09](S09-late-settlement.md) | **late settlement** | 8 Sep | 3.5 of 5 | **the golden rule HELD and money was still lost** — both counts derive from the wallet's own ledger, so they cannot see a debt to the biller. And the settlement window is a contract **only one side knows about** | **none** — deepest finding: needs a real contract or reconciliation *against the biller* |
| [S08](S08-broken-binding.md) | **break a binding** | 7 Sep | 3 of 4 | **the outbox guarantees delivery to the BROKER, not to a consumer** — 3 messages destroyed, `sent_at` marked, zero errors, and **nothing recovers them**. The invariants protect money; nothing protects the customer's knowledge | ✅ **detection BUILT & verified** (`mandatory` + returns callback naming the `event_id`); recovery deferred past Phase 7 |
| [S07b](S07b-pool-size.md) | **pool size 2 — proving the fix** | 7 Sep | 2 of 4 | the gap **vanished** (39.3 s → 2.0 s max) rather than shrinking 55% — and fixing the blocking **revealed a 50 events/s relay ceiling** that was invisible behind it | ✅ **FIX PROVEN & KEPT** — first built-and-verified fix of Phase 7 |
| [S07](S07-huge-backlog.md) | **10,000-bill backlog** | 6 Sep | 2 of 5 | **the relay STOPPED for 39.3 s** while 7,186 notifications waited — two unrelated jobs share one scheduler thread. Nothing broke; it degraded | ✅ **FIRST EARNED FIX**: `scheduling.pool.size` (and a batch cap as a second measure) |
| [S06](S06-large-backlog.md) | 200-bill backlog in one request | 6 Sep | 1 of 5 | **the eligibility boundary MOVES during a pass** — `now()` is evaluated per bill, so a sliding 60 s cutoff swept up 53 extra bills mid-pass; and the split is **non-deterministic** because the query has no `ORDER BY` | **none** — the suspected cap has *no evidence*: 200 refs in one body, 0.73 s per pass, relay unaffected |