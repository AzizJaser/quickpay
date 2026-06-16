package com.quickpay.wallet.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
public class WalletNotFoundException extends RuntimeException {

    private final String walletNumber;

    public WalletNotFoundException(String message,String walletNumber) {
        super("Wallet not found with wallet number = " + walletNumber);
        this.walletNumber = walletNumber;
    }
}
