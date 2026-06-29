package com.quickpay.wallet.dto.response;

public record TransactionResponse(
        String entryId,
        String idempotencyKey
) {
}
