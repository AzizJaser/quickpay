package com.quickpay.customer.exception;

import lombok.Getter;

@Getter
public class CustomerIsNotActiveException extends RuntimeException {

    private String email;

    public CustomerIsNotActiveException(String email) {
        super("Customer with email "+ email +" is not active");
    }
}
