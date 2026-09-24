package com.quickpay.customer.dto.event;

public record CustomerClosureEvent(
        String cif,
        String customerName,
        String phoneNumber,
        String email
) {
}
