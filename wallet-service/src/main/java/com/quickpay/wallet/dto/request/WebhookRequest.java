package com.quickpay.wallet.dto.request;

public record WebhookRequest(
        String walletNumber,
        Long amount,
        String gatewayTxnId
) {
}
