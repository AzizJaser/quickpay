package com.quickpay.bill.dto.response;


import com.quickpay.bill.enums.BillerStatus;

public record BillerResult(
        String billNumber,
        BillerStatus status,
        String reference,
        String billerTxnId
) {
}
