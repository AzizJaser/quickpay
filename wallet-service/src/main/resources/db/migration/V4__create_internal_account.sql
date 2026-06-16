INSERT INTO wallet (wallet_number, cif, wallet_name, balance, status)
VALUES ('000000000001', '0000000000', 'Internal TopUp', 1000000000, 'Active')
ON CONFLICT (wallet_number) DO NOTHING;
