package com.quickpay.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record BillPaymentRequest (
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
