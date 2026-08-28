ALTER TABLE ledger
    ADD CONSTRAINT ck_ledger_transaction_type_not_null CHECK ( transaction_type IS NOT NULL ) NOT VALID;