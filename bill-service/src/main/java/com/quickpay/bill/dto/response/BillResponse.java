package com.quickpay.bill.dto.response;

import com.quickpay.bill.enums.BillStatus;

public record BillResponse(
        java.lang.String paymentId,
        java.lang.String billReference,
        Long amount,
        BillStatus status
) {
}
