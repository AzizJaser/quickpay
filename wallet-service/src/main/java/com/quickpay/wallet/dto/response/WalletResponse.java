package com.quickpay.wallet.dto.response;

import com.quickpay.wallet.domain.Wallet;
import com.quickpay.wallet.enums.WalletStatus;

public record WalletResponse(
        String walletNumber,
        Long balance,
        String walletName,
        WalletStatus status
) {

    public WalletResponse fromWallet(Wallet wallet){
        return new WalletResponse(wallet.getWallet_number(),wallet.getBalance(),wallet.getWallet_name(),wallet.getStatus());
    }
}
