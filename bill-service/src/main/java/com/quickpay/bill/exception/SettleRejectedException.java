package com.quickpay.bill.exception;

import lombok.Getter;

@Getter
public class SettleRejectedException extends RuntimeException {

    private final String paymentId;
    public SettleRejectedException(String paymentId) {
        super("Settlement for Payment number " + paymentId + " is rejected");
        this.paymentId = paymentId;
    }
}
