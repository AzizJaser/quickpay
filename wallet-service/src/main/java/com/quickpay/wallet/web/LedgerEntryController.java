package com.quickpay.wallet.web;

import com.quickpay.wallet.dto.request.TopUpRequest;
import com.quickpay.wallet.dto.request.TransferRequest;
import com.quickpay.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@AllArgsConstructor
@RestController
@RequestMapping("/v1/transfer")
public class LedgerEntryController {

    private final WalletService walletService;


    @PostMapping("/betweenWallets")
    public ResponseEntity<Void> transferBetweenWallets(@RequestBody @Valid TransferRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        walletService.transfer(request.debitedWalletNumber(), request.creditedWalletNumber(), request.amount(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/top-up")
    public ResponseEntity<Void> topUp(@RequestBody @Valid TopUpRequest request,  @RequestHeader("Idempotency-Key") String idempotencyKey){
        walletService.topUp(request.wallet_number(), request.amount(),idempotencyKey);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@RequestBody @Valid TopUpRequest request,  @RequestHeader("Idempotency-Key") String idempotencyKey){
        walletService.withdraw(request.wallet_number(), request.amount(),idempotencyKey);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
