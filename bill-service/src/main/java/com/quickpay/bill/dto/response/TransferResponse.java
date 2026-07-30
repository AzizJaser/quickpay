package com.quickpay.bill.dto.response;

public record TransferResponse(
        String entryId,
        String idempotencyKey
) {
}
