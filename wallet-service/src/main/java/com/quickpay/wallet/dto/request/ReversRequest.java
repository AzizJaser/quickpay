package com.quickpay.wallet.dto.request;

public record ReversRequest(
        String original_entry_id,
        String idempotencyKey
) {
}
