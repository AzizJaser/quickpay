package com.quickpay.bill.dto.request;

public record TransferRequest(
        String debitedWalletNumber,
        String creditedWalletNumber,
        Long amount
) {
}
