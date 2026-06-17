# ADR-0001 — Two-leg single-row ledger

- **Status:** Accepted
- **Date:** 2026-06-14
- **Deciders:** Abdulaziz

## Context
Every money movement must be recorded so the golden rule — money is never
created or destroyed — is guaranteed by the database itself, not by trusting
application code to be correct. A transfer moves an amount from one wallet to
another; the recording model has to make "the two sides always net to zero"
structurally impossible to violate. Constraints in force: no gold-plating, and
fees / 3+-leg transactions are not a current requirement.

## Options considered
1. **Two-leg single row** — one ledger row holds both sides of a movement:
   `debited_amount` (negative) and `credited_amount` (positive) for the same
   transaction. Conservation is a column-level invariant.
2. **Parent + child rows** — a `transaction` parent with N child one-leg rows.
   Naturally supports fees and 3+ legs, but conservation becomes a
   cross-row sum that the DB can't enforce with a single simple CHECK.
3. **total / available / hold balance columns** (considered for two-phase
   payments) — rejected: no reserve/capture requirement exists, and
   lock-ordering already solves the concurrency problem without it.

## Decision
Use the two-leg single-row model. Each transfer is one ledger row carrying
both the debit and the credit.

## Consequences
- ✅ The DB enforces conservation with a single `CHECK(debited_amount +
  credited_amount = 0)` — the core lesson of the project. A bug that moves
  money one-sided cannot be committed.
- ✅ Simple to reason about and to reconcile (balance == SUM of a wallet's legs).
- ❌ Cannot represent fees or 3-plus-leg transactions without a schema
  migration and a model change.
- 🔁 Revisit ONLY if a concrete >2-leg requirement appears (e.g. real fees).
  Until then, do not switch to parent/child — it trades away the DB-enforced
  conservation CHECK for flexibility we don't need.