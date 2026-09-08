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
