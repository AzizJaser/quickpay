package com.quickpay.bill.dto.event;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentEvent(
        String paymentId,
        String cif,
        String billReference,
        Long amount,
        String walletNumber,
        LocalDateTime occurredAt
) {
}
