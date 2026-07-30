package com.quickpay.bill.exception;

public class ReserveDeclinedException extends RuntimeException {
    public ReserveDeclinedException(String message) {
        super(message);
    }
}
