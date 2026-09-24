package com.quickpay.wallet.exception;

public class WalletAssertionMismatchException extends RuntimeException {
    public WalletAssertionMismatchException() {
        super("you asserted on a wallet not in this transfer");
    }
}
