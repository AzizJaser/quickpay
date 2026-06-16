# QuickPay Wallet Service — structure & what YOU write

I (the AI) scaffolded the build, config, and folders. **Every `.java` file below is
yours to write** — that's the whole point of this project. This file is the map.

```
wallet-service/
├── pom.xml                     ✅ done  (build config)
├── docker-compose.yml          ✅ done  (local Postgres)
├── src/main/resources/
│   ├── application.yml          ✅ done  (DB + JPA + Flyway config)
│   └── db/migration/            ⬜ YOU   (Flyway SQL — see below)
└── src/main/java/com/quickpay/wallet/
    ├── QuickpayWalletApplication.java   ⬜ YOU  (the @SpringBootApplication main class)
    ├── domain/        ⬜ YOU  — JPA entities: Wallet, LedgerEntry (+ EntryType enum)
    ├── repository/    ⬜ YOU  — Spring Data repositories (the FOR UPDATE lock lives here)
    ├── service/       ⬜ YOU  — WalletService: credit/debit, @Transactional, the invariant
    ├── web/           ⬜ YOU  — REST controllers
    ├── dto/           ⬜ YOU  — request/response records (never expose entities directly)
    ├── config/        ⬜ YOU  — any @Configuration you need
    └── exception/     ⬜ YOU  — domain exceptions + @ControllerAdvice handler
```

## Suggested order to write things (each is a reviewable rep)

1. **`db/migration/V1__create_wallet_and_ledger.sql`** — the schema first.
   Must encode the decisions we made:
   - `wallet`: id, owner, currency, `balance`, version/created_at
     — and a **`CHECK (balance >= 0)`** constraint (overdraft tripwire).
   - `ledger_entry`: id, wallet_id (FK), amount, type, `idempotency_key`,
     created_at — with a **`UNIQUE` constraint on idempotency_key**.
   - Index `ledger_entry(wallet_id)` so summing a wallet's history is cheap.

2. **`QuickpayWalletApplication.java`** — 5-line Spring Boot main class.

3. **`domain/Wallet.java` + `domain/LedgerEntry.java`** — JPA entities that match V1.
   - Money type: **NOT `double`/`float`.** Decide the right type and tell me why.
   - `@Version` if you want optimistic locking too.

4. **`repository/WalletRepository.java`** — note the method that locks the row
   (`@Lock(LockModeType.PESSIMISTIC_WRITE)` + a `findById`-style query).

5. **`service/WalletService.java`** — the heart. `@Transactional` credit/debit:
   lock wallet → check balance → insert entry → update balance → commit.
   This is where the golden rule is enforced. Paste it for review when drafted.

6. **`web` + `dto` + `exception`** — wire it to HTTP last.

## Run it
```
docker compose up -d          # start Postgres
./mvnw spring-boot:run        # (generate the wrapper, or use your local mvn)
```

> Reminder from CLAUDE.md: paste your drafts here and I review them as a blunt
> senior — failure modes, idempotency, transaction boundaries, money conservation.
> I only write real code if you explicitly invoke **"rung 4"**.