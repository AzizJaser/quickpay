package com.quickpay.wallet.exception;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) {
        super("invalid amount entered!");
    }
}
