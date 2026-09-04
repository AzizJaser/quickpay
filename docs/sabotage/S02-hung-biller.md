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

> **10 seconds per pass. Stops being acceptable around 30 bills.** — *guessing*

### Q2 · Does the settlement window fire this time?

> **"It will fire and trigger if it receives a timeout."** — *medium*
>
> i.e. a read timeout **does** count as an answer, so the bills expire at 60 s.

### Q3 · What does each side believe? ⭐

> **"It will send a request to the wallet to move funds to the biller account."** — *high*
>
> i.e. once the sweep inquires and the biller answers `PAID`, the bill service captures.

### Q4 · Where does the customer's money end up?

> **Paid — and yes, that is the correct outcome.** — *high*

### Q5 · Does the golden rule hold?

> **Yes.** — *medium*

### Q6 · Does this earn the circuit breaker?

> **"I don't know."** — recorded honestly; the run decides.

⚠️ **Tension noted before running:** Q2 predicts the settlement window **reverts** the bills,
while Q3/Q4 predict the sweep **captures** them. Both cannot happen to the same bill. Either
one prediction is wrong, or they apply to different bills depending on timing.

**Confidence:** Q1 guessing · Q2 medium · Q3 high · Q4 high · Q5 medium · Q6 none.

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
