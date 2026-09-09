package com.quickpay.bill.exception;

import lombok.Getter;

@Getter
public class ParsingNotificationEventException extends RuntimeException {

    private final String paymentId;

    public ParsingNotificationEventException(String paymentId) {
        super("Can not serialise a notification event for payment ID: "+ paymentId);
        this.paymentId = paymentId;
    }
}
