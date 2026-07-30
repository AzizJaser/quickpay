package com.quickpay.bill.dto.request;

public record BillerPayRequest(
        String billNumber,
        Long amount,
        String reference
) {
}
