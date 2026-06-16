package com.quickpay.wallet.web;

import com.quickpay.wallet.domain.Wallet;
import com.quickpay.wallet.dto.request.CreateWalletRequest;
import com.quickpay.wallet.dto.request.TransferRequest;
import com.quickpay.wallet.dto.response.WalletResponse;
import com.quickpay.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/wallets")
@AllArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{wallet_number}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String wallet_number){
        Wallet wallet = walletService.fetchWallet(wallet_number);

        return ResponseEntity.ok(fromWallet(wallet));
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@RequestBody @Valid CreateWalletRequest walletRequest){
        Wallet wallet = walletService.createWallet(walletRequest.cif(),walletRequest.walletName());
        return ResponseEntity.status(HttpStatus.CREATED).body(fromWallet(wallet));
    }

    @PutMapping("/activate/{wallet_number}")
    public ResponseEntity<WalletResponse> activateWallet(@PathVariable String wallet_number){
        Wallet wallet = walletService.activateWallet(wallet_number);
        return ResponseEntity.status(HttpStatus.OK).body(fromWallet(wallet));
    }

    @PutMapping("/suspend/{wallet_number}")
    public ResponseEntity<WalletResponse> suspendWallet(@PathVariable String wallet_number){
        Wallet wallet = walletService.suspendWallet(wallet_number);
        return ResponseEntity.status(HttpStatus.OK).body(fromWallet(wallet));
    }

    @DeleteMapping("/close/{wallet_number}")
    public ResponseEntity<WalletResponse> closeWallet(@PathVariable String wallet_number){
        Wallet wallet = walletService.closeWallet(wallet_number);
        return ResponseEntity.ok(fromWallet(wallet));
    }



    private WalletResponse fromWallet(Wallet wallet){
        return new WalletResponse(wallet.getWallet_number(),wallet.getBalance(),wallet.getWallet_name(),wallet.getStatus());
    }
}
