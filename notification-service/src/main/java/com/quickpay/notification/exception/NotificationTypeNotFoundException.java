package com.quickpay.notification.exception;

import lombok.Getter;

@Getter
public class NotificationTypeNotFoundException extends RuntimeException {

    private String key;
    public NotificationTypeNotFoundException(String key) {
        super("Notification of type "+key+" is not found");
        this.key = key;
    }
}
