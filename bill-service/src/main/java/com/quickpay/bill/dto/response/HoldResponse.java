package com.quickpay.bill.dto.response;

public record HoldResponse(
        String entryId,
        String idempotencyKey,
        String cif
) {
}
