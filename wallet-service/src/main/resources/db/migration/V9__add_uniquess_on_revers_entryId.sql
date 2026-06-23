ALTER TABLE ledger
     ADD CONSTRAINT reverses_entry_id_unique UNIQUE (reverses_entry_id);