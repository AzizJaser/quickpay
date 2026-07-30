package com.quickpay.bill.web;

import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.dto.request.PaymentRequest;
import com.quickpay.bill.dto.response.BillResponse;
import com.quickpay.bill.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bill")
public class BillController {

    private final BillService billService;

    @PostMapping("/create")
    public ResponseEntity<BillResponse> createBill(@Valid @RequestBody PaymentRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        Bill bill = billService.createPayment(request.billReference(), request.walletNumber(), request.amount(),idempotencyKey);
        BillResponse response = new BillResponse(bill.getPaymentId(), bill.getBillReference(), bill.getAmount(),bill.getStatus());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/reserve")
    public ResponseEntity<BillResponse> payBill(@Valid @RequestBody PaymentRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey){
        Bill bill = billService.createPayment(request.billReference(), request.walletNumber(), request.amount(),idempotencyKey);
        Bill reservedBill = billService.reserveFunds(bill);
        billService.payBiller(reservedBill.getPaymentId());
        BillResponse response = new BillResponse(reservedBill.getPaymentId(), reservedBill.getBillReference(), reservedBill.getAmount(),reservedBill.getStatus());
        return new ResponseEntity<>(response,HttpStatus.CREATED);
    }
}
