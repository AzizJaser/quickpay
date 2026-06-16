ALTER TABLE wallet
    RENAME COLUMN walletNumber TO wallet_number;

ALTER TABLE wallet
    RENAME COLUMN walletName TO wallet_name;

ALTER TABLE wallet
    RENAME COLUMN createdAt TO created_at;

ALTER TABLE wallet
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text,
    ALTER COLUMN status SET DEFAULT 'Pending',
    ADD CONSTRAINT check_wallet_status CHECK (status IN ('Pending', 'Active', 'Suspended', 'Closed'));


ALTER TABLE ledger
    RENAME COLUMN entryId TO entry_id;

ALTER TABLE ledger
    RENAME COLUMN debitedWalletNumber TO debited_wallet_number;

ALTER TABLE ledger
    RENAME COLUMN creditedWalletNumber TO credited_wallet_number;

ALTER TABLE ledger
    RENAME COLUMN debitedAmount TO debited_amount;

ALTER TABLE ledger
    RENAME COLUMN creditedAmount TO credited_amount;

ALTER TABLE ledger
    RENAME COLUMN CreatedAt TO created_at;