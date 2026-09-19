package com.quickpay.customer.exception;


public class WalletAccessDeniedException extends RuntimeException {

    public WalletAccessDeniedException() {
        super("access denied to this wallet");
    }
}
