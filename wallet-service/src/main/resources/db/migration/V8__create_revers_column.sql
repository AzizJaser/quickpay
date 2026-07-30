ALTER TABLE ledger
    ADD reverses_entry_id varchar(36),
ADD CONSTRAINT fk_reversed_entry_id
    FOREIGN KEY (reverses_entry_id)
    REFERENCES ledger(entry_id);