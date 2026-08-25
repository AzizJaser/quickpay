package com.quickpay.wallet.exception;

import lombok.Getter;

@Getter
public class UnHoldTransactionException extends RuntimeException {

    private final String entryId;

    public UnHoldTransactionException(String entryId) {
        super("The transaction with id "+entryId+" can't be settled ...");
        this.entryId = entryId;
    }
}
