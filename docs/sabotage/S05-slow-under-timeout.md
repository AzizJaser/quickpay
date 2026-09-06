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

## 7 · Follow-on scenarios

*(pending)*

---

## 8 · Log evidence

*(pending)*
