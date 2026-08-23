package com.quickpay.notification.dto.request;

public record SMSNotification(
        String phoneNumber,
        String message,
        String reference
) {
}
