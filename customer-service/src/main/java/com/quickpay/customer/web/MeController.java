package com.quickpay.customer.web;

import com.quickpay.customer.client.BillClient;
import com.quickpay.customer.client.WalletClient;
import com.quickpay.customer.dto.request.BillPaymentRequest;
import com.quickpay.customer.dto.request.PaymentRequest;
import com.quickpay.customer.dto.response.BillResponse;
import com.quickpay.customer.dto.response.WalletResponse;
import com.quickpay.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
public class MeController { // Me is the use / customer, all the request here will attribute "cif" and valid session

    private final WalletClient walletClient;

    private final CustomerService customerService;

    private final BillClient billClient;


    @GetMapping("/wallets")
    public ResponseEntity<List<WalletResponse>> myWallets(@RequestAttribute("cif") String cif){
        return new ResponseEntity<>(walletClient.getWalletsByCif(cif), HttpStatus.OK);
    }

    @DeleteMapping("/close")
    public ResponseEntity<Void> closeAccount(@RequestAttribute("cif") String cif){
        customerService.closeAccount(cif);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/pay")
    public ResponseEntity<BillResponse> billPayment(@RequestAttribute("cif") String cif, @Valid @RequestBody PaymentRequest request, @RequestHeader("Idempotency-Key") String key){
        BillPaymentRequest billRequest = new BillPaymentRequest(cif, request.billReference(), request.walletNumber(), request.amount());
        return new ResponseEntity<>(billClient.pay(billRequest,key),HttpStatus.OK);
    }
}
