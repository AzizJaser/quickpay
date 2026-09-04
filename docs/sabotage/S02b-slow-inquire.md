# S02b — slow biller on BOTH paths (the sweep can finally be stressed)

| | |
|---|---|
| **Date** | 2026-09-04 |
| **Services** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation prefix** | `S02B-` |
| **Roadmap topic** | #5 — **third attempt to earn or refute the circuit breaker** |

**Why this run exists.** S01 failed to earn the breaker (a dead process refuses connections
in ~1 ms). S02 failed too — `inquire()` was a bare map lookup, so the sweep was immune to
biller latency however hung the payment path was. A scaffolding fix now applies the same
`delayMs` to `inquire`, which is how an overloaded service actually behaves. **Verified
live: an inquire took 3.06 s with `delayMs=3000`, previously instant.**

**Parameters**

```
inquire + pay delay   3000 ms   ← now above the read timeout, so EVERY call times out
read timeout          2000 ms
sweep interval        10 s      fixedDelay, measured from the END of the previous pass
settlement window     60 s
```

**Baseline:** 0 `Reserved` · held = 12 (known orphan) · wallet `005100000001` = 1975.

---

## 1 · What I am breaking

Set the biller to `delayMs = 3000` — above the 2 s read timeout — so **every** call to it
times out: the initial `pay`, and every subsequent `inquire` from the sweep. Submit 5 bills
and leave it running past the 60 s settlement window.

Unlike S01 the biller is alive and accepting connections; unlike S02 the *sweep's* path is
now slow too.

---

## 2 · PREDICTION — written before the run

### Q1 · How long is one sweep pass with 5 stranded bills?

> **10 seconds per pass; unacceptable at around 30 bills.** — *guessing* (carried over
> from S02, where it could not be tested)

### Q2 · Does anything resolve, or is the system stuck until the biller recovers?

> **It will resolve, once the settlement window has passed.** — *medium*

### Q3 · Does the settlement window fire?

> **Yes.** — *medium*

### Q4 · What would a circuit breaker actually save, and is it worth it?

> **"It will save the golden rule."** — *guessing*

⚠️ **Note before running:** Q2 and Q3 are the same claim stated twice — that the window
expires these bills. S01 established that the window lives inside `resolve`, and `resolve`
is only reached when `inquire` *returns*. A timed-out `inquire` throws. **This run tests
whether a timeout counts as an answer.**

**Confidence:** Q1 guessing · Q2 medium · Q3 medium · Q4 guessing.

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
| wallet 005100000001 | 1975 | | |
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
