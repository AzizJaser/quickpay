INSERT INTO wallet (wallet_number, cif, wallet_name, balance, status, is_system)
VALUES ('000000000002', '0000000000', 'Outward Transfer', 0, 'Active',true)
    ON CONFLICT (wallet_number) DO NOTHING;