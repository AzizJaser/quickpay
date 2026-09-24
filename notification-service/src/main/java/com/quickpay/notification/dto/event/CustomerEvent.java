package com.quickpay.notification.dto.event;

public record CustomerEvent(
        String cif,
        String customerName,
        String phoneNumber,
        String email
) {
}
