package com.quickpay.customer.exception;

public class MissingSessionException extends RuntimeException {
    public MissingSessionException() {
        super("request is missing session header (authorization)");
    }
}
