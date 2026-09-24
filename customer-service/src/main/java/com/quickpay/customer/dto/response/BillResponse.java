package com.quickpay.customer.dto.response;

public record BillResponse (
        String paymentId,
        String billReference,
        Long amount,
        String status
) {
}
