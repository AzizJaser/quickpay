package com.quickpay.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank
        @Size(min = 10,max = 10)
        String cif,
        @NotBlank
        @Size(max = 50)
        String walletName
) {
}
