package com.quickpay.wallet.exception;

import com.quickpay.wallet.enums.WalletStatus;
import lombok.Getter;

@Getter
public class WalletNotActiveException extends RuntimeException {

    private final String wallet_number;
    private final WalletStatus status;

    public WalletNotActiveException(String message, String wallet_number,WalletStatus status) {
        super("wallet with number = "+wallet_number+" is "+status.toString());
        this.wallet_number = wallet_number;
        this.status = status;
    }
}
