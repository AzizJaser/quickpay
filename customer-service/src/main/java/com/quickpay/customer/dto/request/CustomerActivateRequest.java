package com.quickpay.customer.dto.request;

public record CustomerActivateRequest (
        String cif,
        String password
) {
}
