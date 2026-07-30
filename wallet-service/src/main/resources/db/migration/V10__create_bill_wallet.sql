INSERT INTO wallet (wallet_number, cif, wallet_name, balance, status,is_internal,allows_negative)
VALUES ('000000000004', '0000000000', 'Bill Account', 0, 'Active',true,false)
    ON CONFLICT (wallet_number) DO NOTHING;
