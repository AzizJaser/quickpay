package com.quickpay.wallet.web;

import com.quickpay.wallet.domain.LedgerEntry;
import com.quickpay.wallet.dto.request.ReversRequest;
import com.quickpay.wallet.dto.request.TopUpRequest;
import com.quickpay.wallet.dto.request.TransferRequest;
import com.quickpay.wallet.dto.response.TransactionResponse;
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
    public ResponseEntity<TransactionResponse> transferBetweenWallets(@RequestBody @Valid TransferRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.transfer(request.debitedWalletNumber(), request.creditedWalletNumber(), request.amount(), idempotencyKey,null);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/top-up")
    public ResponseEntity<TransactionResponse> topUp(@RequestBody @Valid TopUpRequest request,  @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.topUp(request.wallet_number(), request.amount(),idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@RequestBody @Valid TopUpRequest request,  @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry =  walletService.withdraw(request.wallet_number(), request.amount(),idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/revers")
    public ResponseEntity<TransactionResponse> revers(@RequestBody @Valid ReversRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.revers(request.original_entry_id(),idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }
}
