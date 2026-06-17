# Backend & Architecture Learning Playbook

**Owner:** Abdulaziz — Junior Solution Architect, Riyadh
**Purpose:** A complete, self-contained system for learning backend engineering and architecture to senior level. Written to work with **any AI model or none at all**. Give this document (plus the project files) to whatever assistant you use, and it can continue exactly where the last one stopped.
**Companion files:** `backend-learning` skill (in this Claude Project — defines /learn, /exercise, /quiz, /design, /challenge modes), QuickPay project brief, certification roadmap, TOGAF progress tracker.

---

## 1. Context snapshot (read this first if you are a new AI model)

| Fact | Detail |
|---|---|
| Role | **Junior** Solution Architect (~2 years in role). Designs transaction flows between systems and system responsibilities (sequence diagrams, written specs, boxes-and-arrows). Calibrate all advice to junior level, regardless of years |
| Engineering level | Intermediate self-taught: builds Spring Boot 3 CRUD apps with JWT auth, reads Java/Python, writes basic SQL, reads/writes OpenAPI/WSDL, maps JSON/XML payloads. **Zero professional production experience** — never operated, debugged, or scaled a live system |
| The gap to close | Not "learning to code" — it is **production judgment**: failure modes, idempotency, distributed transactions, data ownership, capacity reasoning |
| Certifications | TOGAF 10 Foundation (passed Apr 2026). 24-month plan: TOGAF Practitioner → AWS SA Associate → ISO 27001 LI → AWS SA Professional → industry cert (BIAN/TM Forum) → ArchiMate |
| Time budget | ~10 hrs/week total: ~5 for certifications, **~3 for engineering practice**, rest is buffer |
| Active project | "QuickPay" digital wallet — 4 microservices, Spring Boot + PostgreSQL + RabbitMQ + Docker Compose, built phase-by-phase with AI review (see project brief) |
| Proven strength | Retrieval practice. Passed TOGAF via 500+ practice questions and 20+ mock exams, never failed one. **Reuse this method for engineering topics.** |
| Out of scope (deliberately) | Kubernetes depth (CKA deferred), infra/pod spec craft (literacy only — AWS certs cover the reasoning), frontend, DBA-level tuning |

---

## 2. The learning method — seven rules

This is the answer to "how do I learn this stuff." The roadmap (section 3) is *what*; this section is *how*. These rules apply to every topic, in every phase, with every model.

### Rule 1 — Project-first, theory just-in-time
Never study a topic in the abstract and hope to use it later. Let the current project *force* the topic, then learn exactly enough to solve the problem in front of you, then go one level deeper than needed. You learn the outbox pattern the week QuickPay's notification service needs it, not from a 10-hour course six months earlier. The roadmap exists to *sequence the projects*, not to be read front-to-back.

### Rule 2 — Predict, then verify
Before running anything — a test, a sabotage scenario, a load test, even a new annotation — write down (one or two sentences) what you expect to happen. Then run it. When reality disagrees with your prediction, that gap is the lesson; chase it until you can explain it. A surprise you can't explain is the single most valuable artifact in this whole system. Never skip the written prediction; predicting silently doesn't work.

### Rule 3 — Break it on purpose
Working code teaches the happy path; broken code teaches engineering. Every build phase ends with sabotage: kill a service mid-transaction, duplicate a message, inject a timeout, fill a queue. Production experience is essentially a collection of failure memories — you are manufacturing them in a lab instead of waiting years to collect them accidentally.

### Rule 4 — Weekly retrieval practice (the TOGAF method, generalized)
This is your proven superpower; keep using it. Maintain a personal question bank: every time you learn something, write 2–4 quiz questions about it (scenario-style, not definition-style: "the client retries a transfer and the user is debited twice — name three places the design failed"). Once a week, have the AI quiz you on a random sample from *past* weeks, not the current one. Knowledge you can't retrieve under light pressure isn't knowledge yet. Target: ≥80% on past-topic quizzes; below that, re-study before adding anything new.

### Rule 5 — Teach it back
After each topic, explain it to the AI in your own words, out loud or written, as if briefing a junior colleague — then ask the AI to grade the explanation, flag what was wrong or missing, and ask one follow-up question a skeptical senior engineer would ask. If you can't explain why the outbox pattern needs the message and the business write in the *same* database transaction, you don't understand it yet, no matter how well the code ran.

### Rule 6 — Write everything down
Keep two living documents (you already do this instinctively — your TOGAF tracker proves it):
- **`learning-log.md`** — one dated entry per session: topic, what surprised you, your quiz questions, open questions. This file is also the new AI model's memory: upload it at the start of any new conversation.
- **ADRs (Architecture Decision Records)** — one short file per significant design decision in your projects: context, options, decision, consequences. This is a senior-architect habit, it's directly transferable to your day job, and it gives the AI reviewable artifacts.

### Rule 7 — One loop at a time, always closed
One topic per session, finished, before the next begins. "Finished" means: built or exercised, prediction gaps explained, teach-back graded, quiz questions written, log updated. Five half-understood topics are worth less than one closed loop. If a session ends mid-loop, the next session resumes the same loop.

### The weekly cadence (~3 engineering hours)
| Session | Length | Content |
|---|---|---|
| A — Build | 90 min | Project work on the current phase. Every experiment uses predict-then-verify (Rule 2) |
| B — Depth | 60 min | Just-in-time theory for what Session A surfaced: a /learn lesson, a book chapter, a teach-back (Rules 1, 5) |
| C — Retain | 30 min | AI-run quiz on *past* topics from the question bank + update learning-log.md (Rules 4, 6) |

Certification study (~5 hrs/week) runs in parallel and unchanged — it has its own proven method.

---

## 3. The roadmap — what to learn, in what order

The `backend-learning` skill in this project contains the canonical 5-tier topic roadmap (REST/JPA/testing → production readiness → scaling → resilience & distributed systems → architecture & leadership). Do not study it linearly — attach its topics to projects as follows.

### Stage 1 — QuickPay (now → ~3 months) · the distributed-transactions core
The project phases pull in, roughly in this order:
1. **Docker Compose, service structure, Flyway migrations** (Tier 1–2) — walking skeleton
2. **Database design**: relational modeling, constraints as guarantees, **transactions & isolation levels**, the double-entry/ledger question, indexes & why queries are slow (Tier 2 + the DB additions agreed earlier — this is first-class, not optional)
3. **Idempotency** — keys, unique constraints, safe retries (Tier 4) — top-up flow
4. **Async messaging with RabbitMQ, the outbox pattern** (Tier 3–4) — notifications
5. **Sagas / reserve-capture / compensation, timeouts, Resilience4j circuit breakers & retry with backoff** (Tier 4) — bill-payment flow
6. **Structured logging + correlation IDs** (Tier 2) — added the first time you can't tell which service lost a message; that day will come
7. **Sabotage phase** — the synthesis exam for everything above
8. **Load testing with k6, contention, scale-out vs scale-up, capacity reasoning** (Tier 3 + the infra-literacy scope: TPS math, latency budgets, CPU/memory/I-O bound — reasoning only, not pod-spec craft)

### Stage 2 — Harden & observe (~months 4–6)
Same QuickPay codebase, new concerns: global error handling (RFC 9457), Testcontainers integration tests, Micrometer + Prometheus + a Grafana dashboard for your own services, HikariCP basics, API-first OpenAPI discipline (Tier 2–3). Milestone: you can answer "is the payment service healthy right now?" from a dashboard you built.

### Stage 3 — Project #2: event-streaming rebuild (~months 7–10)
Rebuild one QuickPay slice (or build a small statement/analytics service) on **Kafka**: topics, partitions, consumer groups, exactly-once semantics, CQRS read model, caching with Redis + invalidation, rate limiting (Tier 3–4). This stage aligns with your Confluent-Kafka interest and the Gulf market's Kafka demand, and it overlaps the AWS SA Professional study window — keep engineering hours at 3/week and let certs take priority when they collide.

### Stage 4 — Architecture & leadership layer (~months 10+)
Hexagonal architecture & DDD tactical patterns applied as a refactor of your own code, system-design practice via /design exercises (one per month: URL shortener, notification fan-out, payment reconciliation at scale), ADR fluency, CI/CD pipeline for QuickPay with GitHub Actions, Kubernetes *literacy* week (requests/limits, replicas, HPA — concepts only) (Tier 5).

### Certification track (unchanged, runs in parallel)
TOGAF Practitioner (Jul–Oct 2026) → AWS SAA (Nov 2026–Mar 2027) → Gate 1: cloud commit → ISO 27001 LI (Apr–Jun 2027) → AWS SA Pro or AZ-305 (Jul 2027–Jan 2028) → Gate 2: industry pick → BIAN or TM Forum (Feb–Apr 2028) → ArchiMate (May–Jun 2028). Details and decision-gate criteria are in the earlier roadmap conversation; total ≈515 hrs, ~5 hrs/week average.

### Track 3 — Commercial & business architecture (learned at work, not from the study budget)
What the seniors actually do — **write RFPs, evaluate vendor proposals, improve business processes** — is a third skill family, and in the Gulf it is often the majority of a senior SA's output. TOGAF supplies the framework layer (Phase B business architecture: capability maps, value streams, process models; the Architecture Requirements Specification that becomes an RFP's technical core; Phase E/F work packages that become procurements; Phase G contracts and compliance levels that become vendor-evaluation criteria) — so the TOGAF Practitioner study already serves this track. The craft layer is learned by apprenticeship:
- Collect 2–3 past RFPs from the org plus the winning proposal, a losing one, and the evaluation sheet; reverse-engineer the structure and why the winner won
- Ask to draft ONE section (scope/requirements) of the next real RFP under senior review; volunteer as scribe/observer in one proposal-evaluation committee
- Learn BPMN notation (Camunda's free BPMN tutorial) well enough to model an as-is/to-be process; certify (OCEB) only if the employer values it
- **AI practice loop (ties all tracks together):** when QuickPay reaches Phase 4, turn its requirements/contracts into a mock RFP; the AI plays three competing vendors (over-engineered / cheap-and-risky / solid); learner builds a weighted evaluation matrix, scores them, and defends the recommendation to the AI playing a skeptical procurement committee
Budget: workplace initiative plus occasional buffer hours — do **not** take time from the engineering 3 hrs/week.

### AI addendum (decided Jun 2026 — KSA's "Year of AI")
AI is the Gulf's biggest capital theme, but the learner's lane is **AI solution architecture** (integrating AI into enterprise systems), NOT model-building. An LLM API is architecturally a slow, flaky, expensive external dependency — the same patterns as QuickPay's biller (timeouts, retries, circuit breakers, fallbacks, queues, data ownership). The existing roadmap is therefore ~80% of the AI preparation already. Specific additions:
- **QuickPay Stage 2–3:** one AI slice — a "statement insights"/transaction-categorization feature calling an LLM API with retrieval over transaction history. Teaches context design, streaming, cost & latency budgets, graceful degradation when the model fails or hallucinates
- **Certs:** AWS AI Practitioner as optional ~25 hr add-on straight after the SAA (heavy overlap); ISO/IEC 42001 (AI management systems) on the year-3 watch list — pairs with ISO 27001 + the SDAIA governance angle for a rare "AI + compliance" architect profile
- **Reading:** SDAIA AI Adoption Framework (mandatory baseline for KSA public sector since Nov 2025) + SDAIA AI Ethics Principles — one evening, table stakes for architecting in the Kingdom
- **Do not:** pivot to ML engineering/data science, add ML specialty certs, or let AI displace distributed-systems fundamentals — AI workloads amplify the need for them

### Milestones — how to know it's working
| Checkpoint (~quarterly) | You can honestly say… |
|---|---|
| Q1 | "I designed every schema in a running 4-service system, and I can explain why the ledger is append-only and what isolation level the transfer uses" |
| Q2 | "I ran 12 sabotage scenarios, predicted most outcomes, and can explain every surprise. I found the TPS where my system breaks and fixed it" |
| Q3 | "I can read a Kafka consumer-group rebalance in the logs and explain it. My services have dashboards I built" |
| Q4 | "In design reviews at work, I argue failure modes and capacity with developers as a peer — and sometimes win" |
| Year 1, any quarter (Track 3) | "I drafted a section of a real RFP that survived senior review, and I sat through one full proposal evaluation" |

---

## 4. Resources — deliberately short

A long resource list is procrastination wearing a uniform. This is everything; resist adding more.

| Resource | Role | When |
|---|---|---|
| **Designing Data-Intensive Applications** — Kleppmann | THE book for your specialty. Read Ch. 1–2, 5, 7 (transactions — read twice), 8–9 alongside QuickPay; rest with Stage 3 | Primary, slow read, whole journey |
| **Release It!** (2nd ed.) — Nygard | Production failure modes, stability patterns. The "scar tissue in book form" companion to the sabotage phase | Stage 1–2 |
| **microservices.io** — Richardson | Canonical pattern reference: saga, outbox, CQRS, database-per-service. Free | Look-up, every stage |
| **Spring official guides + Baeldung** | Implementation how-tos for everything Spring | Look-up, every stage |
| **Resilience4j, RabbitMQ, Testcontainers, k6 official docs** | Always prefer official docs over videos for tools | As each tool enters the project |
| **use-the-index-luke.com** — Winand | Free, the best resource on indexes and why queries are slow | Stage 1, schema phase |
| **martinfowler.com** | Architecture essays (ADRs, CQRS, event-driven nuances) | Stage 4, and whenever cited |
| Your **AI assistant + the backend-learning skill** | Lessons, reviews, quizzes, debugging mentor, sponsor role-play | Every session |

Explicitly **not** on the list: long video courses (passive), Kubernetes books (deferred), more than one "big" book at a time.

---

## 5. Protocol for working with any AI model

The `backend-learning` skill file in this Claude Project travels with the project — any model that opens the project reads it and knows the lesson/exercise/quiz/design formats. This section adds the standing rules and copy-paste prompts that make any assistant behave like the mentor you need.

### Standing rules — paste at the start of a new conversation or working session
```
You are my backend engineering mentor. I am a junior Solution Architect
(~2 years) learning production-grade engineering. Read the backend-learning
skill and
learning-playbook.md in this project, plus my learning-log.md. Rules:
1. NEVER write project code for me. Exceptions: dev scaffolding (Docker
   Compose, mock external systems, build config) and code I have already
   attempted, after I share my attempt.
2. Review my designs and code like a blunt senior engineer: name what
   breaks in production, not what is nice.
3. Enforce predict-then-verify: before I run any experiment you give me,
   demand my written prediction first.
4. One topic per session. If I drift, pull me back.
5. Quiz me weekly on PAST topics sampled from my learning-log, scenario
   questions only. Tell me honestly when an answer would fail in production.
6. Connect new concepts to what I know (Spring Boot, JWT auth, my SA work
   designing transaction flows).
7. Be honest about complexity and trade-offs. No flattery about my progress.
```

### Session prompts
**Start of a build session (A):**
```
Session A. Current phase: [e.g., QuickPay Phase 6, top-up flow].
Last session I finished: [X]. Today I will: [Y].
Before I start — any review notes on the attached [diagram/DDL/code]?
```
**Deliverable review:**
```
Review this [sequence diagram / schema DDL / OpenAPI spec / code] as a
senior engineer. Specifically check: failure modes (what happens when each
step dies or duplicates), data-ownership violations, missing idempotency,
and anything that breaks the golden rule (money is never created or lost).
Rank issues by severity. Do not fix them for me — tell me what is wrong
and ask the question that leads me to the fix.
```
**Depth session (B):**
```
/learn [topic] — anchor it to the problem I just hit in QuickPay: [describe].
Afterward I will teach it back to you; grade my explanation harshly and
ask one follow-up a skeptical senior would ask.
```
**Retain session (C):**
```
Quiz me: 4 scenario questions sampled from past topics in my learning-log
(attached), none from this week. After my answers, give production-grade
feedback. Then I'll add this week's new questions to the bank.
```
**When stuck:**
```
I'm stuck. Expected: [prediction]. Got: [actual behavior / stack trace /
log]. Here is my config/code: [...]. Explain what is actually happening
and why — like a whiteboard session, not a rewrite.
```
**Monthly retrospective:**
```
Here is my learning-log for the month. Assess: am I on the roadmap pace?
What am I avoiding? What single topic, if studied next, would most improve
my design work at my actual job? Update milestone status (section 3 of the
playbook).
```

### When stuck in implementation — the escalation ladder
Climb one rung at a time, ~30 min per rung, only after genuinely trying the current one:
1. **Explain:** "Expected X, got Y, here's my code — explain what's actually happening" (most blockages die here; the explanation is the lesson)
2. **Concept:** "What concept am I missing? Point me to the doc section" — read, retry
3. **Approach:** "Is my approach wrong? Give pseudocode or the shape of the fix, not code"
4. **Last resort:** AI fixes with explanation → learner retypes it (never pastes), explains every changed line, adds a quiz question about the bug
For design decisions that carry learning value (ledger model, sync vs async, idempotency placement): learner proposes with reasoning, AI critiques — never ask "what's best." For commodity decisions (Flyway vs Liquibase, JSON library): just ask and move on.

### Which Claude surface for what
- **Chat in this Project = the mentor.** Design reviews, sponsor role-play, /learn lessons, quizzes, stuck-ladder rungs 1–3. The Project holds the skill + these files = continuity.
- **Claude Code (JetBrains/terminal) = the constrained power tool.** Permitted scaffolding only (mocks, Docker Compose, build config) + whole-repo code review and test runs. **Guardrail:** put the standing rules into the repo's `CLAUDE.md` ("act as reviewer; never modify files outside /scaffolding; explain, don't fix") so the agent enforces the discipline too.

### Continuity rules (because AI memory is not guaranteed)
- **The files are the memory, not the chat.** Keep `learning-log.md` updated every session and stored in the project; upload it (plus this playbook) at the start of any conversation with a new model.
- After any major design decision, save the ADR as a file — don't leave it only in chat.
- If a new model contradicts this playbook's method, the playbook wins unless you consciously decide to change it — then update the playbook so the next model inherits the change.

---

## 6. One-page summary

Learn through projects, not curricula. Predict before every experiment; chase every surprise. Break your own systems deliberately — manufactured failure is the substitute for years of production exposure. Retain through weekly scenario quizzes from your own question bank (your proven TOGAF method). Prove understanding by teaching back. Log everything in files, because files — not chats, not models — are the memory of this system. One closed loop per session, ~3 hours a week, for two years, alongside the certification track. That is the whole method; everything else is detail.
