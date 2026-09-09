# Phase 7 — Sabotage: final report

**4–9 September 2026 · 14 documented runs · three services, one broker, three databases**

> **Criterion (roadmap topic #7):** *"12 scenarios run, most predicted, every surprise
> explained."* Note what it asks for — not "no surprises", but **no unexplained ones.**

**Verdict: met.** 14 records in `docs/sabotage/`, every prediction written and committed
*before* its run, every surprise traced to a mechanism.

---

## 1 · The headline

**The golden rule held in all 14 runs.** Money was never created or destroyed — not under a
five-minute outage, not under a 10,000-bill backlog, not under 20 concurrent duplicate
attacks, not when a message was destroyed by the broker, not when the biller and the platform
reached opposite conclusions about the same payment.

**And the phase's most valuable finding is that this was never as reassuring as it looked.**

Three runs, in sequence, dismantled it:

- **S09** — the golden rule held *while money was lost*. The held-money identity and the
  suspense balance are both derived from the wallet's own ledger. Two counts of the same book
  cannot disagree about a debt the book does not record.
- **S10** — the invariants protect money; **nothing protects the customer's knowledge.** A
  paid bill, a delivered message, and a customer who will never be told.
- **S12** — under concurrency, **every code-level check caught 0 of 10** while the database
  constraints caught 9 of 9. The guarantee was never in the code.

The correct reading of "the golden rule held, 14 times" is not *the system is safe*. It is
**the money invariants are strong and narrow, and most of what can go wrong lives outside
them.**

---

## 2 · Scoreboard

| # | scenario | date | predicted | fix earned |
|---|---|---|---|---|
| S01 | biller down 5 min | 4 Sep | 4 / 5 | none — *deliberately* |
| S02 | hung biller (slow `pay`) | 4 Sep | 3 / 4 | none — found a real bug instead |
| S02b | slow on **both** paths | 4 Sep | 1 / 4 | breaker **earned** |
| S03 | bulk inquiry | 5 Sep | 2 / 3 | breaker **withdrawn**; batching built |
| S04 | hold outstanding too long | 5 Sep | 2 / 5 | none — quantified a known risk |
| S05 | slow but under the timeout | 6 Sep | 3 / 4 | none — argues for instrumentation |
| S06 | 200-bill backlog | 6 Sep | 1 / 5 | none — suspected cap had no evidence |
| S07 | 10,000-bill backlog | 6 Sep | 2 / 5 | ✅ **`scheduling.pool.size`** |
| S07b | pool size 2 — proving it | 7 Sep | 2 / 4 | ✅ **fix proven & kept** |
| S08 | break a binding | 7 Sep | 3 / 4 | ✅ **`mandatory` + returns callback** |
| S09 | late settlement | 8 Sep | 3.5 / 5 | none — deepest finding |
| S10 | consumer-side loss (2 arms) | 8 Sep | 3 / 6 | reconciliation **earned**, not built |
| S11a | biller enforces the window | 8 Sep | 1 / 3 (1 untested) | ✅ **two-sided contract** |
| S12 | duplicate — 4 seams | 9 Sep | 4 / 5 | none — the thesis measured |

**Prediction accuracy: 34.5 of 62 ≈ 56%.**

That number is the point. A prediction rate near 100% would mean the scenarios were too easy
to be worth running; near 0% would mean the mental model was absent. **At ~56%, roughly half
the runs taught something that could not have been reasoned out from the code** — and the
misses were consistently more valuable than the hits.

---

## 3 · What was actually changed

Four things in six days. Every one has a measurement behind it.

**1. Bulk inquiry (S03).** Per-reference polling made the sweep's cost scale with its backlog
— 5 stranded bills cost 10 s per pass, growing linearly. One batched call costs the same as
one single call. **Removed ~80% of the cost that had just earned a circuit breaker.**

**2. `spring.task.scheduling.pool.size: 2` (S07 → S07b).** Two unrelated `@Scheduled` jobs
shared one thread. The relay **stopped for 39.3 seconds** while 7,186 notifications waited.
With pool size 2 the gap did not shrink — it **vanished** (2.0 s maximum, zero gaps over 3 s,
no duplicates across 20,297 `message_id`s).

**3. `mandatory` + a returns callback (S08).** Deleting a binding makes RabbitMQ accept and
silently discard. The relay logged nothing, marked `sent_at`, and restoring the binding
recovered nothing. The callback now names the lost `event_id`. **Detection only — recovery
deliberately deferred.**

**4. The biller enforces the settlement window (S11a).** Scaffolding, not application code —
and that is the finding. `resolve`, the sweep, the wallet and the window value are
byte-identical to S09. **A one-sided timeout became a two-sided contract and the loss
disappeared.**

---

## 4 · What was deliberately NOT built — and why that matters more

### The circuit breaker: three scenarios, never built

This is the earned-fixes rule doing its hardest work.

| run | outcome |
|---|---|
| **S01** | could not earn it — a dead process refuses connections in ~1 ms; breaking a circuit on a 1 ms failure saves nothing |
| **S02** | could not *test* it — `inquire` was a bare map lookup, so the sweep was immune to biller latency |
| **S02b** | **earned it** — 10 s per pass, 166 calls to a failing biller |
| **S03** | **withdrew the verdict** — batching removed ~80% of the measured cost |
| **S05** | showed a failure-count breaker is **blind** to a 300× slowdown that never fails |

**Had the breaker been built after S02b, it would have been protecting against a problem a
design change was about to eliminate — and it would have looked like it was working.**

*Before adding a mechanism to MANAGE a cost, ask whether the cost can be REMOVED.* Roadmap
topic #5 remains open, and that is the correct state.

### Also not built, each with a measured reason

- **An `ORDER BY` on the sweep** — the one change S06 supports.
- **A batch cap** — S06 found no evidence for the cap that was suspected.
- **Latency metrics / slow-call thresholds** — what S05 actually argues for.
- **Reconciliation** — earned by S10, deferred because it needs *two* queries and a start
  boundary, not one query and enthusiasm.

---

## 5 · The six lessons

### 1. Two counts that share a source are one count

S09's structural finding. `SUM(HOLD) − SUM(SETTLEMENT) − SUM(RELEASE)` and the suspense
balance both read the wallet's own ledger. They agreed perfectly while the platform owed the
biller 77 SAR that no record anywhere described. **A "second independent check" that shares a
source is not independent.**

### 2. The invariants protect money; nothing protects the customer's knowledge

S08 destroyed 3 messages with zero errors. S10 arm A dropped a notification 26 ms after
receiving it, with one `WARN` in a log deleted after 7 days. S10 arm B drove two notifications
to a terminal state that nothing ever revisits. **In every case the money was correct.**

### 3. A contract needs three terms, and we had one

S09: the settlement window lived only in the bill service's config — *a unilateral timeout
wearing a contract's clothes*. S11a fixed the number and exposed the rest:

| term | agreed after S11a? |
|---|---|
| the number (60,000 ms) | ✅ |
| the event it is measured from | ❌ `bill.created_at` vs request arrival |
| whose clock measures it | ❌ never discussed |

**Both sides can honour "60 seconds" perfectly and still disagree.**

### 4. Fix ordering can invert a fix's value

S10's sharpest practical finding. `attempts` never increments when the provider *throws*, so
the retry is unbounded. The obvious fix is to move the increment. **Doing that first would
convert an unbounded retry into guaranteed permanent loss in 25 seconds**, because
`maximum-retries: 5` × `resend-interval: 5 s` is the real defect. **Budget before counter.**

### 5. Detection is not recovery

The `mandatory` callback built in S08 catches broker-side loss the instant it happens — and is
structurally blind to S10's consumer-side loss, where routing was perfect. Meanwhile S10's
set difference found **all 10 message losses in the project's history**, including the ones
S08 had concluded were unrecoverable. *The messages were gone; the record of their loss never
was.* **Detect live, reconcile after — they are complementary, not competing.**

### 6. An error can protect the money and still lie about why

S12. Ten concurrent settles produced nine `400 insufficient balance` — neither the discharge
check nor `uq_entry_discharged_once` fired. The **suspense account running out of money**
stopped them. Money perfect; diagnosis pointing at the wrong system entirely. And
state-dependent: with more money in suspense, the identical fault reports 409. **The invariant
that protects the money is not the one that explains it.**

---

## 6 · Open decisions, ranked

| # | decision | evidence | note |
|---|---|---|---|
| 1 | **Retry budget = duration, not count** | S10: 5 × 5 s = **25 s** destroys a notification permanently | ⚠️ **before** the `attempts` fix |
| 2 | **Reconciliation, two queries + a start boundary** | S10: 10 orphans and 11 terminal-`FAILED` rows, **9 undetected since August**; join key already exists | no new schema |
| 3 | **`expiresAt` in the pay request** | S11a: two clocks, two start events | application code |
| 4 | **`mandatory` recovery half** | S08: unmark `sent_at` so the relay retries | decide the bound first |
| 5 | **Reject vs replay a duplicate key** | S12: a timed-out client gets 409 and still cannot learn whether its money moved | the biller already replays |
| 6 | **Translate the duplicate-settle error** | S12: same fault reports 400 or 409 | |
| 7 | **Circuit breaker / topic #5** | S01–S05 | **stays open — correctly** |

---

## 7 · What Phase 7 could NOT test

Stated plainly, because a report that only lists what was found overstates its coverage.

- **Kill mid-saga** — never run. The relay crashing between publish and `sent_at` is the
  gap; S12 seam 4 proved the resulting duplicate delivery would be safe, but the crash itself
  is untested.
- **Flood / head-of-line blocking** — one queue, two bindings, left unsplit deliberately so a
  scenario could earn the split. S07's backlog stressed the *relay*, not the consumer queue.
- **The clock sliver (S11c)** — a settlement landing between the two windows. S11a's Q2 is
  recorded **untested**, not answered.
- **Contract rollout order (S11b)** — adding `EXPIRED` to the biller before the bill service
  can deserialize it would throw on the whole bulk inquiry and strand every bill in that pass.
- **Database failure** — Postgres down mid-transfer, disk full, connection-pool exhaustion.
- **Real concurrency at scale** — S12 used 10 parallel requests. Phase 8 (k6) is where TPS
  limits get found.

---

## 8 · Reviewer errors, on the record

Recorded because a report that hides its own mistakes is not evidence.

- **S06 — quoted 57.7 ms/resolve** by averaging across a span containing a 10 s idle gap.
  Real figure **7.6 ms**.
- **S08 — ran a verification against a service that had not been restarted**, so no
  UNROUTABLE lines appeared while the broker counters showed messages destroyed. Accidentally
  reproduced the exact bug under test.
- **S11a — the first run was invalid.** Used `delayMs` to make the biller decide late, which
  also slows `inquire`; the sweep's calls timed out, `resolve` was never reached, and the bill
  stranded — reproducing S01/S04 instead of testing the window.
- **S11a — repeated the S09 mistake at smaller scale**, putting the window number in the
  biller's config and choosing the anchor unilaterally. The learner caught it: *"but this
  window is a contract between the biller and bill."*
- **S12 — speculated a concurrency interleave** to explain `attempts=2` on a delivered
  message. The real cause was the provider's configured `failureRate: 0.3`.

---

## 9 · Where the value came from

**Not from the scenarios that confirmed the design.** From the ones that broke their own
premises:

- **S01** was written expecting each failed call to burn a 2 s timeout. Measured: **~1 ms.** A
  dead dependency is the *cheap* failure; a hung one is expensive. That inverted what the
  circuit-breaker work was for.
- **S02** could not test what it was built for, because `inquire` was a bare map lookup.
- **S03** withdrew the verdict S02b had just earned.
- **S06** found the suspected cap had no evidence, and found a moving eligibility boundary
  nobody was looking for.
- **S09** confirmed its prediction about the platform and was falsified about the biller —
  which is where the finding was.

**Five of fourteen runs falsified a premise the run itself was built on.** That is the
strongest argument for the written-prediction rule: none of those five would have been
visible if the prediction had been written afterwards.

---

## 10 · Next

**Phase 8 — k6 load test.** Requires a written breaking-TPS prediction before the first run.

**Optional extras**, in descending value: **S11c** (the clock sliver — earns `expiresAt`),
**kill mid-saga**, **flood** (earns the queue split), **S11b** (contract rollout order).

*The money core has been attacked for six days and has not moved once. Everything still open
is about what happens around it.*