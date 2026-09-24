package com.quickpay.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record TransferRequest (
        @NotBlank
        @Pattern(regexp = "^0[0-9]{9}$")
        String cif,
    @NotBlank
    String debitedWalletNumber,
    @NotBlank
    String creditedWalletNumber,
    @NotNull
    @Positive
    Long amount
) {
}
