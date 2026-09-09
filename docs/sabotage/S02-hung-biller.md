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

```
21:55:15   5 bills submitted, biller in TIMEOUT mode (delayMs 6000, timeout-sleep 3000)
21:55:15   5 x HOLD written          pay() call times out after exactly 2 s
21:55:17   5 x RestClientException escapes payBiller into the async handler
21:55:28   5 x SETTLEMENT written    sweep inquires -> biller answers PAID -> capture
           all 5 bills -> Paid       held back to 12 (the known orphan)
```

Biller's own state afterwards: **5 settled references, all `PAID`** — it agrees with the
bill service.

---

## 4 · Prediction vs reality

| Q | Predicted | Actual | |
|---|---|---|---|
| Q1 | 10 s per pass; unacceptable ~30 bills | **UNTESTED** — see below | — |
| Q2 | the window fires; a timeout counts as an answer | **UNTESTED** — bills resolved in 13 s, nowhere near the 60 s window | — |
| Q3 | the bill service asks the wallet to move funds to the biller account | **✓ exactly** — 5 × `SETTLEMENT` at 21:55:28 | ✓ |
| Q4 | Paid, and that is correct | **✓** — biller's own records say `PAID` for all five, so paying is right | ✓ |
| Q5 | golden rule holds | **✓** — see §5 | ✓ |
| Q6 | "I don't know" | **still unanswered — the breaker remains unearned** | — |

### 🔴 SURPRISE 1 — the scenario could not test what it was designed to test

`inquire()` does **not** sleep:

```java
public PaymentResult inquire(String reference) {
    return settledByReference.get(reference);   // plain map lookup, always instant
}
```

`sleep(delayMs)` exists only in `pay()`. **The sweep's path is never slowed by biller
latency**, so `N × 2 s` per sweep pass *cannot happen through `inquire`* however hung the
biller is on the payment path.

That invalidates Q1 and Q2 as posed, and means **two scenarios in a row have failed to earn
the circuit breaker** — S01 because a dead process fails in ~1 ms, S02 because the slow path
is not the one the sweep uses.

**What would actually stress the sweep:** latency on the *inquire* endpoint. The simulator
cannot currently do that — a scaffolding change (`sleep(delayMs)` in `inquire`, or a
separate inquire-delay switch) is needed before S02b can run. **Until then topic #5 stays
open, and that is the honest position.**

### 🔴 SURPRISE 2 — five exceptions escaped `payBiller` (a real bug)

```
catch (HttpServerErrorException)   <- not thrown
catch (ResourceAccessException)    <- not thrown either
ACTUAL: org.springframework.web.client.RestClientException
        "Error while extracting response for type [BillerResult]"
```

A read timeout that fires **while the response body is being read** surfaces as
`RestClientException`, not `ResourceAccessException`. Neither catch matches, so all five
escaped into `@Async` and were swallowed by `SimpleAsyncUncaughtExceptionHandler`.

**No harm here** — the sweep recovered every bill 13 s later. But the catch list does not
match reality, which is the same shape as the `original_entry_id` DTO mismatch: only
visible by exercising the path. Any *other* client exception on that path is silently
dropped too.

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (type identity) | 12 | 12 | ✓ |
| suspense account balance | 12 | 12 | ✓ |
| wallet 005100000001 | 2050 | 1975 | ✓ (75 = 5+10+15+20+25, correctly paid) |
| drifted wallets | 0 | 0 | ✓ |

**Held, then settled outward — no money created or destroyed.** The customer paid exactly
what the biller recorded as received.

---

## 6 · What I would fix, and whether I fixed it

**(a) `payBiller`'s catch list is wrong. NOT FIXED — deliberately, pending a decision.**
Catching `RestClientException` (the parent of both) would cover it, but a broad catch also
hides genuinely unexpected failures. The narrower question: which client exceptions should
leave a bill `Reserved` for the sweep, and which are bugs? Every one currently escapes into
`@Async` and is dropped.

**(b) The circuit breaker is STILL not earned.** Two scenarios, no evidence. The honest
position is that topic #5 stays open until a scenario measures a cost the breaker would
remove. **Refusing to add it on two failed attempts is the finding**, not a gap.

---

## 7 · Follow-on scenarios this suggests

- **S02b — slow `inquire`.** Needs a scaffolding change so the simulator can add latency to
  the inquiry path. **This is the only remaining candidate to earn the circuit breaker.**
- **S02c — biller hung and never settles at all** (sleep beyond `timeoutSleepMs`, or a mode
  that accepts and abandons). Combines a slow `pay` with a `NOT_FOUND` inquire, which is the
  case where the settlement window finally matters.
- **Exception-path scenario** — force other client failures (malformed body, connection
  reset mid-read) and check which ones `payBiller` actually catches.

---

## 8 · Log evidence (preserved)

```
21:55:15.355 WARN  [S02-BILL-1] [task-1] BillService : Calling biller gateway ...
21:55:17.363 ERROR [S02-BILL-1] [task-1] SimpleAsyncUncaughtExceptionHandler :
             org.springframework.web.client.RestClientException:
             Error while extracting response for type [BillerResult]
             (2.008 s after the call — exactly the 2 s read timeout)

21:55:28.170 .. 21:55:28.297   5 x SETTLEMENT, one sweep pass
5 escaped exceptions total (one per bill)
```
