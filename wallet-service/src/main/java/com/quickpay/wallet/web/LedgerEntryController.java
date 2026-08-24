package com.quickpay.wallet.web;

import com.quickpay.wallet.domain.LedgerEntry;
import com.quickpay.wallet.dto.request.*;
import com.quickpay.wallet.dto.response.TransactionResponse;
import com.quickpay.wallet.enums.TransactionType;
import com.quickpay.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/v1/transfer")
public class LedgerEntryController {

    private final WalletService walletService;


    @PostMapping("/betweenWallets")
    public ResponseEntity<TransactionResponse> transferBetweenWallets(@RequestBody @Valid TransferRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.transfer(request.debitedWalletNumber(), request.creditedWalletNumber(), request.amount(), idempotencyKey,null,null, TransactionType.TRANSFER);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/top-up")
    public ResponseEntity<TransactionResponse> topUp(@RequestBody @Valid TopUpRequest request,  @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.topUp(request.walletNumber(), request.amount(),idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@RequestBody @Valid TopUpRequest request,  @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry =  walletService.withdraw(request.walletNumber(), request.amount(),idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/revers")
    public ResponseEntity<TransactionResponse> revers(@RequestBody @Valid ReversRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.revers(request.originalEntryId(),idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/hold")
    public ResponseEntity<TransactionResponse> hold(@RequestBody @Valid HoldRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.hold(request.walletNumber(), request.amount(), idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }

    @PostMapping("/settle")
    public ResponseEntity<TransactionResponse> settle(@RequestBody @Valid SettleRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        LedgerEntry entry = walletService.settle(request.entryId(), idempotencyKey);
        return ResponseEntity.ok(new TransactionResponse(entry.getEntryId(),entry.getIdempotencyKey()));
    }
}
