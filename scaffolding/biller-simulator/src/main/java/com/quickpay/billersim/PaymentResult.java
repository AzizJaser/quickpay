package com.quickpay.billersim;

/**
 * The biller's answer for a payment / inquiry.
 * status is one of: PAID, FAILED, NOT_FOUND. billerTxnId is set only when PAID.
 */
public record PaymentResult(String reference,
                            String billNumber,
                            String status,
                            String billerTxnId,
                            String message) {
}