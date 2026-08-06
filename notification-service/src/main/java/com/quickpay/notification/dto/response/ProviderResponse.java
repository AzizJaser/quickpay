package com.quickpay.notification.dto.response;

import com.quickpay.notification.enums.NotificationStatus;

public record ProviderResponse(
        String reference,
        String destination,
        NotificationStatus status,
        String providerRef,
        String message
) {
}
