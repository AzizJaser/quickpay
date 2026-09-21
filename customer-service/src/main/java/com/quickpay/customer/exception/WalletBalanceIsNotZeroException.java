package com.quickpay.customer.exception;

import lombok.Getter;

@Getter
public class WalletBalanceIsNotZeroException extends RuntimeException {

    private final String walletNumber;
    public WalletBalanceIsNotZeroException(String walletNumber) {
        super("wallet with number "+walletNumber+" can not be close because balance is not zero");
        this.walletNumber = walletNumber;
    }
}
