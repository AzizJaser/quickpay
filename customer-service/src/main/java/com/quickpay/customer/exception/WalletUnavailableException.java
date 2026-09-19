package com.quickpay.customer.exception;


public class WalletUnavailableException extends RuntimeException {


    public WalletUnavailableException( Throwable cause) {
        super("Wallet is not available",cause);
    }

    public WalletUnavailableException(){
        super("Wallet is not available");
    }

}
