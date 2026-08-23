package com.quickpay.notification.dto.request;

public record EmailNotification(
        String email,
        String message,
        String reference
) {
}
