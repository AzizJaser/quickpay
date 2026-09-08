# S11a — the biller enforces the settlement window

> Prediction must be written and saved **before** the run.

| | |
|---|---|
| **Date** | 2026-09-08 |
| **Services running** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation id prefix** | `S11-` |
| **Related** | direct follow-on to [S09](S09-late-settlement.md) |

---

## 1 · What I am changing, and why

S09 found the settlement window was never an agreement — `biller-settlement-window-ms` lived
only in the bill service's config, so the biller settled `PAID` 30 s after the platform had
reverted and refunded.

**S11a gives the biller the same number.** Past it, the biller refuses and stores **nothing**,
so a later inquiry returns `NOT_FOUND` and `resolve` already reverts a `NOT_FOUND` bill past
its own window. **Scaffolding only; no application code changed.**

```java
long elapsedMs = (System.nanoTime() - receivedAtNanos) / 1_000_000L;
if (elapsedMs > settlementWindowMs) {
    logger.warn("REFUSING to settle reference {} ... storing nothing");
    return new PaymentResult(..., "EXPIRED", null, "...refused");
}
settledByReference.put(req.reference(), result);   // only inside the window
```

### Two defects left in deliberately

**1. The contract has a number but no anchor.** Raised by the learner before the run:
*"but this window is a contract between the biller and bill."* The number is now agreed; the
**event it is measured from** and **whose clock measures it** are not. The bill service
measures from `bill.created_at`, the biller from when the request arrived — strictly later.

**2. `NOT_FOUND` is a lie.** The biller has a record — it refused. Right outcome, wrong
reason, and the refusal is not idempotent. An explicit `EXPIRED` status is S11b.

---

## 2 · PREDICTION  ⚠️ written before the run

Setup identical to S09: biller sleeps **90 s** (`delayMs: 90000`), bill window 60 s, one bill.

### Learner's predictions, verbatim

| Q | Prediction | Confidence |
|---|---|---|
| Q1 · does the S09 divergence disappear? | *"it will be same in S09"* | **high** |
| Q2 · does the clock mismatch matter? | *"it doesn't matter, if the bill is within the window it will accept the settle"* | **high** |
| Q3 · the refusal stores nothing | *"bill will be reject and wallet refunded"* | **high** |
| Q4 · does the golden rule hold? | *"no, bill is paid with provider and the fund is in the customer's wallet"* | **high** |

⚠️ **Flagged before the run:** all four rest on one assumption — that the biller still
settles. If that is wrong, all four move together. It is one claim tested four ways, not four
independent predictions.

**Q4 is an evolution from S09**, where the same question was answered *"yes, it will hold."*
The answer is now **"no"**, on the grounds that the biller is paid while the customer keeps
the funds — the S09 lesson internalised: the wallet's two counts cannot see a
platform-vs-biller imbalance.

**This run finally tests S09's prediction 3** — *"if the biller sticks with the agreement then
yes it will hold"* — which S09 could not test, because the biller never stuck to it.

---

## 3 · What actually happened

*(pending)*

## 4 · Prediction vs reality

*(pending)*

## 5 · Did the golden rule hold?

*(pending)*

## 6 · What I would fix, and whether I fixed it

*(pending)*

## 7 · Follow-on scenarios

*(pending)*

## 8 · Log evidence

*(pending)*