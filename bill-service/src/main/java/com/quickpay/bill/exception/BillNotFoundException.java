package com.quickpay.bill.exception;

public class BillNotFoundException extends RuntimeException {
    public BillNotFoundException(String paymentId) {
        super("Payment request with number "+paymentId+" not found");
    }
}
