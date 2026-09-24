package com.quickpay.bill.exception;

import lombok.Getter;

@Getter
public class CifMismatchException extends RuntimeException {

    private String cif;
    private String walletNumber;
    public CifMismatchException(String cif,String walletNumber) {
        super("Wallet Number "+walletNumber+" doesn't belong to cif number "+cif);
        this.cif = cif;
        this.walletNumber = walletNumber;
    }
}
