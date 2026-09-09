ALTER TABLE ledger
    ADD transaction_type varchar(20) CHECK (transaction_type IN ('TRANSFER', 'DEPOSIT', 'HOLD', 'SETTLEMENT','RELEASE','WITHDRAWAL')),
    ADD settles_entry_id varchar(36),
ADD CONSTRAINT fk_settled_entry_id
    FOREIGN KEY (settles_entry_id)
    REFERENCES ledger(entry_id);


CREATE UNIQUE INDEX uq_entry_discharged_once ON ledger (coalesce(reverses_entry_id, settles_entry_id));
COMMENT ON INDEX uq_entry_discharged_once IS 'This index is a constrain on columns settles_entry_id and reverses_entry_id with uniqueness on the two columns';