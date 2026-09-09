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

### Arm B — learner's prediction, verbatim (locked before the run)

| Q | Prediction | Confidence |
|---|---|---|
| Q4 | *"it will be saved, it will resend, if the provider was up"* — i.e. a `processed_events` row IS created, the resend job picks it up, and the customer gets the message once the provider returns | **high** |
| Q5 | *"attempt will not pass 5"* | **high** |
| Q6 | *"golden rule will stay"* | **medium** |

**Confirmed from the code before running** (so the run tests behaviour, not syntax):
`NotificationProviderClient` has **no try/catch and no `onStatus` handler for 5xx or
connection failures** — only a 422 handler. A stopped provider therefore raises
`ResourceAccessException` out of `smsProvider(...)`, straight through `deliver()`.

⚠️ **The crux of Q5, stated before the run:** `event.setAttempts(event.getAttempts() + 1)`
is the *third-from-last* line of `deliver()`, **after** both provider calls. If the provider
call throws, that line is never reached. Q5 asks whether "will not pass 5" is true because
the cap engages — or true for a reason that makes the cap meaningless.

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

## 3b · What actually happened — ARM B

Run in two variants, because the provider can fail in two ways that look identical to a
customer and behave oppositely in code. Mode forced each time so the configured
`failureRate: 0.3` could not contaminate the result.

**B1 — provider returns 503** (`SERVER_ERROR`; the client has no 5xx handler, so it *throws*):

```
   T+10s   attempts=0   PENDING/PENDING
   T+20s   attempts=0   PENDING/PENDING
   ...
   T+60s   attempts=0   PENDING/PENDING     <- twelve resend passes, counter never moved
```

**B2 — provider returns 422** (`FAILED`; handled by `onStatus`, so it *returns normally*),
run while B1's row was still PENDING and visible to the same job:

```
   time      B2 (422)                 B1 (had been frozen at 0)
   T+10s   att=3 PENDING/PENDING     att=2 PENDING/PENDING
   T+20s   att=5 FAILED/FAILED       att=4 PENDING/PENDING
   T+30s   att=5 FAILED/FAILED       att=5 FAILED/FAILED
```

**The frozen row unfroze the instant the provider started *answering* instead of throwing.**
Same row, same job, same customer — only the failure's shape changed.

**Recovery test — provider restored to healthy, eight clean resend passes:**

```
 S10-ARM-B1  attempts=5  FAILED  FAILED  sms_sent_at = null
 S10-ARM-B2  attempts=5  FAILED  FAILED  sms_sent_at = null
```

**Neither recovered. Both customers paid and were never told.**

---

## 4b · Prediction vs reality — ARM B

| Q | Predicted | Actual | |
|---|---|---|---|
| Q4a | *"it will be saved"* | **✓** — the row is written before `deliver()` is called | ✓ |
| Q4b | *"it will resend"* | **✓** — 12 passes in B1, and the job never stops trying | ✓ |
| Q4c | *"if the provider was up"* → the customer gets it | **✗ FALSIFIED** — provider healthy, 8 clean passes, **still `FAILED`, still nothing sent** | ✗ |
| Q5 | *"attempt will not pass 5"* | **✓ literally, in both arms — but for opposite reasons.** B2: true because the cap works. B1: true because **the counter never moved at all** | ~ |
| Q6 | golden rule holds | **✓** — 12/12, zero drift | ✓ |

### 🔴 FINDING 1 — the retry cap only exists when the provider answers

`event.setAttempts(event.getAttempts() + 1)` is the third-from-last line of `deliver()`,
**after** both provider calls. When the provider *throws*, that line is never reached:

| provider does | client behaviour | `attempts` | outcome |
|---|---|---|---|
| 503 / connection refused | **throws** past `setAttempts` | **frozen at 0** | retries **forever**, unbounded |
| 422 `FAILED` | returns normally | 0→5 | terminal `FAILED` in ~25 s |

**The bound is absent from exactly the failure it is most needed for.** A dead or erroring
provider produces an unbounded retry loop; a politely-refusing one is capped. "Will not pass
5" was satisfied by "will not pass 0" — the prediction was right and the mechanism it assumed
was not there.

### 🔴 FINDING 2 — twenty-five seconds destroys a notification permanently

`maximum-retries: 5` × `resend-interval-ms: 5000` = **25 seconds.**

A provider outage lasting longer than 25 seconds drives every in-flight notification to a
terminal `FAILED` state. The `ResendingJob` only queries `PENDING`, so **a `FAILED` row is
never looked at again.** Recovery is impossible without manual intervention, the money has
already moved, and the customer is never told.

**This is the first Phase 7 finding where the *fix* is the thing that causes the loss.** The
retry cap exists to stop infinite loops; set against a 5-second interval it converts a brief,
survivable outage into permanent data loss. **A retry budget must be a duration, not a count**
— 5 attempts means something completely different at 5 s and at 5 min.

### 🔴 FINDING 3 — arm A's reconciliation is blind to arm B

Re-running the set difference after arm B:

```
orphans (bill sent, notification has NO row) : 10   <- unchanged
rows in terminal FAILED state                : 11   <- INVISIBLE to that query
```

Arm A's query finds *missing rows*. Arm B produces *present rows in a dead state*. **They are
two different loss classes and neither query sees the other.** A reconciliation built only
from arm A's evidence would have reported "all clear" while 11 customers went untold.

The 11 are not all from this run — 9 predate it (7 from 9 Aug, 2 from 10 Aug). **This has
been happening silently since the notification service was built.**

---

## 5b · Did the golden rule hold? — ARM B

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense balance | 12 | 12 | ✓ |
| drifted wallets | 0 | 0 | ✓ |

Held. **For the third scenario running, the golden rule is the wrong instrument** — the money
moved correctly every time. What is lost is the customer's knowledge, and no money invariant
can see it.

---

## 6 · What I would fix, and whether I fixed it

**NOT FIXED — Phase 7 is a measuring exercise, and these need a decision first.** Ranked:

**1. 🔴 The retry budget must be a duration, not a count.** 25 seconds is not an outage
tolerance, it is a rounding error. `maximum-retries: 5` against a 5-second interval is the
whole bug. Options: raise the count, back off exponentially, or express the budget as
"keep trying for N hours." **Decide the policy before touching the code.**

**2. 🔴 `attempts` must increment even when the provider throws.** Today the counter sits
after the calls that can throw. Moving the increment (or using try/finally) is small — but it
makes B1 terminate at 5 too, which **without fixing #1 first converts an unbounded retry into
guaranteed permanent loss in 25 seconds.** ⚠️ **Do not fix #2 before #1.** That ordering is
the finding, not a footnote.

**3. 🟢 The reconciliation is earned — and it needs BOTH queries.** Arm A proved the join key
already exists (`processed_events.message_id` = `outbox.event_id`), no new schema. Arm B
proved one query is not enough:
- *missing row* → set difference (10 found, retroactively, including every S08 loss)
- *terminal FAILED row* → state query (11 found, 9 of them undetected since August)

**With a start boundary** — 4 of the 10 orphans predate the binding, and a check with no
agreed cutoff reports noise as findings. Same lesson as S09's settlement window, in a
different place.

**4. A `FAILED` row is currently a dead end.** Nothing reads it, nothing alerts on it, nothing
retries it. At minimum it should be surfaced.

---

## 7 · Follow-on scenarios

- **S11 — biller enforces the settlement window** (carried over from S09): prove the fix is a
  *contract*, not more code on our side.
- **S12 — provider outage of 30 s vs 5 min**, after the retry policy is decided: verify the
  new budget actually survives a realistic outage.
- **Duplicate · kill mid-saga · flood** — still untouched.

---

## 8 · Log evidence

```
ARM A — 26 ms from received to dropped:
09:16:30.850 INFO [S10-ARM-A] : message id d0e99ad1-... received, routing key bill.payment.paid
09:16:30.876 WARN [S10-ARM-A] : no customer for cif 7700000001 (message d0e99ad1-...) — dropping
  no ERROR. no requeue. no dead letter. queue depth 0. zero UNROUTABLE lines.

ARM B1 — twelve passes, counter frozen:
09:53:26.919 ERROR [resending-fbc6001d] : Unexpected error happened during the resending job,
             503 Service Unavailable: ".../provider/v1/sms"
09:53:31.947 ERROR [resending-3873e7ad] : (same)
09:53:36.975 ERROR [resending-fb7e31a9] : (same)
  attempts stayed 0 throughout — setAttempts() sits after the call that throws.

FINAL: S10-ARM-B1 attempts=5 FAILED/FAILED sms_sent_at=null
       S10-ARM-B2 attempts=5 FAILED/FAILED sms_sent_at=null
       provider healthy, 8 clean passes, neither recovered.
```

### Run hygiene note
Two provider processes were running (a stale `java -jar` at PID 8605 and the live IDE process
at PID 23340). The first arm-B attempt killed the stale one, which was not bound to 9093, so
it ran against a **healthy** provider and was discarded. It did surface that the provider's
`failureRate` is **0.3**, which explains an `attempts=2` on a successfully-delivered message —
one drawn failure, then a success. Both real arms were run with the mode **forced**, so that
rate could not contaminate them.
