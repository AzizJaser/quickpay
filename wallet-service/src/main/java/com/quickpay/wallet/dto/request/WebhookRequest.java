package com.quickpay.wallet.dto.request;

import jakarta.validation.constraints.Size;

public record WebhookRequest(
        String walletNumber,
        Long amount,
        @Size(min = 36, max = 36)
        String gatewayTxnId
) {
}