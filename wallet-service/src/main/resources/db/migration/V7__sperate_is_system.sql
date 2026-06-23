ALTER TABLE wallet ADD COLUMN is_internal boolean NOT NULL DEFAULT false;
ALTER TABLE wallet ADD COLUMN allows_negative boolean NOT NULL DEFAULT false;

-- inward (toUp) account is internal and can go negative
UPDATE wallet SET is_internal = true WHERE wallet_number = '000000000001';
UPDATE wallet SET allows_negative = true WHERE wallet_number = '000000000001';

-- outward account is internal and can NOT go negative
UPDATE wallet SET is_internal = true WHERE wallet_number = '000000000002';
UPDATE wallet SET allows_negative = false WHERE wallet_number = '000000000002';


-- dropping the old check and create the new one
ALTER TABLE wallet DROP CONSTRAINT wallet_balance_check;
ALTER TABLE wallet DROP COLUMN is_system;
ALTER TABLE wallet ADD CONSTRAINT wallet_balance_check CHECK (balance >=0 OR allows_negative);

UPDATE wallet
SET balance = COALESCE((SELECT SUM(debited_amount)  FROM ledger WHERE debited_wallet_number  = '000000000001'), 0)
    + COALESCE((SELECT SUM(credited_amount) FROM ledger WHERE credited_wallet_number = '000000000001'), 0)
WHERE wallet_number = '000000000001';

INSERT INTO wallet (wallet_number, cif, wallet_name, balance, status,is_internal,allows_negative)
VALUES ('000000000003', '0000000000', 'suspend account', 0, 'Active',true,false)
    ON CONFLICT (wallet_number) DO NOTHING;


