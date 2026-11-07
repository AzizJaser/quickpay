# One-shot prompt: generate BACKLOG.md from the repo

Run once in Claude Code at the QuickPay root, after the new `CLAUDE.md` is committed.

---

Act as Product and Ops together for this one task. Read, in this order, and **do not read source code yet**:

1. `PROJECT_PLAN.md` — especially: the NEXT ACTION block; "PHASE 8 GAP, found 22 Sep — the relay cannot keep up"; §5 Open decisions; §6 Remaining work, in particular **Bucket D — cross-cutting concerns (deliberately deferred, NOT forgotten)**, **Bucket C — design debt**, **Small deferred refinements**, and **Known test-coverage boundary**; every line containing "deferred", "parked", "never ran" or "hole".
2. `docs/sabotage/PHASE7_REPORT.md` and `docs/sabotage/README.md` — scenarios listed but not executed; S08 ("outbox guarantees delivery to the broker, not to a consumer" — recovery deferred); S09 (what the invariants cannot see).
3. `docs/load/PHASE8_REPORT.md` — the pool-vs-CPU finding and what was never tried after it.
4. `docs/security/THREAT_MODEL.md` (15 open questions), `docs/security/ASVS-L1.md` (every FAIL), `docs/security/A04-idor-money-path.md`, `docs/security/A04b-authz-status-degrades-across-hops.md`.
5. `adr/` (all), `ROADMAP_DEV_PATH_STATUS.md`, `quickpay-project-brief.md` (requirements + Sponsor decisions log, 7 entries).
6. `docs/diagrams/*.md` and `docs/migrations/cif-backfill-2026-09-19.md`.

Produce `BACKLOG.md` with tickets drawn **only from what those documents say** — gaps they name, work they mark deferred, findings they leave open, scenarios they list but never ran. Do not invent features. Expected sources include at least: service-to-service auth (5B, "hole on record"); gateway webhook authenticity; the ASVS-L1 FAILs; S08 recovery; the relay throughput gap; the pool-vs-CPU follow-up; Phase 7 scenarios not executed; input validation / decimal precision; login rate limiting; the deferred refinements list; the test-coverage boundary; History/statements as a **candidate** requirement for Product to reinstate (decisions-log entry 6 parked it — flag it, do not decide it).

Ticket format, one per item:

```
### QP-<n> · <title>
Type: feature | security | performance | ops | debt
Source: <file and section that justifies it — quote one line>
Issue: <one paragraph — what is wrong or missing today, in plain words, and what it risks>
Acceptance criteria: <2–4 bullets, each testable>
Risk to the golden rule: none | indirect | direct
Size: S (one evening) | M (two) | L (three+, split before starting)
Status: todo
```

Then:
1. Order the list the way Product would: money-safety first, then customer-visible value, then performance, then debt. One line of reasoning per position for the top five.
2. Flag any ticket that is really two tickets and split it.
3. Add an `## ops` section with up to three non-functional tickets Ops would raise after reading the load and sabotage reports (health checks, observability, secrets handling) — same format.
4. Add `## Candidate requirements (for Product to rule on)` — History/statements and anything else the documents call a "deferred candidate" (e.g., the AI feature). Not tickets yet.
5. End with `## Sponsor decisions log (carried forward)` copied verbatim from `quickpay-project-brief.md`, so Product stays consistent with past rulings.

Do not start any ticket. Do not modify any other file. When `BACKLOG.md` is written, say only: "Backlog ready — N tickets, M candidates. Product recommends starting with QP-<n> because <one line>."
