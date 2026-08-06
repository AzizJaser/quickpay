package com.quickpay.notification.dto.event;

import java.time.LocalDateTime;

public record NotificationEvent (
        String entryId,
        String cif,
        String walletNumber,
        String counterpartyWalletNumber,
        Long amount,
        LocalDateTime occurredAt
) {
}

