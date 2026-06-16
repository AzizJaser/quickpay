package com.quickpay.wallet.exception;

public class WalletNumberAllocationException extends RuntimeException {

    public WalletNumberAllocationException(String message) {
        super("We couldn't create a wallet for you, please check with client support");
    }
}
