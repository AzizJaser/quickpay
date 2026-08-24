ALTER TABLE ledger
    ADD CONSTRAINT ck_one_discharge_kind CHECK ( reverses_entry_id IS NULL OR settles_entry_id IS NULL );