# S07b — scheduler pool size 2: does the fix work?

| | |
|---|---|
| **Date** | 2026-09-07 |
| **Correlation prefix** | `S07B-` |
| **Roadmap topic** | #7 — **the before/after that proves (or disproves) Phase 7's first earned fix** |

**Why this run exists.** S07 measured a **39.3 s total halt** of the outbox relay while
7,186 customer notifications waited, caused by `EODReconciliationJob` and
`NotificationPublisherJob` sharing Spring Boot's default **single-threaded** scheduler. The
fix — `spring.task.scheduling.pool.size: 2` — is now applied. **Without a before/after the
fix is a belief, not a result.** S03 overturning S02b is the precedent.

**Baseline:** 0 `Reserved` · 0 unsent events · held = 12 · wallet `005100000001` = 16,870 ·
**10,260 bills and 10,233 outbox rows already in the tables** from S07.

**S07 comparison targets:**

```
relay gap during a large pass   39.3 s
clean per-bill cost              5.2 ms   (pass 2, no competing load)
pass 2                           7,405 bills in 38.19 s
```

⚠️ **Methodology, decided in advance:** S07's clean measurement (pass 2) ran *after*
submission finished. S07b must reproduce that — strand the bills, let submission settle,
then measure an uncontended pass. Comparing a contended pass against a clean one is exactly
what contaminated S07's pass 1.

---

## 1 · What I am breaking

Nothing new. Same 10,000-bill backlog as S07, with the only change being the scheduler pool
size. **This is a controlled re-run, not a new failure.**

---

## 2 · PREDICTION — written before the run

| # | Question | Prediction | Confidence |
|---|---|---|---|
| 1 | Does the 39.3 s relay gap disappear? | **No — but at least 55% smaller** | medium |
| 2 | Does the sweep get faster or slower? | **Same** | medium |
| 3 | What happens now the two jobs can overlap on `outbox_bill_notification`? | **The row will be locked until the thread writes `sent_at`** | guessing |
| 4 | Does the golden rule hold under real concurrency? | **Yes** | high |

⚠️ **Prediction 1 is the interesting one** — it predicts the fix is *partial*. If the relay
genuinely gets its own thread, the naive expectation is the gap disappears entirely. A
55% reduction implies something else is still blocking it, which would be a finding in its
own right.

⚠️ **Prediction 3 concerns a genuinely new condition.** Until now the single thread was an
**accidental mutex** — those two jobs could never run at once. This is the first time they
can interleave, and "it was safe because it never happened concurrently" is the kind of
assumption that survives untested until it does not.

---

## 3 · What actually happened

10,000 bills stranded in one continuous burst (08:47:46 → 08:48:41). By luck the earliest
windows closed at 08:48:46 — **five seconds after submission ended** — so every sweep pass
was uncontended, exactly the like-for-like comparison the methodology required.

```
                        S07 (pool 1)        S07b (pool 2)
relay largest gap       39.3 s              2.0 s          <- the 2 s interval itself
relay gaps over 3 s     1                   0
sweep cost/bill         5.2 ms              5.3 ms         <- unchanged
```

Both threads carried relay work (`scheduling-1` AND `scheduling-2`), confirming the pool is
live and tasks are not pinned to a thread.

```
pass 1:    875 bills in  5.37 s  (6.1 ms/bill)
pass 2:  8,965 bills in 47.38 s  (5.3 ms/bill)
pass 3:    160 bills in  1.16 s  (7.3 ms/bill)
```

### 🔴 THE FIX WORKED — AND REVEALED THE NEXT CONSTRAINT

The relay never missed its 2 s interval. But the **unsent backlog still grew**, peaking at
~6,600, then drained at ~700 per 15 s:

```
T+15 s  unsent 2,016        T+60 s  unsent 6,600  <- peak
T+30 s  unsent 4,157        T+75 s  unsent 5,900
T+45 s  unsent 6,421        ...      drains at ~47/s
```

Not blocking — a **throughput ceiling**:

```
findTop100BySentAtIsNull...    100 events per batch
notification-interval-ms 2000  every 2 seconds
                             = 50 events/second, hard cap
```

The sweep writes ~200 events/s; the relay publishes 50/s. **That ceiling existed in S07 too
— it was invisible because the relay was blocked entirely, so a throughput limit never got
the chance to matter.**

**Fixing the blocking revealed the constraint underneath it.** Which is the normal shape of
performance work, and precisely why S07b had to be run rather than assumed.

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | gap will not disappear — **~55% smaller** | **✗ it disappeared entirely** — 39.3 s → 2.0 s max, zero gaps over 3 s. Wrong, in your favour | ✗ |
| 2 | sweep speed unchanged | **✓ exactly** — 5.3 ms/bill vs 5.2 ms | ✓ |
| 3 | the row will be locked until `sent_at` is written | **✗ no contention occurred** — see below | ✗ |
| 4 | the golden rule holds | **✓** — identity 12 = suspense 12, zero drift, **20,297 distinct `message_id`s, no duplicates** | ✓ |

### Why prediction 3 did not happen — and why the design is safe by construction

The concern was right to raise: until now the single thread was an **accidental mutex**, and
this was the first time the two jobs could interleave.

But they never contend, because they touch **disjoint rows at disjoint times**:

- the sweep only **INSERTs** new outbox rows (`sent_at` null), one transaction per bill
- the relay only **SELECTs rows the sweep has already committed**, then UPDATEs `sent_at`

A row the relay is updating is one the sweep finished with; a row the sweep is writing is not
yet visible to the relay. Postgres MVCC does the rest. **20,233 events, 20,233 distinct, zero
duplicates, zero unsent** at the end.

That safety is a property of the outbox pattern, not of the single thread — which is why
removing the accidental mutex cost nothing.

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense account balance | 12 | 12 | ✓ |
| wallet 005100000001 | 16,870 | 16,870 | ✓ (10,000 held, all returned) |
| drifted wallets | 0 | 0 | ✓ |
| duplicate `message_id`s in `processed_events` | — | **0 of 20,297** | ✓ |

Held under genuine job concurrency for the first time.

---

## 6 · What I would fix, and whether I fixed it

### ✅ THE FIX IS PROVEN — `spring.task.scheduling.pool.size: 2` stays

**Kept**, with a measured before/after: a 39.3 s relay halt became zero gaps over 3 s, at no
cost to sweep throughput and with no concurrency defects. **This is Phase 7's first fix
built AND verified.**

Worth noting what made it safe: the outbox pattern already guaranteed disjoint access. The
single thread was hiding that guarantee rather than providing it.

### ⏳ NEWLY REVEALED, NOT FIXED — the relay's 50 events/second ceiling

`Top100` every 2 s caps the relay at 50/s while the sweep produces ~200/s. A 10,000-event
backlog therefore takes ~200 s to drain, during which customers wait.

Deliberately **not** fixed, because the numbers do not yet justify it: 10,000 simultaneous
bill failures is not a realistic steady state, and the backlog *did* drain completely. The
levers, when something earns them: raise the batch size, shorten the interval, or publish in
parallel. **Each trades broker pressure for latency, so it needs a target, and there is no
target yet.**

⚠️ **The general finding worth keeping: fixing a blocking problem exposes the throughput
problem behind it.** The 50/s ceiling was always there and was completely invisible while
the relay was blocked.

---

## 7 · Follow-on scenarios

- **S08 — break a binding.** The measured `publish_in` vs `publish_out` gap: an outbox
  reporting success while the broker silently drops the message.
- **S09 — late settlement.** Violate the settlement window deliberately.
- **S10 — relay throughput.** Only if a realistic load makes the 50/s ceiling matter; more
  naturally a Phase 8 question than a Phase 7 one.

---

## 8 · Log evidence

```
both scheduler threads carrying relay work:
  [scheduling-1] c.q.b.config.NotificationPublisherJob
  [scheduling-2] c.q.b.config.NotificationPublisherJob

relay: 7,477 log lines, largest gap 2.0 s, zero gaps over 3 s
       (S07 for comparison: 6,122 lines, one 39.3 s gap)

outbox: 20,233 events, 20,233 distinct, 0 unsent
processed_events: 20,297 rows, 20,297 distinct message_id — no duplicates
drifted wallets: 0
```
