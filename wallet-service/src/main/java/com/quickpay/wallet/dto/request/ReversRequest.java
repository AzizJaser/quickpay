package com.quickpay.wallet.dto.request;

public record ReversRequest(
        String originalEntryId,
        String idempotencyKey
) {
}
