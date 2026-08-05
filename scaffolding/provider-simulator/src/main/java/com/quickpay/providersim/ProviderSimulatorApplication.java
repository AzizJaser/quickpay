package com.quickpay.providersim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mock SMS/email provider for QuickPay's notification service.
 *
 * Requirement 5 says the notification channel "fails regularly — that must never block or
 * fail a payment". This simulator is what makes that real: it fails on demand, so the
 * notification service's retry columns (sms_status, attempts, last_attempt_at) and its
 * retry job have something genuine to react to.
 */
@SpringBootApplication
public class ProviderSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProviderSimulatorApplication.class, args);
    }
}
