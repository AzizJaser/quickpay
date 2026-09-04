# S02 — hung biller (accepts the connection, never answers)

> Prediction must be written and saved **before** the run. S01 showed why: it falsified its
> own premise, and that was only visible because the premise was written down first.

| | |
|---|---|
| **Date** | 2026-09-04 |
| **Services running** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation id prefix** | `S02-` |
| **Related roadmap topic** | #5 sagas, timeouts, circuit breakers — **this is the scenario that must earn Resilience4j, since S01 did not** |

**Baseline before the run:** 0 `Reserved` bills · held in suspense = 12 (known orphan) ·
wallet `005100000001` balance = 2050.

**Parameters (confirmed from code):**

```
biller client read timeout      2 s
biller client connect timeout   2 s
sweep interval                  10 s   fixedDelay — next pass starts 10 s after the
                                       previous one FINISHES, not after it started
settlement window               60 s
TIMEOUT mode                    accepts the connection, sleeps past the caller's
                                timeout, THEN settles PAID
```

**Contrast with S01:** there the biller process was *stopped*, so connections were refused
in ~1 ms and nothing was ever recorded on the biller side. Here the biller is **alive and
accepting** — so every call costs the full read timeout, and the biller **does** end up
with a record.

---

## 1 · What I am breaking, and how

Put the biller simulator into `TIMEOUT` mode with a delay well above the 2 s read timeout,
then submit several bills. The biller accepts each payment, sleeps past the caller's
timeout, and settles **PAID** — while the bill service sees only a timeout and never learns
the outcome. Left running long enough to cross the 60 s settlement window, with the sweep
retrying throughout.

---

## 2 · PREDICTION  ⚠️ written before the run

### Q1 · Cost per sweep pass

With **5 stranded bills**, how long does one sweep pass take?
At what number of bills does the sweep stop keeping up with its 10 s interval?

> *(answer)*

### Q2 · Does the settlement window fire this time?

S01 established that the window cannot fire without an *answer* from the biller —
`resolve` is only called with a `BillerResult`, and a thrown `inquire` never reaches it.
**Is a read timeout an answer?** Predict whether these bills expire at 60 s, or behave
like S01 and wait indefinitely.

> *(answer)*

### Q3 · What does each side believe? ⭐

`TIMEOUT` settles **PAID** on the biller after sleeping. So the biller's records say the
payment succeeded, while the bill service never heard.

- What does the **biller** think happened?
- What does the **bill service** think happened?
- What happens when the sweep later inquires and the biller answers `PAID`?
- Is there a window in which the two disagree, and what resolves it?

> *(answer)*

### Q4 · Where does the customer's money end up?

Held, refunded, or paid to the biller? And is that the *correct* outcome given what the
biller believes?

> *(answer)*

### Q5 · Does the golden rule hold?

Can this scenario create or destroy money? If so, by what sequence?

> *(answer)*

### Q6 · Does this earn the circuit breaker?

S01 did not — a refused connection costs ~1 ms. Predict whether the measured cost here is
large enough to justify Resilience4j, and **what specifically the breaker would protect**.

> *(answer)*

**Confidence per question:** *(high / medium / guessing — the guesses are the valuable ones)*

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
| held in suspense (type identity) | 12 | | |
| suspense account balance | 12 | | |
| wallet 005100000001 | 2050 | | |
| drifted wallets | 0 | | |

---

## 6 · What I would fix, and whether I fixed it

*(pending)*

---

## 7 · Follow-on scenarios this suggests

*(pending)*

---

## 8 · Log evidence (preserved)

*(pending)*
