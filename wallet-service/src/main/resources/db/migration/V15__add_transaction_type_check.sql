ALTER TABLE ledger
    DROP CONSTRAINT ledger_transaction_type_check;


ALTER TABLE  ledger
    ADD CONSTRAINT ck_ledger_transaction_type CHECK (transaction_type IN ('TRANSFER', 'DEPOSIT', 'HOLD', 'SETTLEMENT','RELEASE','WITHDRAWAL','REVERSAL'));