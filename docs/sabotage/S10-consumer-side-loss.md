# S10 — consumer-side loss (the blind spot in S08's fix)

> Prediction must be written and saved **before** the run.

| | |
|---|---|
| **Date** | 2026-09-08 |
| **Services running** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation id prefix** | `S10-` |
| **Related roadmap topic** | #7 sabotage — and the scenario that decides whether end-to-end reconciliation is earned |

---

## 1 · What I am breaking, and how

**Nothing.** The trigger is already latent in the data.

The notification service owns customer identity in its own `customers` table — a deliberate
design decision, not a gap: the platform is capped at **four services** and #4 is history,
so there was never a slot for a customer service. `V1__customer_and_processed_events_table.sql`
created `customers` on day one.

That table holds **two rows** (`5100000001`, `5100000002`). The wallet holds at least eight
distinct cifs. Every scenario S01–S09 happened to use the two known ones.

### Arm A — a cif the owner cannot resolve
Pay a bill from wallet `007700000001` (cif `7700000001`). Money moves normally; the bill
reaches a terminal state; the event is published and **routed correctly**. The listener then
cannot resolve the customer.

### Arm B — a customer that exists, but the provider is down
Stop the provider simulator (9093), pay a bill for cif `5100000001`, let the resend job run
~10 passes, then bring the provider back.

**Both arms are invisible to the S08 `mandatory` fix**, because nothing about the routing is
wrong. The message is delivered and acked. This is the layer past where that fix can see.

**The structural question:** both arms fail inside the same listener, and every `catch` there
ends in the word *"dropping"* — so nothing is ever requeued or dead-lettered. The only
difference between them is **where the failure lands relative to
`processedEventRepository.save(event)`.**

---

## 2 · PREDICTION  ⚠️ written before the run

### Arm A — learner's prediction, verbatim

> "The notification will not create row and it will throw CustomerNotFoundException."

Position also recorded, verbatim: **"I think it's not applicable"** — i.e. arm A's *local*
behaviour is deducible from reading `NotificationListener` and does not need a test to
confirm. **That is accepted as correct**, and is why arm A is run *cheap*: one bill, and the
measurement is deliberately pointed somewhere else.

⚠️ **Reviewer's note on the word "throw":** it throws and is **caught** three lines later.
The message is therefore **acked to RabbitMQ as successfully handled** — not requeued, not
dead-lettered. Queue depth returns to zero and every broker metric stays healthy. Flagged
before the run because "throws" and "is dropped after being caught" have very different
consequences at the broker, and only one of them is what happens.

### What arm A actually measures — NOT the listener

The listener's behaviour is agreed. The **cross-service** picture is the open question:

| # | Question | Predicted |
|---|---|---|
| A1 | Does the bill service's `outbox_notification` row get `sent_at` set? | **UNPREDICTED** |
| A2 | Does the S08 `mandatory` returns callback fire? | **UNPREDICTED** |
| A3 | Is there any query, anywhere, that can find the discrepancy afterwards? | **UNPREDICTED** |

Recorded honestly as unpredicted rather than back-filled — the same treatment S02's Q6 got.
**A3 is the one that decides whether end-to-end reconciliation is earned.**

### Arm B — to be predicted separately before that arm runs

Deliberately held back so arm A's result cannot contaminate it. The question already put on
the record:

> **Q5 · Provider down, `maximum-retries: 5`, resend every 5 s. After ten passes, what is
> `attempts` on that row and what state is it in?**
>
> Reviewer's stated position before the run: **not 5, and not `FAILED`.** Look at where
> `event.setAttempts(event.getAttempts() + 1)` sits in `deliver()` relative to the provider
> call that can throw.

---

## 3 · What actually happened — ARM A

```
09:16:28  bill S10-001 paid from wallet 007700000001 (cif 7700000001), 33 SAR
09:16:28  HOLD 33 -> SETTLEMENT 33      wallet 3000 -> 2967, bill -> Paid
09:16:30  relay publishes bill.payment.paid, marks sent_at
09:16:30  listener RECEIVES it, logs the message id and routing key
09:16:30  WARN "no customer for cif 7700000001 (message d0e99ad1...) - dropping"
          message ACKED. queue depth 0. no error anywhere.
T+5/10/20 bill=Paid  outbox=+1  processed_events=+0
```

**Four systems, four self-consistent views, one contradiction:**

| where | what it says |
|---|---|
| bill `outbox_bill_notification` | `sent_at = 09:16:30.843` — **"published successfully"** |
| RabbitMQ | delivered, **acked**, queue depth 0, zero unroutable |
| notification `processed_events` | **no row** |
| customer | 33 SAR gone, **never told** |

---

## 4 · Prediction vs reality — ARM A

| # | Predicted | Actual | |
|---|---|---|---|
| — | no row created; `CustomerNotFoundException` | **✓ exactly** — 0 rows, WARN at the drop branch | ✓ |
| A1 | *unpredicted* — does the bill outbox mark `sent_at`? | **YES.** The bill service holds a durable record asserting the notification was sent. It was not. | — |
| A2 | *unpredicted* — does the S08 `mandatory` callback fire? | **NO — zero UNROUTABLE lines.** Routing was perfect. **This is the blind spot, confirmed.** | — |
| A3 | *unpredicted* — is the discrepancy findable afterwards? | **🟢 YES, and better than expected — see below** | — |

### 🟢 A3 — the reconciliation already has a join key, and it found EVERY loss in the project's history

`processed_events.message_id` **is** the bill's `outbox.event_id` — the relay sets it. So no
new column, no new service, no instrumentation is needed. One set difference:

```
bill events marked SENT              : 20,241
events notification processed        : 20,301  (includes wallet events)
ORPHANS — bill says sent, notification has no row :  10
```

Run for the first time, against three weeks of accumulated data, it **retroactively
identified every known message-loss event in the project**:

| event | cif | correlation id | what it was |
|---|---|---|---|
| b4a6fda6 · d6388f04 · 9f70a3f7 | 5100000001 | `OUTBOX-PAID`, `OUTBOX-REJ2`, `reconciliation-bills-…` | 31 Aug — **before the bill→notification binding existed** |
| 11442028 | 5100000001 | `RELAY-E2E` | 2 Sep — relay built, binding not yet wired |
| 211f3b9c · 4a3b16d6 · 9954449d | 5100000001 | `S08-BILL-1/2/3` | 7 Sep — **the 3 messages S08 destroyed by deleting the binding** |
| 6efd188b · 104e25ea | 5100000001 | `S08B-BILL-1/2` | 8 Sep — **the 2 unroutable messages from the S08b verification** |
| **d0e99ad1** | **7700000001** | `S10-ARM-A` | **8 Sep — this run** |

**It found the S08 losses that S08 itself concluded were unrecoverable**, and it did so
without having been told they happened. S08's write-up recorded *"restoring the binding
recovers nothing"* — true for the messages, but the **record** of the loss was recoverable
all along, in a join nobody had run.

**This query is strictly more powerful than the `mandatory` callback.** `mandatory` catches
broker-side loss *immediately* but is blind to consumer-side loss. The set difference catches
**both**, after the fact. They are complementary, not competing: **detect live, reconcile
after.**

⚠️ **Caveat found by running it:** 4 of the 10 orphans are development artifacts from before
the binding existed. A real reconciliation needs a **start boundary** — the same lesson as
the settlement window in S09, and the same trap: a check with no agreed cutoff reports noise
as findings.

---

## 5 · Did the golden rule hold? — ARM A

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense account balance | 12 | 12 | ✓ |
| wallet `007700000001` | 3000 | 2967 | ✓ (33 correctly paid) |
| drifted wallets | 0 | 0 | ✓ |

**Held — and irrelevant, for the second scenario running.** The money was *correctly* taken;
the bill really was paid. Nothing about the ledger is wrong. What was lost is the customer's
knowledge that it happened — which S08 already established the invariants cannot see, and
which arm A shows survives past the fix S08 earned.

---

## 6 · What I would fix, and whether I fixed it

*(pending — arm B first)*

## 7 · Follow-on scenarios

*(pending)*

## 8 · Log evidence

```
09:16:30.850 INFO [S10-ARM-A] NotificationListener : message id d0e99ad1-ff20-4a7e-a966-8eabfb3c917a
             received with routing key bill.payment.paid and correlation id S10-ARM-A
09:16:30.876 WARN [S10-ARM-A] NotificationListener : no customer for cif 7700000001
             (message d0e99ad1-ff20-4a7e-a966-8eabfb3c917a) — dropping

26 milliseconds between "received" and "dropping".
No ERROR. No requeue. No dead letter. Queue depth 0. Zero UNROUTABLE lines.
The only evidence is one WARN in a log deleted after 7 days.
```
