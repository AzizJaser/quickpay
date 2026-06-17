# Architecture Decision Records

One file per significant design decision: context, options, decision,
consequences (see `learning-playbook.md`, Rule 6). Append-only — a decision is
never edited after it's Accepted; if it changes, write a new ADR that
supersedes the old one.

Start a new record by copying `0000-template.md` to `NNNN-short-title.md`.

## Index

| # | Decision | Status | Date |
|---|----------|--------|------|
| 0000 | _Template (not a real decision)_ | — | — |
| [0001](0001-two-leg-ledger.md) | Two-leg single-row ledger | Accepted | 2026-06-14 |
| [0002](0002-system-accounts-may-go-negative.md) | System accounts may go negative | Accepted | 2026-06-16 |
| [0003](0003-applied-migrations-are-immutable.md) | Applied Flyway migrations are immutable | Accepted | 2026-06-16 |