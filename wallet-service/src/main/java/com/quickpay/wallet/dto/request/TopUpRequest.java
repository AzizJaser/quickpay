package com.quickpay.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TopUpRequest(
        @NotBlank
        String walletNumber,
        @NotNull
        @Positive
        Long amount
) {
}
