package com.quickpay.customer.dto.response;

public record CustomerResponse (
        String cif,
        String customerName,
        String status
) {
}
