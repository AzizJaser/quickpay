package com.quickpay.customer.exception;

public class CustomerClosedException extends RuntimeException {
    public CustomerClosedException() {
        super("Customer status is closed");
    }
}
