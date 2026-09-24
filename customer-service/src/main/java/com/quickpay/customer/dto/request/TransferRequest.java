package com.quickpay.customer.dto.request;

public record TransferRequest (
        String debitedWalletNumber,
        String creditedWalletNumber,
        Long amount
) {
}
