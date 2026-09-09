package com.quickpay.wallet.dto.response;

public record HoldResponse(
        String entryId,
        String idempotencyKey,
        String cif
) {
}
