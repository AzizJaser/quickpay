UPDATE ledger
SET transaction_type = CASE
    WHEN debited_wallet_number = '000000000001' THEN 'DEPOSIT'
    WHEN credited_wallet_number = '000000000002' THEN 'WITHDRAWAL'
    WHEN credited_wallet_number = '000000000003' THEN 'HOLD'
    WHEN debited_wallet_number = '000000000003' AND credited_wallet_number = '000000000004' THEN 'SETTLEMENT'
    WHEN debited_wallet_number = '000000000003' THEN 'RELEASE'
    WHEN NOT EXISTS (
        select 4
        from wallet w
        where w.is_internal and (w.wallet_number = ledger.debited_wallet_number
                                     OR w.wallet_number = ledger.credited_wallet_number)
    ) THEN 'TRANSFER'
    END
WHERE transaction_type IS NULL;


UPDATE ledger SET transaction_type = 'RELEASE'
WHERE transaction_type = 'REVERSAL' AND debited_wallet_number = '000000000003';