package com.quickpay.customer.exception;

public class BillUnavailableException extends RuntimeException {
    public BillUnavailableException(Throwable cause) {
        super("bill is not available",cause);
    }

    public BillUnavailableException(){
        super("bill is not available");
    }
}
