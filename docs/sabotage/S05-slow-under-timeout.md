# S05 — biller slow but UNDER the timeout (nothing fails)

| | |
|---|---|
| **Date** | 2026-09-06 |
| **Correlation prefix** | `S05-` |
| **Roadmap topic** | #5 — the last open question for the circuit breaker |

**Why this run exists.** S01–S04 all had something visibly wrong: a refused connection, a
read timeout, a 503. Here **every call succeeds**. The biller answers in 1.5 s against a 2 s
read timeout, so nothing errors, nothing is logged, and every bill resolves. The question is
whether "working, but slow" is a problem at all — and how anyone would know.

**Parameters:** biller `delayMs = 1500` · read timeout 2000 ms · sweep interval 10 s ·
relay interval 2 s · **scheduler pool size = 1 (Spring Boot default, not overridden)**.

---

## 1 · What I am breaking

Nothing, technically. The biller is healthy and answers every call — just slowly, at 1.5 s,
which is under the 2 s timeout. Bills are submitted and left to resolve normally.

---

## 2 · PREDICTION — written before the run

| # | Question | Prediction | Confidence |
|---|---|---|---|
| 1 | Sweep pass duration with 5 stranded bills | **1.5 s for the bulk call covering all 5** — the S03 batching means one call, not five | medium |
| 2 | Would a circuit breaker trip? | **No — nothing is failing, it is just slower** | medium |
| 3 | Is there any signal that the system is degraded? | **Logs** | guessing |
| 4 | Where does this hurt? | **"It will hurt the whole service."** Sharpened during discussion: **the sweep and the relay share `scheduling-1`, so a slow sweep delays the relay** — outbound customer notifications are held up by a slow biller they have nothing to do with | guessing |

⚠️ **Premise confirmed before the run:** both `EODReconciliationJob` and
`NotificationPublisherJob` log on `[scheduling-1]`, and `spring.task.scheduling.pool.size`
is not configured — **Spring Boot's default TaskScheduler has ONE thread**. So every
`@Scheduled` method in the bill service is serialised. *(Found by the learner, not the
reviewer.)*

⚠️ **Related gap found while predicting:** `billRepository.findByStatus(Reserved)` has **no
limit**, so the bulk request carries *every* stranded bill. Batching made the call **count**
independent of the backlog but left the request **size** proportional to it. Contrast the
notification retry job, which is bounded (`findTop100By...`).

---

## 3 · What actually happened

Biller set to answer in **1.5 s** (under the 2 s read timeout). Five bills submitted 09:15:55.

```
09:15:55   5 bills submitted -> Reserved
09:17:15   all 5 Rejected — created 09:15:55, reverted ~80 s later
           (past the 60 s window, and the biller ANSWERED with NOT_FOUND,
            so resolve ran and the window fired)

measured directly: 5 references in one bulk call = 1.56 s
relay published all 5 events in 14 ms (09:16:59.435 -> .449)
```

**No error was logged at any point.** The sweep only logs on failure, and nothing failed.

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | 1.5 s for the bulk call covering all 5 | **✓ measured 1.56 s** | ✓ |
| 2 | nothing fails, just slower | **✓** — every call succeeded, every bill resolved, zero errors | ✓ |
| 3 | logs are the signal | **✗ — there are none.** The sweep logs only on failure. A biller at 1.5 s and one at 5 ms produce **byte-identical logs** | ✗ |
| 4 | it hurts the whole service — the sweep and relay share `scheduling-1` | **✓ mechanism confirmed**, magnitude small — see below | ✓ |

### Prediction 3 was wrong, and that is the scenario's point

**Slow-but-working is invisible.** Every other scenario announced itself with an ERROR line.
This one produces **no signal at all**: no error, no warning, no counter. The only way to
know the biller degraded from 5 ms to 1.5 s — a 300× slowdown — is to measure latency, and
**nothing in the system measures latency**.

That is the honest answer to "where does this hurt": *you would not know it was happening.*

### Prediction 4 — mechanism confirmed, and an accidental fix discovered

Confirmed: both `EODReconciliationJob` and `NotificationPublisherJob` log on
**`[scheduling-1]`**, and `spring.task.scheduling.pool.size` is unset, so Spring Boot's
default **single-threaded** `TaskScheduler` serialises every `@Scheduled` method in the
service. A slow sweep therefore blocks the outbox relay — outbound customer notifications
delayed by a biller they have nothing to do with.

**But the magnitude is now small, and S03 is why:**

| | thread held per pass | relay impact |
|---|---|---|
| before S03 (per-item) | 5 × 1.5 s = **7.5 s** of a ~17 s cycle | relay starved for nearly half the cycle |
| after S03 (bulk) | **1.5 s** of a 12 s cycle | relay misses at most one 2 s firing |

**S03's batching incidentally fixed a starvation problem neither of us had noticed.** It was
built to cut the sweep's own cost; it also bounded the sweep's blast radius on every other
scheduled job in the service. Second time batching solved more than it was aimed at.

⚠️ **Not measured:** precise relay starvation. The sweep logs nothing on success, so there
is no start/finish timestamp to measure against, and proving delay needs sustained load —
**Phase 8 work, not Phase 7.** The mechanism is certain; the magnitude above is arithmetic,
not measurement.

---

## 5 · Did the golden rule hold?

| check | after | agree? |
|---|---|---|
| held in suspense (identity) | 12 | ✓ |
| suspense account balance | 12 | ✓ |

Back to the known orphan; the five bills' holds were all released.

---

## 6 · What I would fix, and whether I fixed it

**NOTHING FIXED — but this scenario changes what topic #5 needs.**

**A circuit breaker would not have tripped, and should not have.** Nothing failed. Every
call returned inside the timeout. A breaker keyed on failures is blind to a 300× slowdown
that stays under the threshold — which is precisely the case here.

**What this actually argues for is instrumentation, not protection:**

- **Latency metrics on the biller client** — p50/p95/p99 per endpoint. Without them, "the
  biller got 300× slower" is unobservable. This is Micrometer, roadmap topic #11, Stage 2.
- **A slow-call threshold** — Resilience4j's breaker can also open on *calls slower than X*
  rather than only on failures. That is the variant this scenario would earn, and it is a
  different configuration from the one S02b argued for.
- **Sweep duration as a metric** — the missing number that made this scenario hard to
  measure at all.

**Topic #5 remains open after five scenarios**, and the reason is now clearer: the failures
that a classic failure-count breaker would catch are either cheap (S01), removed (S03), or
invisible to it (S05).

### Related gap found while predicting, NOT fixed

`billRepository.findByStatus(Reserved)` has **no limit**, so the bulk request carries *every*
stranded bill. S03's batching made the call **count** independent of the backlog but left
the request **size** proportional to it. Contrast the notification retry job, which was
deliberately bounded with `findTop100By...`. **S06 should test this with a large backlog.**

---

## 7 · Follow-on scenarios

- **S06 — large backlog.** 200+ stranded bills in one request body. Where does the batch
  itself become the problem, and does the cap need to exist?
- **S07 — break a binding.** The measured `publish_in` vs `publish_out` gap: an outbox
  reporting success while the broker drops the message.
- **S08 — late settlement.** Violate the settlement window deliberately.

---

## 8 · Log evidence

```
Total error lines produced by this scenario: ZERO.

relay, publishing five rejection events:
09:16:59.435 [bill-relay-...] NotificationPublisherJob
09:16:59.449 [bill-relay-...] NotificationPublisherJob      <- 14 ms for all five

both jobs share one thread:
[scheduling-1] c.q.b.config.NotificationPublisherJob
[scheduling-1] c.q.bill.config.EODReconciliationJob

direct measurement: POST /biller/v1/payments/inquiries with 5 refs = 1.56 s
```
