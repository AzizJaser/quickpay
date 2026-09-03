package com.quickpay.notification.enums;

import com.quickpay.notification.exception.NotificationTypeNotFoundException;
import lombok.Getter;

@Getter
public enum NotificationMessage {
    MONEY_SENT("wallet.money.sent","your transaction has been sent!"),
    MONEY_RECEIVED("wallet.money.received","you received a transaction!"),
    BILL_PAID("bill.payment.paid","your bill has been paid!"),
    BILL_REJECTED("bill.payment.rejected","your bill has been rejected!");

    private String routingKey;
    private String message;

    NotificationMessage(String routingKey,String message){
        this.routingKey = routingKey;
        this.message = message;
    }

    public static NotificationMessage fromRoutingKey(String routingKey){
        for(NotificationMessage message : values()){
            if(message.routingKey.equals(routingKey)){
                return message;
            }
        }
        throw new NotificationTypeNotFoundException(routingKey);
    }

}
