package com.quickpay.wallet.enums;

public enum TransactionType {
    TRANSFER,
    DEPOSIT,
    HOLD,
    SETTLEMENT,
    RELEASE,
    WITHDRAWAL,
    REVERSAL;

    public boolean isCustomerFacing(){
        return switch (this) {
            case HOLD, RELEASE, SETTLEMENT -> false;
            case DEPOSIT, TRANSFER, WITHDRAWAL, REVERSAL -> true;
        };
    }
}
