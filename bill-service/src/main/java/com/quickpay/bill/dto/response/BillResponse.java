package com.quickpay.bill.dto.response;

import com.quickpay.bill.enums.BillStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BillResponse(
        String paymentId,
        String billReference,
        Long amount,
        BillStatus status
) {
}
