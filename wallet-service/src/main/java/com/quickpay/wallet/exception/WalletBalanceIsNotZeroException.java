package com.quickpay.wallet.exception;

import lombok.Getter;

@Getter
public class WalletBalanceIsNotZeroException extends RuntimeException {

    private final String walletNumber;
    public WalletBalanceIsNotZeroException(String walletNumber) {
        super("Wallet cannot be close because balance is not zero");
        this.walletNumber = walletNumber;
    }
}
