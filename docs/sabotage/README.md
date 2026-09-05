# Phase 7 — Sabotage

The synthesis exam. Kill, duplicate, delay, flood — and explain every surprise.

**Roadmap topic #7.** Success criterion: *"12 scenarios run, most predicted, every surprise
explained."* Note what that asks for — not "no surprises", but **no unexplained ones**.

Runs on the **three-service system** (wallet · bill · notification). The four-services gate
was dropped on 31 Aug: history #4 is parked to the Kafka project, and the entire
distributed-transaction surface is already here to break. #4 gets its own addendum when it
is born.

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