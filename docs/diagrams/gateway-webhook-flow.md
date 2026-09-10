# Gateway webhook flow (top-up via external payment gateway)

Built & verified 2026-06-16/17 (PR#2). The gateway simulator (`scaffolding/gateway-simulator`,
:9090) sends an HMAC-signed webhook to the wallet service (`POST /v1/gateway/webhook`, :8080),
which credits the customer exactly once even if the callback is redelivered.

```mermaid
sequenceDiagram
    autonumber
    participant gw as Gateway simulator
    participant wallet as Wallet service (/v1/gateway/webhook)
    participant inward as Inward system account

    gw->>wallet: POST raw JSON body + X-Signature (HMAC-SHA256, lowercase hex)
    wallet->>wallet: Recompute HMAC, constant-time compare (MessageDigest.isEqual)

    alt signature invalid
        wallet-->>gw: 401 Unauthorized (reject forgery)
    else signature valid
        wallet->>wallet: Parse WebhookRequest (walletNumber, amount, gatewayTxnId)
        wallet->>wallet: topUp(walletNumber, amount, idempotencyKey = gatewayTxnId)
        alt new idempotency key
            inward-->>wallet: debit inward, credit customer (one ledger row)
            wallet-->>gw: 200 OK (credited once)
        else duplicate key (redelivery)
            wallet->>wallet: UNIQUE(idempotency_key) hit -> DuplicatedEntryException, caught
            wallet-->>gw: 200 OK (no double credit)
        end
    end

    Note over gw,wallet: gatewayTxnId = idempotency key.<br/>Return 200 on duplicates so the gateway stops redelivering.<br/>"callbacks may be delivered more than once" is handled by UNIQUE(idempotency_key).
```

**Key guarantees**
- **Authenticity:** HMAC-SHA256 over the raw body, constant-time compare → forged calls get 401.
- **Exactly-once credit:** `gatewayTxnId` is the idempotency key; `UNIQUE(idempotency_key)` on the
  ledger makes redelivery a no-op.
- **Stop the retries:** duplicates return **200** (not an error) so the gateway stops redelivering.