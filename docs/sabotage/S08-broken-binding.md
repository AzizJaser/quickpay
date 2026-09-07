# S08 — break a binding: the outbox reports success for messages that were destroyed

| | |
|---|---|
| **Date** | 2026-09-07 |
| **Correlation prefix** | `S08-` |
| **Roadmap topic** | #7 — the first scenario touching the **event pipeline** rather than the biller path |

**Why this run exists.** During S03 the counters showed `publish_in 79` vs `publish_out 72`
— **seven messages accepted by the exchange and routed nowhere**, with no error anywhere and
an outbox reporting success. Those seven are *still* in the counters four days later
(`20309` vs `20302`), which is itself the finding: nothing noticed, and nothing ever will.

**What makes this unlike every earlier scenario:** there is no failure. The relay publishes
successfully, marks `sent_at`, and moves on. RabbitMQ accepts the message and discards it
because no binding matches.

**Baseline:** 0 `Reserved` · 0 unsent events · `publish_in 20309` / `publish_out 20302`
(7 unroutable, all from S03).

**Bindings before:**

```
quickpay.events --[bill.payment.*]--> notification.money-events
quickpay.events --[wallet.money.*]--> notification.money-events
```

---

## 1 · What I am breaking

Delete the `bill.payment.*` binding while the system runs, then put bills through the full
flow. The bill service keeps publishing; the exchange keeps accepting; the messages are
dropped. Then restore the binding and see whether anything recovers.

---

## 2 · PREDICTION — written before the run

| # | Question | Prediction | Confidence |
|---|---|---|---|
| 1 | Does the relay notice? | **No — RabbitMQ will accept it** | high |
| 2 | What do the outbox rows say? | **`sent_at` will be written, and it is a lie — it was never delivered** | medium |
| 3 | Does `publish_in − publish_out` move? | *not sure* | — |
| 4 | Can the system recover when the binding is restored? | **No — those events are lost. Only the ones still unsent will go through normally** | guessing |
| 5 | What would have caught this? | **A synchronous call from notification back to bill — an end-of-day check that what was sent was actually received** | — |

⚠️ **Prediction 5 is a reconciliation proposal, and worth noting as such:** it says the
publisher cannot detect this alone and needs the *consumer* to confirm. That is a different
class of fix from a detector — it is an end-to-end agreement, the same shape as the
settlement window.

⚠️ **Prediction 4 is the one with teeth.** Every earlier scenario self-healed once the
dependency returned. This is the first where **the outbox has already declared victory**, so
there may be nothing left to retry.

---

## 3 · What actually happened

*(pending)*

---

## 4 · Prediction vs reality

*(pending)*

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense | 12 | | |
| suspense balance | 12 | | |
| notifications delivered vs events published | | | |

⚠️ **Note:** the golden rule is about *money*, and money will almost certainly be fine here —
the ledger never depended on the broker. **The thing at risk is the customer's knowledge**,
which no constraint protects.

---

## 6 · What I would fix, and whether I fixed it

*(pending)*

---

## 7 · Follow-on scenarios

*(pending)*

---

## 8 · Log evidence

*(pending)*
