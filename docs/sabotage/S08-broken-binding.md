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

Binding deleted (`204`), three bill payments run, binding restored.

```
S08-001  Paid  bill.payment.paid  marked_sent = TRUE
S08-002  Paid  bill.payment.paid  marked_sent = TRUE
S08-003  Paid  bill.payment.paid  marked_sent = TRUE

notifications delivered: 0
errors logged:           0
broker counters:  publish_in 20312 / publish_out 20302  -> unroutable 7 -> 10
```

**After restoring the binding:**

```
recovered:                        0
S08 events still marked sent:     3 of 3
a NEW payment:                    delivered normally
```

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | the relay will not notice — RabbitMQ accepts it | **✓ exactly** — zero errors, zero warnings, a completely normal-looking trace | ✓ |
| 2 | `sent_at` written, and it is a lie | **✓** — 3 of 3 marked sent for messages that were destroyed | ✓ |
| 3 | *not sure* whether the counters move | **they moved: 7 → 10**, matching the three lost messages exactly. **The only trace anywhere in the system** | — |
| 4 | events lost; unsent ones go through normally | **✓ precisely** — 0 recovered, 3 still marked sent, and a new payment delivered fine | ✓ |
| 5 | a sync call from notification back to bill would catch it | see §6 — a reconciliation, and the right *class* of answer | — |

### 🔴 THE FINDING — the outbox guarantees delivery TO THE BROKER, not to a consumer

Every earlier scenario self-healed once the dependency returned. **This one cannot**, and
the reason is structural: the outbox's contract is *"the broker accepted this message"*.
RabbitMQ **did** accept it — then discarded it, because a topic exchange with no matching
binding drops what it receives. That is not an error; it is defined behaviour.

So the relay did its job correctly, marked `sent_at` correctly, and the message ceased to
exist. **There is no retry, because from the outbox's point of view nothing failed.**

Three customers were never told their bills were paid, and **nothing in the system will ever
notice.** The seven losses from S03 have sat undetected in the counters for four days — the
scenario proving itself before it was even run.

### The number nothing computes

```
bill events marked SENT:        20,237
bill events actually RECEIVED:  20,230
                       gap:          7
```

*(The broker reports 10 unroutable; this cross-database count says 7. Exact attribution
needs more care — some earlier losses may have been dropped consumer-side for other reasons.
The **existence** of a gap is the point.)*

That comparison spans **two databases owned by two services**, which is precisely why nobody
computes it.

---

## 5 · Did the golden rule hold?

| check | after | agree? |
|---|---|---|
| held in suspense (identity) | 12 | ✓ |
| suspense account balance | 12 | ✓ |
| drifted wallets | 0 | ✓ |

**Money was never at risk — and that is the point.** The ledger never depended on the broker,
so every constraint held perfectly while three customers silently lost their notification.

⚠️ **The invariants protect money. Nothing protects the customer's knowledge.** Eight
scenarios have hammered the golden rule and it has not moved once; the first scenario to
touch the event pipeline lost data immediately and silently.

---

## 6 · What I would fix, and whether I fixed it

**NOT FIXED — but this is the strongest unaddressed finding of Phase 7.**

Three candidate fixes, in ascending order of strength:

**1. Detect it — `publish_in − publish_out`.** Already proven to work: it moved 7 → 10, and
it is the *only* signal that exists. Cheap, and a Phase 8 dashboard metric. But it detects
**after the loss**, tells you nothing about *which* messages, and would not have prevented
this.

**2. Prevent it — publisher confirms + the `mandatory` flag.** With `mandatory`, RabbitMQ
**returns** an unroutable message to the publisher instead of discarding it. The relay would
then know the publish failed and could leave `sent_at` null, making the existing retry work.
**This turns a silent loss into an ordinary retryable failure** — the strongest fix, and it
closes the gap between "the broker accepted it" and "somebody will receive it".

**3. Reconcile it — the learner's proposal.** A periodic check that what the bill service
marked sent was actually received. This is a *different class* of answer: not a detector but
an **end-to-end agreement**, the same shape as the settlement window. It is the only option
that catches losses occurring *beyond* the broker — a consumer that drops a malformed
payload, say — which neither (1) nor (2) would see.

**Recommendation, not built:** (2) prevents the failure class entirely and is a
configuration change plus a branch in the relay. (3) is the honest long-term answer but
needs a cross-service contract and is more machinery than Phase 7 should build. (1) is
nearly free and belongs on the Phase 8 dashboard regardless.

⚠️ **Deliberately left unfixed so the decision is recorded rather than reflexive** — but this
one is different from the circuit breaker: **the harm is measured, reproducible, and
currently undetectable.** The breaker had no measured harm; this has 7 (or 10) lost customer
notifications sitting in production data.

---

## 7 · Follow-on scenarios

- **S08b — with `mandatory` enabled.** Break the binding again and confirm the relay leaves
  `sent_at` null and retries. The before/after that would prove fix (2), same discipline as
  S07b.
- **S09 — late settlement.** Violate the settlement window deliberately.
- **S10 — consumer-side loss.** Malformed payload the listener drops: a loss that
  `mandatory` cannot see, and only reconciliation would catch.

---

## 8 · Log evidence

```
the entire bill-service trace for S08-BILL-1..3 — every line INFO, zero errors:
  09:03:34.171 INFO [S08-BILL-1] WalletService : ledger with entry id ...
  09:03:34.214 INFO [S08-BILL-2] WalletService : ...
  (the relay logged its usual "event ... is sent with correlation id ..." for all three)

the ONLY trace of the loss, in the broker:
  publish_in 20312 / publish_out 20302  ->  unroutable 7 -> 10

after restoring the binding:
  recovered 0 · S08 events still marked sent 3 of 3 · new payment delivered normally
```
