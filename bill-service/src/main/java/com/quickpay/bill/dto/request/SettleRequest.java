package com.quickpay.bill.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SettleRequest(
        @NotBlank
        String entryId
) {
}
