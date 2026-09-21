package com.quickpay.customer.exception;

import lombok.Getter;

@Getter
public class SuspendedWalletException extends RuntimeException {

    private final String cif;
    private final String walletNumber;

    public SuspendedWalletException(String cif,String walletNumber) {
        super("Error while closing you account, please contact support");
        this.cif = cif;
        this.walletNumber = walletNumber;
    }
}
