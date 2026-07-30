package com.quickpay.bill.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
        @NotBlank
        String billReference,
        @NotBlank
        String walletNumber,
        @NotNull
        @Positive
        Long amount
) {
}
