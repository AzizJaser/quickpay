package com.quickpay.wallet.exception;

import lombok.Getter;

@Getter
public class WalletCifOwnershipConflictException extends RuntimeException {

    private String cif;
    private String walletNumber;
    public WalletCifOwnershipConflictException(String cif,String walletNumber) {
        super("Wallet number: "+walletNumber+" doesn't belong to cif number : "+cif);
        this.cif = cif;
        this.walletNumber = walletNumber;
    }
}
