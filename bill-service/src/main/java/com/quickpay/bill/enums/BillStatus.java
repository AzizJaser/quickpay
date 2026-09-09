package com.quickpay.bill.enums;

public enum BillStatus {

    Pending,
    Reserved,
    Rejected,
    Paid,
    Failed;

    public boolean requireNotification(){
        return switch (this){
            case Paid,Rejected -> true;
            case Failed,Reserved,Pending -> false;
        };
    }
}
