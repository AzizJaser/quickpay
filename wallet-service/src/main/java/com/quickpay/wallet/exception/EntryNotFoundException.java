package com.quickpay.wallet.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class EntryNotFoundException extends RuntimeException {

    private final String entryId;
    public EntryNotFoundException(String entryId) {
        super("Entry with id "+entryId+" is not found.");
        this.entryId = entryId;
    }
}
