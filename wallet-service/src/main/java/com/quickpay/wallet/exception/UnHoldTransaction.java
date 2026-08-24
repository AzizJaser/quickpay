package com.quickpay.wallet.exception;

import lombok.Getter;

@Getter
public class UnHoldTransaction extends RuntimeException {

    private final String entryId;

    public UnHoldTransaction(String entryId) {
        super("The transaction with id "+entryId+" can't be settled ...");
        this.entryId = entryId;
    }
}
