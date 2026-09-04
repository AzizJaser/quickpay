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

## 7 · Follow-on scenarios this suggests

*(pending)*

---

## 8 · Log evidence (preserved)

*(pending)*
