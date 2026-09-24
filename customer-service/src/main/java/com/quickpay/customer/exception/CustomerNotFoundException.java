package com.quickpay.customer.exception;

import lombok.Getter;

@Getter
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("customer with was not found!");
    }
}
