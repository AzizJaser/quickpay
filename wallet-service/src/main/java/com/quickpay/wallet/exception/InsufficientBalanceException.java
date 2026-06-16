package com.quickpay.wallet.exception;

import lombok.Getter;

@Getter
public class InsufficientBalanceException extends RuntimeException {

    private final String walletNumber;

    public InsufficientBalanceException(String message, String walletNumber) {
        super("Insufficient Balance to do the transaction for wallet number"+walletNumber);
        this.walletNumber = walletNumber;
    }
}
