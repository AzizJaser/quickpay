package com.quickpay.notification.exception;

import lombok.Getter;

@Getter
public class CustomerNotFoundException extends RuntimeException {

    private String cif;

    public CustomerNotFoundException(String cif) {
        super("Customer with CIF = "+cif+" is not found");
        this.cif = cif;
    }
}
