# Gateway Simulator (mock payment gateway)

Dev/test only. Pretends to be a payment gateway: sends **HMAC-signed webhooks**
to the wallet service so you can exercise the money-in flow, idempotency
(redelivery), and signature rejection.

Runs on **:9090**; the wallet service runs on **:8080**.

## Run
```
cd scaffolding/gateway-simulator
mvn spring-boot:run
```

## The webhook contract (what the wallet must implement)
- `POST http://localhost:8080/v1/gateway/webhook`
- Header `X-Signature: <hex HMAC-SHA256 of the RAW body, keyed with the shared secret>`
- Body `{"walletNumber":"...","amount":5000,"gatewayTxnId":"..."}`
- Shared secret (both sides): `super-secret-key` (see `application.yml` here and the wallet's config)

The wallet must verify the HMAC **over the raw received bytes** (not a re-serialized
copy), reject with **401** on mismatch, otherwise call `topUp(walletNumber, amount,
gatewayTxnId)` and return **200** — even on a duplicate (so the gateway stops redelivering).

## Control panel
```
# 1. simulate a payment (valid, signed) — returns the gatewayTxnId + the wallet's status
curl -X POST "http://localhost:9090/simulate/payment?walletNumber=001717171717&amount=5000"

# 2. redeliver the SAME webhook (at-least-once delivery) — wallet must NOT double-credit
curl -X POST "http://localhost:9090/simulate/redeliver/<gatewayTxnId>"

# 3. forge a webhook with a bad signature — wallet must reject (401)
curl -X POST "http://localhost:9090/simulate/forge?walletNumber=001717171717&amount=5000"
```