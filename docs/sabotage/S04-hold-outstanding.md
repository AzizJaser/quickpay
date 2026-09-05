# S04 — how long can money stay held with nobody told?

| | |
|---|---|
| **Date** | 2026-09-05 |
| **Services** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation prefix** | `S04-` |
| **Roadmap topic** | #7 — the first scenario where the system may be behaving **exactly as designed and still be wrong** |

**Why this run exists.** In S01 the customer's money left their wallet at 09:38 and returned
at 09:49 — eleven minutes — with **no message at any point**. That is correct per the
notification policy (a `HOLD` is not customer-facing) but nothing bounds it: a six-hour
outage behaves identically. Confirmed: **nothing in either service watches hold age.**

**Baseline:** 0 `Reserved` · held = 12 (known orphan) · wallet `005100000001` = 1870.

---

## 1 · What I am breaking

Biller forced to `SERVER_ERROR` (503) so every `inquire` throws — the same "no answer"
condition as a stopped process, which S01 and S02b both showed prevents the settlement
window from firing. Submit bills, leave them stranded several minutes, and observe what the
**customer** and an **operator** can each see.

---

## 2 · PREDICTION — written before the run

| # | Question | Prediction | Confidence |
|---|---|---|---|
| 1 | Over ~5 min of outage, how many messages does the customer get? | "Every bill amount will be refunded and they will receive a notification, same number as the bills" | medium |
| 2 | What would an operator see? | "Not sure — no operator screens yet" | high |
| 3 | Does anything change at 1 min vs 10 min? | "If all the bills got refunded, nothing will happen" | high |
| 4 | Is this a bug? | "Not a bug, because it wasn't implemented" | guessing |
| 5 | If it needs fixing — alert, notification, or policy? | "The held bills will be refunded after the settlement window" | high |

⚠️ **Recorded before running:** predictions 1 and 5 both assume the settlement window
expires these bills. **S01 (claim 5) and S02b (Q2/Q3) both tested that and both falsified
it** — the window lives inside `resolve`, which is never reached when `inquire` throws. This
run is the third test of the same assumption.

---

## 3 · What actually happened

Biller forced to `SERVER_ERROR` (503) at 11:51:03, three bills submitted, left stranded.

```
T+0      3 bills submitted -> all Reserved, 42 SAR held
         wallet 005100000001: 1870 -> 1828

T+60s    Reserved x3   held=54 (42 + the 12 orphan)   notifications=88 (+0)
T+180s   Reserved x3   held=54                        notifications=88 (+0)
T+300s   Reserved x3   held=54                        notifications=88 (+0)
```

**Five minutes — five times the settlement window — and:**

| | |
|---|---|
| bills resolved | **0** |
| customer notifications | **0** |
| customer money held | **42 SAR** |
| operator signal | one log line per pass: `cannot reach the biller — 3 bills unresolved` |

That log line says **nothing about how long, and nothing about how much money**.

⚠️ **Run hygiene:** the biller simulator died partway through, so the "biller recovers" half
of the scenario did not execute. The five-minute observation above is unaffected — that is
precisely the condition being tested — but recovery was not measured here (it was in S01
and S02b).

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | every bill refunded, one notification each | **✗ zero refunds, zero notifications** | ✗ |
| 2 | no operator screens | **✓** — the only signal is a log line, and an uninformative one | ✓ |
| 3 | once all refunded, nothing happens | premise never occurred (nothing was refunded) | — |
| 4 | not a bug, just unimplemented | **✓ and already recorded as an accepted risk** — see §6 | ✓ |
| 5 | refunded after the settlement window | **✗ — third falsification of this same assumption** | ✗ |

### 🔴 The same assumption, falsified three times, three different ways

| scenario | failure mode | prediction | result |
|---|---|---|---|
| S01 claim 5 | process stopped (connection refused) | window fires regardless | ✗ |
| S02b Q2/Q3 | read timeout | window fires | ✗ |
| **S04 pred 5** | **503 from a live biller** | **window fires** | **✗** |

**The window is not a timer. It is a condition checked only when the biller ANSWERS.**

```java
try {
    results = billerClient.inquiries(references);   // throws
} catch (...) { log; return; }                      // <- exits here
for (...) { billService.resolve(bill, result); }    // <- never reached
                                                    //    and the window lives INSIDE resolve
```

Three transport failures, one outcome: no answer, no `resolve`, no window, no refund.

### Why "just refund when the biller is unreachable" is wrong

S02 is the counterexample, from this project's own evidence. `TIMEOUT` mode settles **PAID**
after the caller has given up — all five bills there genuinely ended `Paid`. Had the sweep
refunded during that window, the customer would have been refunded **and** the biller owed:
money destroyed from the platform's side.

**"Unreachable" is exactly when you know least.** It cannot distinguish:

- the payment never arrived → refunding is correct
- the payment arrived and is processing → refunding destroys money
- the payment completed, you just cannot see it → refunding destroys money

Two of three say do not refund. Holding is the safer half of a genuinely hard trade.

---

## 5 · Did the golden rule hold?

| check | before | during | agree? |
|---|---|---|---|
| held in suspense (identity) | 12 | 54 | ✓ |
| wallet 005100000001 | 1870 | 1828 | ✓ (42 held, exactly the three bills) |

No money created or destroyed — held, and correctly accounted for.

---

## 6 · What I would fix, and whether I fixed it

**NOTHING FIXED. The decision was already made and stands.**

This is recorded in `PROJECT_PLAN.md` (3 Sep) as an accepted residual risk — *"the expiry
policy lives in the CALLER, so any caller that does not implement one strands customer
money"* — and deliberately scoped out.

**So S04 discovered nothing new. What it did was put numbers on a known decision:**

> *"we know holds can be long"* → *"5 minutes, 3 bills, 42 SAR, zero notifications, and one
> log line that mentions neither duration nor amount"*

An accepted risk with a measurement behind it is worth more than the same risk in the
abstract — that is the whole value of this run.

**Candidates recorded, none built:**

- **Cheapest, highest value:** add duration and amount to the existing sweep log line —
  `3 bills held for 5m12s, 42.00 SAR outstanding, oldest since 11:51:04`. No schema, no
  event, no new code path, and it turns the one thing an operator cannot currently see into
  something greppable and alertable.
- **A `bill.payment.delayed` event** was costed and rejected as out of scope: it needs a
  threshold check, an "already notified" flag (migration + column, or a 6-hour outage sends
  thousands of messages), and — the real cost — **a new write path**, because
  `BillOutcomeService.recordOutcome` is built around a *status change* and a delayed bill
  does not change status. **Every event in the system today is terminal; this would be the
  first non-terminal one.**
- **A long-horizon refund policy** (unresolvable for 24 h → refund and flag) is a deliberate
  trade of a small double-payment risk against not holding money indefinitely. Real systems
  make it — explicitly, at hours or days, not at 60 seconds.

---

## 7 · Follow-on scenarios

- **S05 — biller slow but UNDER the timeout** (1.5 s vs a 2 s timeout). Everything "works"
  while every pass pays the latency. Still the open question for topic #5.
- **S06 — break a binding** (the measured `publish_in` vs `publish_out` gap): an outbox that
  reports success while the broker drops the message.
- **S07 — late settlement**: violate the settlement window deliberately and watch a
  settlement arrive after the revert.

---

## 8 · Log evidence

```
11:57:28.364 ERROR [reconciliation-bills-60c99699] EODReconciliationJob : cannot reach the biller — 3 bills unresolved
11:57:38.379 ERROR [reconciliation-bills-d4655e16] EODReconciliationJob : cannot reach the biller — 3 bills unresolved
11:57:48.396 ERROR [reconciliation-bills-652bd61f] EODReconciliationJob : cannot reach the biller — 3 bills unresolved

every 10 s for the whole outage; no mention of age or amount.
notifications table unchanged at 88 rows throughout.
```
