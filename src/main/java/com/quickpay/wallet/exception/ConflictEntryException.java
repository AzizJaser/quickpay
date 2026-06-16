package com.quickpay.wallet.exception;

public class ConflictEntryException extends RuntimeException {


    public ConflictEntryException(String message) {
        super("debited wallet is the credited wallet, please make sure debited wallet is different than credited wallet.");
    }
}
