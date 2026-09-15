package com.quickpay.customer.dto.event;

public record CustomerRegistrationEvent(
        String cif,
        String customerName,
        String phoneNumber,
        String email
) {
}
