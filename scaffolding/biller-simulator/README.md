# Biller Simulator (mock biller network)

Dev/test only. Pretends to be an **external biller** — the slow, flaky third party the bill service
pays utility bills through. Unlike the gateway simulator (which *pushes* webhooks to the wallet),
the biller is a **server the bill service calls**: it exposes a **pay** endpoint and an **inquiry**
endpoint, dedupes on the caller's **reference**, and can be told to fail / stall on demand.

Runs on **:9091** (gateway simulator :9090, wallet service :8080).

## Run
```
cd scaffolding/biller-simulator
mvn spring-boot:run
```

## The biller contract (what the bill service codes against)

**Pay a bill** — idempotent on `reference`:
```
POST http://localhost:9091/biller/v1/payments
Body: {"billNumber":"Z-123","amount":12345,"reference":"A"}
```
- `reference` = the bill service's own id for this attempt (ref A). The biller **dedupes on it** —
  the same reference always replays the same result, never pays twice. *This is the contract that
  makes the bill service's retries safe.*
- `200` + `{"status":"PAID","billerTxnId":"BILR-...","reference":"A",...}` — accepted.
- `422` + `{"status":"FAILED",...}` — **definite** failure, money NOT taken (safe to reverse).
- `503` — **server error: outcome UNKNOWN.** The call may or may not have landed → inquire.
- *(no response)* — in `TIMEOUT` mode the biller never answers in time; the caller times out → inquire.

**Inquire by reference** — "did my attempt A actually land?":
```
GET http://localhost:9091/biller/v1/payments/A
```
- `200` `PAID` / `FAILED` — we settled this reference.
- `404` `NOT_FOUND` — we never settled it, so it **definitely did not land** (safe to retry or reverse).

> Why inquire by *reference* and not by bill number: the reference maps 1:1 to the bill service's
> own pending record, so the answer is unambiguous ("did *my* attempt land?"). A bill number could
> be paid by a different attempt and muddy the answer.

## Control panel (force outcomes deterministically — predict-then-run)
```
# Force the next pay calls to SUCCESS / FAIL / SERVER_ERROR / TIMEOUT (or NORMAL = use failure-rate)
curl -X POST http://localhost:9091/simulate/mode \
     -H 'Content-Type: application/json' -d '{"outcome":"SERVER_ERROR"}'

# Add latency without changing the outcome (e.g. simulate a 3s-slow biller)
curl -X POST http://localhost:9091/simulate/mode \
     -H 'Content-Type: application/json' -d '{"outcome":"SUCCESS","delayMs":3000}'

# Inspect current mode + settled references
curl http://localhost:9091/simulate/state

# Clear all settlements and return to NORMAL
curl -X POST http://localhost:9091/simulate/reset
```

## How the modes map to the flow's branches
| Force this | Biller does | Exercises the bill service's… |
|---|---|---|
| `SUCCESS` | 200 PAID | happy path → capture (temp → biller) |
| `FAIL` | 422 FAILED, nothing taken | negative path → reverse (temp → customer) |
| `SERVER_ERROR` | 503, **nothing settled** | unknown path → inquire → NOT_FOUND → retry/reverse |
| `TIMEOUT` | stalls past your timeout, then settles **PAID** | unknown path → inquire → PAID → capture (the "it actually went through" case) |

The two `unknown` cases are the whole point of the design: `SERVER_ERROR` is "unknown and *not*
paid", `TIMEOUT` is "unknown but *actually* paid" — and an inquiry by reference tells them apart.