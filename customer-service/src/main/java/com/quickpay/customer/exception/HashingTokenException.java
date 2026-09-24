package com.quickpay.customer.exception;

public class HashingTokenException extends RuntimeException {
    public HashingTokenException() {
        super("Error while generating hashed token");
    }
}
