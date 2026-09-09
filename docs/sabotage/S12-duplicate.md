# S12 — duplicate (the category with zero coverage)

> Prediction must be written and saved **before** the run.

| | |
|---|---|
| **Date** | 2026-09-09 |
| **Services running** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation id prefix** | `S12-` |
| **Related roadmap topic** | #7 — the 12th scenario, closing Phase 7's success criterion |

**Baseline:** wallet `015100000001` = 3635 · `015100000002` = 1200 · ledger rows = 40,592 ·
held = 12 · suspense = 12 · processed_events = 20,306.

---

## 1 · What I am breaking, and how

Eleven scenarios have hammered *delay* (S02, S02b, S05, S06, S09, S11a) and *kill* (S01, S08),
and S07's 10,000-bill backlog was effectively a flood. **Duplicate has never been touched** —
and every money guarantee in this system rests on it.

The enforcement is in the database, with code checks as a fast path in front:

```
ledger_idempotency_key_key    UNIQUE (idempotency_key)
uq_entry_discharged_once      UNIQUE (coalesce(reverses_entry_id, settles_entry_id))
processed_events_pkey         PRIMARY KEY (message_id)
```

```java
@Transactional
public LedgerEntry transfer(...) {
    if (ledgerEntryRepository.existsByIdempotencyKey(idempotencyKey)) {   // CHECK
        throw new DuplicatedEntryException(...);
    }
    ...
    ledgerEntryRepository.save(entry);                                    // ...then ACT
}
```

**`existsByIdempotencyKey` is check-then-act.** Nothing prevents two concurrent callers from
both passing it; the UNIQUE constraint is the only real guard. Four seams:

1. **Sequential duplicate** — same `Idempotency-Key`, second request after the first returns
2. **Concurrent duplicate** — ten simultaneous requests, one key
3. **Double settle** — `settle` twice on the same hold
4. **Duplicate delivery** — the same `message_id` republished to the notification queue

---

## 2 · PREDICTION  ⚠️ written before the run

### Learner's predictions, verbatim

| # | Prediction | Confidence |
|---|---|---|
| Q1/Q2 · duplicates | *"only one will be recorded, HTTP 5xx"* | **medium** |
| mechanism | *"there will lock in the wallet from transfer"* | **medium** |
| Q4 · duplicate delivery | *"one receive message"* | **guessing** |
| Q5 · golden rule | *"it will remain"* | **high** |

**Q3 (double settle) — UNPREDICTED**, recorded as such rather than back-filled.

### Confirmed from the code before running (so the run tests behaviour, not syntax)

`transfer` is `@Transactional`, so a UNIQUE violation should roll back the balance change
with the ledger insert. The wallet rows are locked in a **sorted order** (`compareWallets`),
so concurrent callers serialise rather than deadlock — which is the mechanism the second
prediction names.

⚠️ **The crux of the HTTP-code prediction:** the two rejection paths are different.
Sequential loses at `existsByIdempotencyKey` → `DuplicatedEntryException`. Concurrent loses at
the UNIQUE constraint → `DataIntegrityViolationException`. **Both have `@ExceptionHandler`s.**
The question is whether the same logical condition produces the same status code, and
"HTTP 5xx" is a falsifiable claim about that.

### Separate design question put to the learner, not a prediction

The wallet **rejects** a duplicate rather than replaying the original result. A client that
timed out and retries gets a rejection and still cannot learn whether its money moved. Is
rejecting right, or should a repeated key return the original entry?

---

## 3 · What actually happened

### Seam 1 — sequential duplicate
```
attempt 1 -> 200
attempt 2 -> 409  "duplicated entry | Duplicated Entry with key = S12-SEQ-KEY"
ledger rows with that key: 1     balance 3635 -> 3625 (one transfer of 10)
```

### Seam 2 — ten concurrent, one key
```
 1 x 200
 9 x 409   ALL of them: "conflict | request conflicts with existing data"
ledger rows with that key: 1     balance 3625 -> 3614 (one transfer of 11)
```
**All ten passed `existsByIdempotencyKey`.** Every rejection came from the
`DataIntegrityViolationException` handler, not `DuplicatedEntryException` — the fast-path
check caught **nothing**, and the UNIQUE constraint did 100% of the work.

### Seam 3 — sequential double settle (different keys)
```
settle A -> 200
settle B -> 409  "hold already discharged | hold d140134b... was already discharged"
SETTLEMENT rows: 1     suspense 37 -> 12
```

### Seam 3b — TEN CONCURRENT settles on one hold, all different keys
```
 1 x 200
 9 x 400   "insufficient balance"      <-- NOT "already discharged"
SETTLEMENT rows for that hold: 1
biller account 7400 -> 7430 (+30, exactly once)     suspense back to 12
```

### Seam 4 — duplicate delivery
```
publish -> {'routed': True}
listener: "message id ab7f9751-... received with routing key bill.payment.paid"
rows with that message_id: 1     attempts unchanged at 2     states still SENT/SENT
provider sends: 8 before, 8 after      -> NO second SMS
```

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| Q1/Q2a | *"only one will be recorded"* | **✓ in all four seams** — 1 ledger row, 1 settlement, 1 event row, every time | ✓ |
| Q1/Q2b | *"HTTP 5xx"* | **✗ FALSIFIED** — 409 for transfers, **400** for the concurrent settle. Never 5xx | ✗ |
| mechanism | *"there will lock in the wallet from transfer"* | **✓** — `compareWallets` orders the locks, so the ten serialised instead of deadlocking. That serialisation is *why* seam 3b produced a deterministic outcome | ✓ |
| Q3 | *unpredicted* | see the finding below | — |
| Q4 | *"one receive message"* | **✓ exactly** — provider sends 8 → 8 | ✓ |
| Q5 | *"it will remain"* | **✓** — held 12, suspense 12, zero drift, ledger +6 rows exactly | ✓ |

**4 of 5.**

### 🔴 THE FINDING — a duplicate settle is reported as "insufficient balance"

Ten concurrent settles on one hold produced nine `400 insufficient balance`. **Neither guard
built for this fired.** Not the code-level discharge check — all ten passed it concurrently.
Not `uq_entry_discharged_once` — it never got the chance.

What actually stopped them was **the suspense account running out of money**. `settle` calls
`transfer(SUSPENSE, BILLER, 30, ...)`; the first drains suspense from 42 to 12, and the other
nine hit `InsufficientBalanceException`.

**Money was never at risk** — one settlement, biller +30 exactly, suspense restored. But the
diagnosis is actively misleading: an on-call engineer reading *"insufficient balance"* would
investigate suspense-account funding, when the real event is a **replayed request**.

Worse, **it is timing- and state-dependent.** Had other concurrent holds left more money in
suspense, the balance check would have passed and `uq_entry_discharged_once` would have
rejected with a 409. **The same logical fault reports as 400 or 409 depending on unrelated
account state.** The invariant that protects the money is not the one that explains it.

### 🟢 The constraints earned their keep — the code checks did not

This is the project's thesis measured directly: *correctness lives in DB constraints because
code can be wrong.*

| guard | caught in the race? |
|---|---|
| `existsByIdempotencyKey` (code) | ❌ **0 of 10** — all passed the check-then-act window |
| discharge check in `settle` (code) | ❌ **0 of 10** |
| `ledger_idempotency_key_key` (DB) | ✅ 9 of 9 |
| suspense balance CHECK (DB) | ✅ 9 of 9 — but with the wrong error |
| `processed_events_pkey` (DB) | ✅ dedupe held |

Every code-level check is a **latency optimisation**, not a correctness guarantee. They are
worth keeping — they turn the common sequential case into a clean 409 instead of a constraint
violation — but the run shows none of them is load-bearing.

---

## 5 · Did the golden rule hold?

| check | before | after | |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense balance | 12 | 12 | ✓ |
| wallet `015100000001` | 3635 | **3559** = 3635−10−11−25−30 | ✓ |
| wallet `015100000002` | 1200 | **1221** = 1200+10+11 | ✓ |
| ledger rows | 40,592 | **40,598** = +6 exactly (2 transfers, 2 holds, 2 settlements) | ✓ |
| drifted wallets | 0 | 0 | ✓ |

**Held perfectly, under 20 concurrent duplicate attacks.** Not one duplicate row, not one
double charge. To the digit.

---

## 6 · What I would fix, and whether I fixed it

**NOT FIXED — 1. 🔴 The concurrent-settle error is a lie.** `400 insufficient balance` sends
the reader to the wrong system. The suspense balance is not the guard for this condition; it
just happens to trip first. The shape of the fix: check the discharge state **inside the same
transaction that writes the settlement**, or catch the constraint violation and translate it,
so a replay always reports as a replay regardless of account balances.

**NOT FIXED — 2. The two duplicate paths give different bodies.** Sequential →
*"duplicated entry"*; concurrent → *"conflict / request conflicts with existing data"*. Same
status, same logical condition, different message. A client cannot tell them apart, and it is
pure luck that both are 409.

**OPEN DESIGN QUESTION — reject vs replay.** The wallet *rejects* a duplicate. A client that
timed out and retried gets a 409 and **still does not know whether its money moved** — it must
inquire separately. True idempotency returns the original result, making the retry safe and
self-describing. This is the same shape as the biller's `settledByReference` replay, which the
bill service already relies on. **Learner's call; recorded as open.**

---

## 7 · Follow-on scenarios

- **S11b** — explicit `EXPIRED`, and the contract-rollout-order hazard.
- **S11c** — the knife edge between the two settlement windows.
- **Kill mid-saga** — especially the relay crashing between publish and `sent_at`, which
  produces exactly the duplicate delivery seam 4 just proved is safe.

---

## 8 · Log evidence

```
SEAM 2 — ten concurrent, one key:
  1 x 200,  9 x 409 "conflict | request conflicts with existing data"
  -> all 9 lost at the UNIQUE constraint, none at existsByIdempotencyKey

SEAM 3b — ten concurrent settles, one hold:
  1 x 200,  9 x 400 "insufficient balance"
  -> the suspense account emptying is what stopped them, not any discharge guard
  -> biller 7400 -> 7430 (+30 exactly), SETTLEMENT rows for that hold: 1

SEAM 4 — replay of message_id ab7f9751-eef9-41cf-bfa4-f74ed45358ba:
  08:24:07.144 INFO NotificationListener : message id ab7f9751-... received
  rows: 1   attempts: 2 (unchanged)   provider sends: 8 -> 8
```
