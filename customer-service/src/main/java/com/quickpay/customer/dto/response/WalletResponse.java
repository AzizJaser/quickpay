package com.quickpay.customer.dto.response;

public record WalletResponse(
        String walletNumber,
        Long balance,
        String walletName,
        String status
) {
}
