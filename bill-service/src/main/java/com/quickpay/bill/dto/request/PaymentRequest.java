package com.quickpay.bill.dto.request;

import jakarta.validation.constraints.*;

public record PaymentRequest(
        @NotBlank
        @Pattern(regexp = "^0[0-9]{9}$")
        String cif,
        @NotBlank
        String billReference,
        @NotBlank
        String walletNumber,
        @NotNull
        @Positive
        Long amount
) {
}
