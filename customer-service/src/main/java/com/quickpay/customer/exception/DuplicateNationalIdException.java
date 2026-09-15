package com.quickpay.customer.exception;

import lombok.Getter;

@Getter
public class DuplicateNationalIdException extends RuntimeException {

    private String nationalId;

    public DuplicateNationalIdException(String nationalId) {
        super("customer with national id : "+ nationalId +" is already exists");
        this.nationalId = nationalId;
    }
}
