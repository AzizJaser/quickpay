package com.quickpay.customer.dto.response;

public record TransferResponse (
        String entryId,
        String idempotencyKey
) {
}
