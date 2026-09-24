package com.quickpay.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OutwardTransactionRequest (
        @NotBlank
        String walletNumber,
        @NotNull
        @Positive
        Long amount
){
}
