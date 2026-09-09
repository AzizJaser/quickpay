package com.quickpay.wallet.exception;

import com.quickpay.wallet.enums.TransactionType;
import lombok.Getter;

@Getter
public class HoldAlreadyDischargedException extends RuntimeException {

    private final String holdEntryId;
    private final String dischargedByEntryId;

    private final TransactionType dischargerType;
    public HoldAlreadyDischargedException(String holdEntryId, String dischargedByEntryId, TransactionType dischargerType) {
        super("hold "+ holdEntryId +" was already discharged by Id of" + dischargedByEntryId + "and type is " + dischargerType);
        this.dischargedByEntryId = dischargedByEntryId;
        this.dischargerType = dischargerType;
        this.holdEntryId = holdEntryId;
    }
}
