package com.quickpay.customer.exception;

public class SessionIsNotFoundOrExpiredException extends RuntimeException {
    public SessionIsNotFoundOrExpiredException() {
        super("session is not found or expired");
    }
}
