# Dev-path status — for the Drive roadmap, tab 4

Generated **31 Aug 2026** from the repo, not from memory.
Project start **11 Jun 2026** · 68 commits · ~3,400 lines of app Java · 32 migrations · 17 tests.

Stage 1 window in the roadmap is **now → ~Oct 26**. Six of eight topics are done with
~2 months left; the two remaining are the largest, and #7 is blocked on work that is not
itself a Stage-1 topic (see "Schedule risk" below).

---

## Stage 1 · now → ~Oct 26

| # | Topic | Done | Landed | Evidence |
|---|---|---|---|---|
| 1 | Docker Compose, service structure, Flyway | ✅ | 16 Jun | 3 services + 3 simulators, own DB each, 32 migrations |
| 2 | Database design & transactions | ✅ | 16 Jun → 28 Aug | append-only ledger, 13 constraints, deterministic lock ordering, partial indexes measured at 500k rows |
| 3 | Idempotency | ✅ | 16 Jun | key + unique constraint on every money endpoint; replay returns the stored record |
| 4 | Async messaging + outbox | 🔨 **partial** | 3–4 Aug (wallet) · 31 Aug (bill write) | wallet outbox + relay done and verified; **bill relay not built — no AMQP wiring in the bill service yet** |
| 5 | Sagas, timeouts, circuit breakers | 🔨 **partial** | 26 Jun → 27 Aug | reserve/capture/compensate + EOD sweep done; 2s timeouts set. **No Resilience4j, no retry-with-backoff, no circuit breaker anywhere in the repo** |
| 6 | Structured logging + correlation IDs | ✅ | 12–15 Aug | every boundary incl. `@Async`, `@Scheduled`, outbox, AMQP. One transfer → 5 lines across 2 JVMs and a broker, from one grep |
| 7 | **SABOTAGE PHASE** | ⬜ | — | **not started.** Traceability gate cleared 15 Aug; deliberately blocked on having all 4 services so the pass is run once |
| 8 | Load testing with k6 | ⬜ | — | not started. Requires a written breaking-TPS prediction first |

## Stage 2 · ~Nov 26 → Apr 27 — items already done early

| # | Topic | Done | Landed | Evidence |
|---|---|---|---|---|
| 9 | Global error handling (RFC 9457) | 🔨 **partial** | 24 Aug | `ProblemDetail` throughout the wallet incl. structured 409s carrying recovery data; bill service partial |
| 10 | Testcontainers integration tests | ✅ | Jul–Aug | 17 tests against real Postgres (9 wallet, 8 bill) |

Topics 11–22 not started.

---

## Milestones, by date

| date | milestone |
|---|---|
| 11 Jun | project start |
| 16 Jun | walking skeleton — services, Flyway, ledger, idempotency |
| 26 Jun | bill saga — reserve / capture / compensate |
| 3–4 Aug | transactional outbox + relay (wallet) |
| 10 Aug | notification service complete — retry job, per-channel terminal state |
| 12–15 Aug | correlation IDs across all three services and every async boundary |
| 23–24 Aug | ledger transaction types + discharge invariants; `hold` / `settle` endpoints |
| 27 Aug | bill service switched to the new endpoints; reconciles on 409 |
| 28 Aug | notifications suppressed for the whole hold lifecycle |
| 31 Aug | bill outbox — events written on every resolved outcome |

---

## Not built, and why it matters to the schedule

**Bill relay** — the last piece of topic #4. The bill service has no
`spring-boot-starter-amqp`, no `spring.rabbitmq.*`, no exchange bean. Events are being
written and nothing publishes them.

**History service #4** — not started. **This is the schedule risk:** it is not a Stage-1
topic, but the sabotage phase (#7) was deliberately gated behind having all four services
so the pass is run once rather than repeated. So #7 cannot start until a non-Stage-1 item
lands.

**Resilience4j** — topic #5 names circuit breakers explicitly and there is none in the
repo. Timeouts exist; retry-with-backoff and circuit breaking do not.

**k6** (#8) — not started.

---

## Suggested edits to the Drive sheet

Tab 4, **Done** column:

```
#1  ✅ 16 Jun
#2  ✅ 16 Jun → 28 Aug
#3  ✅ 16 Jun
#4  🔨 partial — bill relay pending
#5  🔨 partial — no Resilience4j / circuit breaker
#6  ✅ 12–15 Aug
#7  ⬜ blocked on history service #4
#8  ⬜
#9  🔨 partial (Stage 2, started early)
#10 ✅ (Stage 2, done early)
```

Tab 1 timeline — QuickPay Stage 1 is ~75% complete and on track for the Oct window,
**provided** history #4 lands in time to unblock the sabotage phase.

Tab 2 checkpoints — the Oct 2026 row notes "QuickPay DoD signs this month → AWS on-ramp
opens". DoD is not yet signed; #7 and #8 are the outstanding gates.