package com.quickpay.customer.exception;

import lombok.Getter;

@Getter
public class ParsingCustomerEventException extends RuntimeException {

    private String cif;

    public ParsingCustomerEventException(String cif) {
        super("Error parsing customer registration event with cif: "+cif);
        this.cif = cif;
    }
}
