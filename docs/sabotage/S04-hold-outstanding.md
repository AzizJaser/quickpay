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

*(pending)*

---

## 4 · Prediction vs reality

*(pending)*

---

## 5 · Did the golden rule hold?

*(pending)*

---

## 6 · What I would fix, and whether I fixed it

*(pending)*

---

## 7 · Follow-on scenarios

*(pending)*

---

## 8 · Log evidence

*(pending)*
