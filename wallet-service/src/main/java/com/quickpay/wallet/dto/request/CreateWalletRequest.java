package com.quickpay.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank
        @Pattern(regexp = "^0[0-9]{9}$")
        String cif,
        @NotBlank
        @Size(max = 50)
        String walletName
) {
}
