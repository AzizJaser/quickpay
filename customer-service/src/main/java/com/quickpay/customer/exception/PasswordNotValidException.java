package com.quickpay.customer.exception;

public class PasswordNotValidException extends RuntimeException {
    public PasswordNotValidException() {
        super("Password is not correct");
    }
}
