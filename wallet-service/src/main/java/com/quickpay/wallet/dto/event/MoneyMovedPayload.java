package com.quickpay.wallet.dto.event;

import java.time.LocalDateTime;

public record MoneyMovedPayload(
        String entryId,
        String cif,
        String walletNumber,
        String counterpartyWalletNumber,
        Long amount,
        LocalDateTime occurredAt
) {
}
