package com.quickpay.wallet.exception;

import lombok.Getter;

@Getter
public class DuplicatedEntryException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicatedEntryException(String message,String idempotencyKey) {
        super("Duplicated Entry with key = " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }
}
