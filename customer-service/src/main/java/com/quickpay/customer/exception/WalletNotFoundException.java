package com.quickpay.customer.exception;

import lombok.Getter;

@Getter
public class WalletNotFoundException extends RuntimeException {
    private final String walletNumber;
    public WalletNotFoundException(String walletNumber) {
        super("wallet is not found");
        this.walletNumber = walletNumber;
    }
}
