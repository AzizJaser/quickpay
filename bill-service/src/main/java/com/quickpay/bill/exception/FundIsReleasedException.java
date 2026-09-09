package com.quickpay.bill.exception;

import lombok.Getter;

@Getter
public class FundIsReleasedException extends RuntimeException {

    private final String paymentId;
    public FundIsReleasedException(String paymentId) {
        super("Payment number " + paymentId + " is released and the customer is refunded");
        this.paymentId = paymentId;
    }
}
