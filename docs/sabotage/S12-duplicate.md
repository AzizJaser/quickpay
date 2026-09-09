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