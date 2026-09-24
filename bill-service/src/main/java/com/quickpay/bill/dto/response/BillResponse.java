package com.quickpay.bill.dto.response;

import com.quickpay.bill.enums.BillStatus;

public record BillResponse(
        String paymentId,
        String billReference,
        Long amount,
        BillStatus status
) {
}
