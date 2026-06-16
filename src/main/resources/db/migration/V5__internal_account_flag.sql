ALTER TABLE wallet ADD COLUMN is_system boolean NOT NULL DEFAULT false;

UPDATE wallet SET is_system = true WHERE wallet_number = '000000000001';


ALTER TABLE wallet DROP CONSTRAINT wallet_balance_check;
ALTER TABLE wallet ADD CONSTRAINT wallet_balance_check CHECK (balance >= 0 OR is_system);

UPDATE wallet
SET balance = COALESCE((SELECT SUM(debited_amount)  FROM ledger WHERE debited_wallet_number  = '000000000001'), 0)
    + COALESCE((SELECT SUM(credited_amount) FROM ledger WHERE credited_wallet_number = '000000000001'), 0)
WHERE wallet_number = '000000000001';
