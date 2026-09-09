# S01 — biller down for five minutes

| | |
|---|---|
| **Date** | 2026-09-04 |
| **Services running** | wallet 8080 · bill 8081 · notification 8082 · gateway-sim 9090 · biller-sim 9091 · provider-sim 9093 |
| **Correlation id prefix** | `S01-` |
| **Related roadmap topic** | #5 sagas, timeouts, circuit breakers |

---

## 1 · What I am breaking, and how

Take the **biller simulator (9091) down entirely** for five minutes while bills are being
paid — process stopped, so calls fail at the connection rather than returning an error
status. Bills will be submitted before and during the outage. The biller is then brought
back up and the system left to recover on its own.

Baseline before the run is clean: a bill payment reaches `Paid` and the customer is
notified on the first attempt.

---

## 2 · PREDICTION — written before the run

**Learner's prediction, verbatim (4 Sep, before execution):**

> "It will be checked again within the settlement window. The sweep will check the bills
> within the window and inquire about them with the biller. The money will be refunded to
> the customer's wallet. The amount deducted then refunded."

**Restated as testable claims:**

| # | Claim |
|---|---|
| 1 | The sweep keeps re-checking affected bills |
| 2 | The sweep inquires with the biller for each `Reserved` bill |
| 3 | The customer's money is refunded — hold released back to their wallet |
| 4 | Net effect on the customer: amount deducted, then refunded |

**Timing, pinned down before the run (verbatim):**

> "If it passed the settlement window it doesn't matter if the biller got back or not."

| # | Claim |
|---|---|
| 5 | **The refund fires once the settlement window (60s) has passed, whether or not the biller is back.** The outage does not delay it. |

**Confidence: medium.**

**Parameters:** `biller-settlement-window-ms` = 60000 · sweep `eod-interval-ms` = 10000 ·
so a bill should be inquired ~6 times before the window closes.

---

## 3 · What actually happened

**Timeline** — biller stopped, two bills submitted 09:38:12, biller returned ~09:49:4x.

```
09:38:12   two bills submitted, biller DOWN     both -> Reserved
09:38–09:49  sweep runs every 10s, every inquire fails (connection refused)
             T+20s   Reserved  held=42  balance=2020
             T+40s   Reserved  held=42  balance=2020
             T+60s   Reserved  held=42  balance=2020   <-- settlement window passed
             T+90s   Reserved  held=42  balance=2020
             T+150s  Reserved  held=42  balance=2020
09:49:42   biller back -> FIRST sweep pass refunds BOTH
             RELEASE 10, RELEASE 20
             held=12  balance=2050   (12 = pre-existing orphan hold)
```

Both customers were notified: two `bill.payment.rejected`, both `SENT`, sharing one
`reconciliation-bills-ac1dd740` run id — one sweep, two bills.

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | The sweep keeps re-checking affected bills | it did — every 10s, 112 failed inquires logged | ✓ |
| 2 | The sweep inquires with the biller for each `Reserved` bill | yes, both bills every pass | ✓ |
| 3 | The money is refunded to the customer's wallet | yes — two `RELEASE` rows, balance restored | ✓ |
| 4 | Net effect: amount deducted, then refunded | exactly that | ✓ |
| 5 | **The refund fires once the window passes, whether or not the biller is back** | **✗ FALSIFIED** — at T+150s (2.5× the window) both bills were still `Reserved` and the money still held | ✗ |
| — | *(second prediction)* refund on the first sweep pass after the biller returns | **✓** — released 09:49:42, on the first pass | ✓ |

### Why claim 5 was wrong — the mechanism

The settlement-window check lives **inside `resolve`**, and `resolve` is only ever called
with a `BillerResult`:

```java
BillerResult result = billerClient.inquire(...);   // throws while the biller is down
billService.resolve(bill, result);                 // never reached
```

When `inquire` throws, the sweep catches it and logs. **`resolve` never runs, so the window
is never consulted.** A bill can only expire on a pass where the biller *answers* —
"the window has passed" is necessary but not sufficient; you also need a reply of
`NOT_FOUND`.

That is not a bug. Reverting without an answer would be reverting on **no evidence at
all** — strictly worse than waiting. But it makes the guarantee weaker than assumed:
**a bill cannot expire while the biller is unreachable, however long the outage lasts.**
Customer money stays held for the full duration, and nothing tells them.

### 🔴 SURPRISE — the premise of this scenario was wrong

This scenario was written expecting *"every call burns a full timeout"*. It did not:

```
09:49:12.019  bill 1
09:49:12.021  bill 2     <-- 2 ms apart
09:49:22.032 / .033      <-- 1 ms
09:49:32.044 / .045      <-- 1 ms
```

**~1 ms per failed inquire, not 2 s.** A stopped process sends TCP RST, so the connection is
*refused* immediately — there is no timeout to burn. The 2 s read timeout only applies once
a connection is **accepted**.

So a dead dependency is the **cheap** failure. The expensive one is a dependency that
accepts connections and never answers — a hung service, a black-holed route, an
overloaded queue. That is where *N* bills × 2 s per pass actually materialises.

**This changes what a circuit breaker is for here.** It is not needed to protect against a
dead biller — that fails fast and costs nothing. It is needed for the *hung* biller. The
scenario that earns it is S02, not this one.

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (type identity) | 12 | 12 | ✓ |
| suspense account balance | 12 | 12 | ✓ |
| wallet 005100000001 balance | 2050 | 2050 | ✓ (2020 while held) |
| drifted wallets | 0 | 0 | ✓ |

Two independent counts agree, and no wallet drifted. **Money was never created or
destroyed** — only held and returned.

---

## 6 · What I would fix, and whether I fixed it

**The failure argues for two things, and only one of them is a circuit breaker.**

**(a) Nothing tells the customer during the outage.** Money left their wallet at 09:38 and
came back at 09:49. For eleven minutes they saw a debit and no bill paid, with no message.
Correct per the notification policy (a `HOLD` is not customer-facing), but the *duration* is
unbounded — an outage of hours behaves the same way. **Not fixed. Candidate: notify when a
hold has been outstanding longer than N.**

**(b) A circuit breaker — but NOT for this failure.** Connection-refused is ~1 ms, so
breaking the circuit saves nothing measurable. **Decision: do not add Resilience4j on the
strength of this scenario.** It has not been earned yet. **S02 (hung biller)** is the
scenario that would earn it, and it should be run before deciding.

*This is the earned-fixes rule doing real work: the scenario I wrote to justify a circuit
breaker turned out not to justify it.*

---

## 7 · Follow-on scenarios this suggests

- **S02 — hung biller (accepts, never answers).** `TIMEOUT` mode with a delay far above the
  read timeout. This is where *N* bills × 2 s per sweep pass appears, and where the sweep
  stops keeping up with its own interval. **The real circuit-breaker scenario.**
- **S03 — outage longer than a customer would tolerate.** How long can money stay held with
  nobody told? Argues for a "hold outstanding > N" alert.
- **S04 — biller returns with a settlement it accepted before dying.** Here the biller had
  no record; if it *had*, the refund would already have happened and the settlement would
  arrive afterwards — the late-settlement discrepancy.

---

## 8 · Log evidence (preserved)

```
112 failed inquires logged during the outage (every 10s, two per pass)

09:41:25.220 ERROR [reconciliation-bills-10a822c3] EODReconciliationJob : unable to connect to the biller ...
09:41:35.224 ERROR [reconciliation-bills-c7222708] EODReconciliationJob : unable to connect to the biller ...
09:41:35.225 ERROR [reconciliation-bills-c7222708] EODReconciliationJob : unable to connect to the biller ...

recovery — first pass after the biller returned:
09:49:42.173  RELEASE 10   (S01-001)
09:49:42.213  RELEASE 20   (S01-002)
both -> bill.payment.rejected -> SENT, correlation reconciliation-bills-ac1dd740
```
