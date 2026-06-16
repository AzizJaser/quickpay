package com.quickpay.wallet.exception;

public class NumberOfWalletsExceededException extends RuntimeException {
    public NumberOfWalletsExceededException(String message) {
        super("you have exceed the number of wallets per customer.");
    }
}
