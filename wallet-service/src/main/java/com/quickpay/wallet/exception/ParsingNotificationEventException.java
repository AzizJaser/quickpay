package com.quickpay.wallet.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;

@Getter
public class ParsingNotificationEventException extends RuntimeException {

    private final String entryId;

    public ParsingNotificationEventException(String entryId) {
        super("Can not serialise a notification event for entry ID: "+entryId);
        this.entryId = entryId;
    }
}
