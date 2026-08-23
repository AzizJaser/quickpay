ALTER TABLE ledger
    ADD transaction_type varchar(20) CHECK (transaction_type IN ('TRANSFER', 'DEPOSIT', 'HOLD', 'SETTLEMENT','RELEASE','WITHDRAWAL')),
    ADD settles_entry_id varchar(36),
ADD CONSTRAINT fk_settled_entry_id
    FOREIGN KEY (settles_entry_id)
    REFERENCES ledger(entry_id);


CREATE UNIQUE INDEX idx_reverse_settle_constrains ON ledger (coalesce(reverses_entry_id, settles_entry_id));