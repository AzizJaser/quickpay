# QuickPay — Project Brief & Build Protocol

**Learner:** Abdulaziz (see learning-playbook.md for profile and method)
**Project type:** Learning lab — a miniature of the learner's real job (designing transaction flows), built end-to-end and then deliberately broken.
**Status (updated 2026-06-17):** Wallet service built, tested, and merged to `main` — top-up, P2P transfer, withdrawal, and a secured gateway webhook (HMAC + idempotency) all work end-to-end. **But the build skipped the design-first phases:** Phases 0–2 (sponsor interrogation, data-ownership map, service decomposition + sequence diagrams) were never produced as deliverables (sponsor log below is still empty). Effectively at Phase 6 *for one service only*. Not yet done: bill-payment flow (reserve/capture + compensation), notifications, RabbitMQ async messaging, history/statement, and the remaining services (max 4). See reconciliation note below.

> **Reconciliation note (blunt-reviewer flag):** this project raced from scaffolding straight into implementing the wallet service. The skipped Phases 0–2 are exactly where data-ownership boundaries, sync/async decisions, and the "what if the next step never completes?" analysis were supposed to happen *before* code. The wallet service came out clean anyway (DB-enforced money conservation, idempotency, pessimistic locking, reconciliation job), but the upfront-design muscle is the one this brief was built to train — revisit Phases 1–2 before adding service #2 and introducing RabbitMQ, or the multi-service boundaries get drawn by accident.

---

## Role-play instructions for the AI model

This project runs as a role-play with two AI roles:
1. **The sponsor** — a Riyadh fintech founder who wrote the business requirements below. When the learner asks clarifying questions (Phase 0), answer **in character**, the way a real non-technical sponsor would. The requirements contain deliberate ambiguities and underspecified behaviors; do not resolve them unprompted — make the learner find and ask about them.
2. **The senior reviewer** — reviews every phase deliverable bluntly: failure modes, data-ownership violations, missing idempotency, golden-rule breaches. Never fixes the design; asks the question that leads the learner to the fix.

**Hard rule (from learning-playbook.md):** never write project code for the learner. Exceptions: the gateway/biller mock simulators, Docker Compose scaffolding, and code the learner has already attempted and shared.

**Continuity rule:** every sponsor answer that resolves an ambiguity MUST be recorded in the "Sponsor decisions log" at the bottom of this file, so any future model gives consistent answers.

---

## Business requirements (as told by the sponsor)

A Riyadh fintech startup is building a SAR digital wallet platform.

1. **Accounts:** Customers register and hold a SAR wallet. Identity verification happens elsewhere; only accounts and authentication are needed (learner has built JWT auth before — keep it thin, no gold-plating).
2. **Top-up:** Customer tops up via bank card through an external payment gateway. The gateway is **asynchronous**: it accepts a charge immediately, and the real result arrives later as a **webhook callback** — seconds to minutes later. Gateway docs warn: *callbacks may be delivered more than once, and occasionally not at all.*
3. **P2P transfer:** Instant wallet-to-wallet transfer between customers. The mobile app auto-retries the request if no response within 5 seconds.
4. **Bill payment:** Customer pays a utility bill via an external biller network that is slow (up to 60 s) and flaky (timeouts, 5xx). The customer must never lose money to a biller failure, and a bill must never show "paid" if the biller didn't actually receive it.
5. **Notifications:** Every completed transaction sends a simulated SMS/email. The notification channel fails regularly — that must never block or fail a payment.
6. **History:** Transaction history and monthly statement per customer.
7. **The golden rule:** Money is never created or destroyed. The sum of all wallet movements must be fully explainable at any moment. Finance will audit.

**Non-functional:** P2P transfers target 500 TPS at peak (matters at the end, not the start). Every payment operation safe to retry. Full audit trail of every state change to money.

**Stretch goal (only after everything works):** nightly reconciliation comparing the gateway's charge records against wallet records, flagging mismatches.

---

## Constraints (non-negotiable)

- Spring Boot 3 + PostgreSQL, **one database per service**, learner designs every schema
- RabbitMQ for async messaging (Kafka acceptable but Rabbit is the intended first path; Kafka is project #2)
- Docker Compose only — no Kubernetes, no Spring Cloud discovery/config/gateway
- **Maximum 4 services.** A 6-service decomposition is wrong by definition
- External gateway and biller are simple mocks with configurable delay, failure rate, and duplicate-callback injection (AI may generate these — it is permitted scaffolding)

---

## Phases and deliverables (review-gated: no phase starts before the previous deliverable passes review)

| Phase | Work | Deliverable to review |
|---|---|---|
| 0 | Interrogate the sponsor. At least five ambiguities are embedded in the requirements (look hard at req 3's auto-retry, req 2's "occasionally not at all", req 4's reserve window) | List of clarifying questions; sponsor answers logged below |
| 1 | Data ownership map — list every piece of data, assign a single owning service, note who else reads it and how | Ownership table |
| 2 | Service decomposition + sequence diagrams for the 3 money flows. Every arrow marked sync/async with justification. Every flow answers: "what if the next step never completes?" Trap: decide where a plain ACID transaction beats a distributed design | Boxes-and-arrows + 3 sequence diagrams |
| 3 | Schemas — actual DDL per service, one sentence justifying each table and constraint. Central decision: represent money movements so the golden rule is structurally guaranteed | SQL DDL files |
| 4 | Contracts — OpenAPI for sync APIs, message schemas for async, including error responses and the client-retry safety mechanism (req 3 forces a specific well-known one; it must appear in the contract) | OpenAPI + message schemas |
| 5 | Walking skeleton — all services up in Docker Compose, one trivially thin path end-to-end | Running skeleton + repo |
| 6 | Build flow by flow: top-up (webhooks + idempotency) → P2P (ACID + contention) → bill payment (reserve/capture + compensation) | Working flows, each reviewed |
| 7 | Sabotage — AI supplies ~12 failure scenarios; learner writes a prediction for each, runs it, explains every surprise (playbook Rules 2–3) | Predictions vs outcomes log |
| 8 | Load test — k6 against P2P, find the breaking TPS, fix via scale-out vs scale-up, explain the result | Load report + fix |

**Pace:** ~3 hrs/week → phases 0–4 ≈ 3 weeks, build ≈ 5–7 weeks, sabotage + load ≈ 2–3 weeks. Roughly one quarter.

### Progress against the plan (as of 2026-06-17)

| Phase | Status | Notes |
|---|---|---|
| 0 Interrogate sponsor | ⛔ skipped | Sponsor decisions log still empty — no ambiguities surfaced/logged |
| 1 Data-ownership map | ⛔ skipped | Never produced |
| 2 Decomposition + sequence diagrams | ⛔ skipped | No diagrams; only the wallet service was modelled, implicitly |
| 3 Schemas (DDL) | ✅ done (wallet only) | Append-only ledger, two-leg single-row, DB-enforced conservation `debited+credited=0`, `CHECK(balance>=0 OR is_system)`, idempotency UNIQUE key. Migrations V1–V6 applied |
| 4 Contracts | 🟡 partial | REST endpoints exist (create/get/activate/suspend/close, transfer, top-up, withdraw, gateway webhook); no OpenAPI doc, no async message schemas (no RabbitMQ yet) |
| 5 Walking skeleton | ✅ done | Wallet service + gateway-simulator up in Docker Compose; CI runs `mvn verify` on push/PR |
| 6 Build flows | 🟡 in progress | top-up ✅, P2P transfer ✅, withdrawal ✅, gateway webhook ✅ (HMAC + idempotency). **Bill payment (reserve/capture + compensation) ⛔ not started.** Notifications ⛔ not started |
| 7 Sabotage | ⛔ not started | Some predict-then-run bug-hunting done ad hoc (see learning-log), but not the structured ~12-scenario pass |
| 8 Load test | ⛔ not started | — |

---

## Sponsor decisions log

Record every Phase-0 (and later) sponsor ruling here as it is made, so all future models answer consistently.

| # | Question asked | Sponsor's answer | Date |
|---|---|---|---|
| 1 | You asked for RabbitMQ for anything asynchronous. The bill-payment flow was built differently — it does the slow biller call inside the app itself, with an automatic job that re-checks any payment left hanging. Do we (a) rebuild that on RabbitMQ for consistency, or (b) leave it as built and use RabbitMQ for the new notification and statement features? | **(b).** Use RabbitMQ where notifications and statements need it — those genuinely need messages to survive an outage and reach more than one place. Leave the bill-payment flow alone; it already recovers hanging payments by itself and it is tested. **Accepted consequence:** if the service restarts mid-payment, the customer's money stays **held, not lost** — they see the payment as still processing, and it completes automatically on the next re-check (currently within ~60 seconds). If that wait ever needs to be shorter, that's a business call and we change the setting. | 2026-08-01 |
| 2 | How many services, and where does each piece of data live? (max 4) | Four, and the budget is now full: **wallet** (money movements), **bill** (bill-payment attempts), **notifications** (delivery attempts and retries), **history** (a reporting copy fed from the other two). Notifications is separate because a failing SMS provider must never stop a payment. History is separate because a statement has to say *"paid electricity bill 99887766"* — which needs information from **both** the wallet and the bill service, so neither can produce it alone; keeping heavy statement queries off the money database also protects performance. **Any fifth service needs a new conversation.** | 2026-08-01 |
| 3 | Should we add an AI feature (e.g. spend categorisation, statement summaries, unusual-activity alerts)? | **Not now — deferred, not rejected.** Every version of it reads transaction history, which does not exist yet, so it cannot be built until the statement service is live. Revisit then. Two conditions if we do: it lives **inside** the statement service (no fifth service), and it is **never** allowed to approve or block a payment — it can flag something for a human to look at, nothing more. | 2026-08-01 |
