# ADR-0005 — RabbitMQ for event fan-out; `@Async` retained for point-to-point external calls

- **Status:** Accepted
- **Date:** 2026-08-01
- **Deciders:** Abdulaziz
- **Relates to:** the brief's hard constraint *"RabbitMQ for async messaging"*; the
  service decomposition decided the same day (wallet, bill, notifications, history).

## Context

The brief names RabbitMQ a **hard constraint** for async messaging. The bill-payment
flow, built June–July, diverged from it: the biller call runs on in-process Spring
`@Async`, with a scheduled EOD sweep re-inquiring any bill left stranded in `Reserved`.
That divergence worked and is tested, but it was never declared — which is the part that
needed fixing regardless of which way the decision went.

Two remaining requirements force the question now:

- **Req 5 (notifications):** every completed transaction sends an SMS/email. *"The
  notification channel fails regularly — that must never block or fail a payment."*
- **Req 6 (history):** transaction history and monthly statements, decided the same day
  to live in a separate **read model** service with its own database.

Both need the **same events**, from **two different publishers** (the wallet moves money;
the bill service knows bill context), delivered to **two independent consumers**. That is
a fan-out problem, and it is a different shape from what the bill service does today.

## Options considered

1. **HTTP fan-out** — the wallet and bill service call notifications and history directly.
   Rejected on three counts. **No durability:** if a consumer is down the event is lost
   permanently — money moved and nobody was ever told, and the read model is silently
   wrong forever. **No buffering:** at the 500 TPS P2P target, a consumer that can only
   absorb a fraction of that makes its slowness the *producer's* problem — either the
   wallet blocks or events are dropped. **Coupling:** publishers must know every
   consumer's endpoint and handle its failures, and adding a third consumer means
   changing the wallet — which is exactly what req 5 forbids.
   *(Note: the objection is not that HTTP is slow. A local call is ~1 ms and would not be
   the bottleneck. The objection is that HTTP has no buffer and no memory.)*
2. **Polling** — consumers poll the wallet and bill databases for new rows. Rejected:
   it violates the one-database-per-service constraint and makes every consumer depend on
   another service's schema.
3. **RabbitMQ everywhere, including retrofitting the bill service's `@Async` biller
   trigger onto a durable queue.** Considered seriously, because it closes a real hole —
   `@Async` is in-memory, so a crash loses in-flight work. Rejected on cost/benefit: it
   rewrites a working, tested path, and the EOD sweep already bounds the damage. One
   async mechanism everywhere is a genuine benefit, but not one worth buying today.
4. **RabbitMQ for event fan-out only; `@Async` retained for the biller call.** Chosen.

## Decision

**Adopt RabbitMQ for domain-event fan-out.** The wallet and the bill service publish
domain events; notifications and history consume them independently.

**Keep the bill service's biller trigger on `@Async` + the EOD sweep.** It is a
point-to-point call to an **external** system with an existing recovery job — not a
fan-out. A broker cannot deliver to the biller anyway (the biller is an HTTP API, not a
consumer), so routing through a queue would add a hop without adding a guarantee the
sweep does not already provide.

The distinction being drawn: **fan-out to internal consumers goes through the broker;
point-to-point calls to external systems do not.**

## Consequences

- ✅ **Durability.** A consumer being down no longer destroys the event — the broker
  holds it until delivery succeeds. This is what makes req 5 satisfiable at all.
- ✅ **Fan-out without coupling.** Adding a consumer requires no change to any publisher.
- ✅ **Failure isolation is structural, not disciplinary.** Notifications cannot block a
  payment because the wallet publishes and forgets — it is not something the wallet has
  to remember to get right.
- ✅ **Buffering under load.** The queue absorbs bursts, so a slow consumer does not apply
  back-pressure to the money path. Matters directly for Phase 8 (500 TPS on P2P).
- ✅ **A working, tested bill-payment path is left intact** rather than rewritten for
  consistency's sake.
- ❌ **New infrastructure.** RabbitMQ joins docker-compose, with new dependencies and a
  new failure mode of its own (broker unavailable at publish time — see the open item).
- ❌ **Two async mechanisms now coexist**, which is an inconsistency anyone reading the
  code will notice. This ADR is the answer to "why?".
- ❌ **The `@Async` in-memory gap is knowingly retained.** If the bill service dies
  between the reserve and the biller call, that in-flight work is gone from memory: the
  bill sits `Reserved` with the customer's funds **held in suspense**, and the customer
  sees the payment as still processing. The EOD sweep recovers it on its next tick —
  bounded by `bill.eod-interval-ms` (default 60 s). **The money is held, never lost**;
  the wallet's ledger and its `UNIQUE(idempotency_key)` guarantee that independently.
- 🔁 **Revisit if:** the sweep interval proves too slow for a stuck payment (a business
  call, not a technical one), or a second point-to-point async path appears — at which
  point replacing the `@Async` pattern wholesale becomes worth the rewrite.

## ⚠️ Open follow-up — NOT decided here

**Reliable publishing.** If a service commits a money movement and *then* publishes to
RabbitMQ, a failed publish means money moved with no event: history is silently wrong
forever and no notification is ever sent. Adopting a broker does not solve this by
itself — it moves the problem to the publish boundary.

The expected answer is the **transactional outbox**: write the event into an `outbox`
table *in the same transaction as the money move*, then a separate poller ships it to the
broker and marks it sent. This is the same write-ahead shape already used for the bill
record — record the intent durably first, act second.

This needs its own decision (and likely its own ADR) **before the wallet publishes
anything**.