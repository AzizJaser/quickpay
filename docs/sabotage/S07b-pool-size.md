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
| wallet 005100000001 | 16,870 | | |
| drifted wallets | 0 | | |

---

## 6 · What I would fix, and whether I fixed it

*(pending)*

---

## 7 · Follow-on scenarios

*(pending)*

---

## 8 · Log evidence

*(pending)*
